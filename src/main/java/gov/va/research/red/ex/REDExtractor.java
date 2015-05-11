package gov.va.research.red.ex;

import gov.va.research.red.CVScore;
import gov.va.research.red.CVUtils;
import gov.va.research.red.LSTriplet;
import gov.va.research.red.LabeledSegment;
import gov.va.research.red.MatchedElement;
import gov.va.research.red.Snippet;
import gov.va.research.red.Token;
import gov.va.research.red.TokenType;
import gov.va.research.red.VTTReader;
import gov.va.research.red.ex.SnippetRegEx.TokenFreq;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class REDExtractor {

	private static final Logger LOG = LoggerFactory
			.getLogger(REDExtractor.class);

	public List<SnippetRegEx> train (
			final Collection<Snippet> snippets, final Collection<String> labels,
			final boolean allowOverMatches, String outputTag) throws IOException {
		// Set up snippet-to-regex map and regex history stacks
		Map<Snippet, Deque<SnippetRegEx>> snippet2regex = new HashMap<>(snippets.size());
		List<Deque<SnippetRegEx>> sreStacks = new ArrayList<>(snippets.size());
		for (Snippet snippet : snippets) {
			if (snippet.getLabeledSegments() == null) {
				snippet2regex.put(snippet, null);
			} else {
				boolean matchingLS = false;
				for (LabeledSegment ls : snippet.getLabeledSegments()) {
					if (CVUtils.containsCI(labels, ls.getLabel())) {
						Deque<SnippetRegEx> snipStack = new ArrayDeque<>();
						snipStack.push(new SnippetRegEx(snippet));
						sreStacks.add(snipStack);
						snippet2regex.put(snippet, snipStack);
						matchingLS = true;
						break;
					}
				}
				if (!matchingLS) {
					snippet2regex.put(snippet, null);
				}
			}
		}
		if (sreStacks == null || sreStacks.isEmpty()) {
			return null;
		}
		
		NoFalsePositives noFalsePositives = new NoFalsePositives();

		// Check for false positives. Each ls3 should have at least one true positive, matching the snippet it originated from.
		for (Deque<SnippetRegEx> sreStack : sreStacks) {
			SnippetRegEx sre = sreStack.peek();
			boolean tps = checkForTruePositives(snippets, new LSExtractor(sre), allowOverMatches);
			if (!tps) {
				throw new RuntimeException(outputTag + ": No tps for regex, should be at least one: " + sre.toString());
			}
			boolean fps = (0 == noFalsePositives.score(snippets, new LSExtractor(sre), allowOverMatches));
			if (fps) {
				throw new RuntimeException("fps for regex: " + sre.toString());
			}
		}
		
		// replace all the digits with their regular expressions.
		replaceDigits(sreStacks);
		// replace the white space with regular expressions.
		replaceWhiteSpace(sreStacks);
		// replace puncuation
		replacePunct(sreStacks);

		// Check for false positives. Each ls3 should have at least one true positive, matching the snippet it originated from.
		for (Deque<SnippetRegEx> sreStack : sreStacks) {
			SnippetRegEx sre = sreStack.peek();
			boolean tps = checkForTruePositives(snippets, new LSExtractor(sre), allowOverMatches);
			if (!tps) {
				throw new RuntimeException("No tps for regex, should be at least one: " + sre.toString());
			}
			boolean fps = (0 == noFalsePositives.score(snippets, new LSExtractor(sre), allowOverMatches));
			if (fps) {
				throw new RuntimeException("fps for regex: " + sre.toString());
			}
		}
		
		sreStacks = removeDuplicates(sreStacks);

		// perform tier 1 discovery
		String ot1 = (outputTag == null ? "t1" : outputTag + "_t1");
		List<Deque<SnippetRegEx>> tier1 = abstractIteratively (snippets, sreStacks, labels, allowOverMatches, ot1, null, noFalsePositives);
		outputSnippet2Regex(snippet2regex, ot1);
		outputRegexHistory(sreStacks, ot1);
		
		List<Deque<SnippetRegEx>> tier1Copy = new ArrayList<>(tier1.size());
		for (Deque<SnippetRegEx> t1stack : tier1) {
			Deque<SnippetRegEx> t1stackCopy = new ArrayDeque<>(t1stack.size());
			tier1Copy.add(t1stackCopy);
			Iterator<SnippetRegEx> t2sIt = t1stack.descendingIterator();
			while (t2sIt.hasNext()) {
				SnippetRegEx sre = t2sIt.next();
				SnippetRegEx sreCopy = new SnippetRegEx(sre);
				t1stackCopy.push(sreCopy);
			}
		}
		
		// perform tier 2 discovery
		TPFPDiff ptfpDiff = new TPFPDiff();
		String ot2 = (outputTag == null ? "t2" : outputTag + "_t2");
		List<Deque<SnippetRegEx>> tier2 = abstractIteratively (snippets, tier1Copy, labels, allowOverMatches, ot2, ptfpDiff, ptfpDiff);
		outputSnippet2Regex(snippet2regex, ot2);
		outputRegexHistory(sreStacks, ot2);

		
		List<SnippetRegEx> returnList = new ArrayList<>(sreStacks.size());
		
		for (Deque<SnippetRegEx> stack : tier1) {
			SnippetRegEx sre = stack.peek();
			boolean add = true;
			for (SnippetRegEx sreAdded : returnList) {
				if (sreAdded.toString().equals(sre.toString())) {
					add = false;
					break;
				}
			}
			if (add) {
				returnList.add(sre);
			}
		}
		for (Deque<SnippetRegEx> stack : tier2) {
			SnippetRegEx sre = stack.peek();
			boolean add = true;
			for (SnippetRegEx sreAdded : returnList) {
				if (sreAdded.toString().equals(sre.toString())) {
					add = false;
					break;
				}
			}
			if (add) {
				returnList.add(sre);
			}
		}

		LOG.info(outputTag + ": measuring sensitivity ...");
		measureSensitivity(snippets, returnList);
		LOG.info(outputTag + ": ... done measuring sensitivity");
		return returnList;
	}

	private List<Deque<SnippetRegEx>> abstractIteratively (
			final Collection<Snippet> snippets,
			final List<Deque<SnippetRegEx>> sreStacks,
			final Collection<String> labels,
			final boolean allowOverMatches,
			final String outputTag,
			final ScoreFunction beforeChangeScoreFunction,
			final ScoreFunction afterChangeScoreFunction) throws IOException {
		String ot = outputTag == null ? "" : outputTag;
		LOG.info(ot + ": trimming regexes ...");
		trimRegEx(snippets, sreStacks, allowOverMatches, beforeChangeScoreFunction, afterChangeScoreFunction);
		LOG.info(ot + ": ... done trimming regexes");
		List<Deque<SnippetRegEx>> newSreStacks = removeDuplicates(sreStacks);
			
//		LOG.info(ot + ": generalizing LSs ...");
//		newSreStacks = generalizeLS(snippets, sreStacks, allowOverMatches, beforeChangeScoreFunction, afterChangeScoreFunction);
//		LOG.info(ot + ": ... done generalizing LSs");
		
		LOG.info(ot + ": generalizing LF to MF ...");
		newSreStacks = generalizeLFtoMF(snippets, sreStacks, allowOverMatches, beforeChangeScoreFunction, afterChangeScoreFunction);
		newSreStacks = removeDuplicates(sreStacks);
		LOG.info(ot + ": ... done generalizing LF to MF");
			
		return newSreStacks;
	}

	/**
	 * @param snippets Snippets for testing replacements
	 * @param snippetRegExStacks Labeled segment triplets representing regexes
	 * @return
	 */
	private List<Deque<SnippetRegEx>> generalizeLFtoMF(Collection<Snippet> snippets,
			List<Deque<SnippetRegEx>> snippetRegExStacks, boolean allowOverMatches,
			ScoreFunction beforeChangeScoreFunction, ScoreFunction afterChangeScoreFunction) {
		// build term frequency list
		Map<Token,TokenFreq> tokenFreqs = new HashMap<>();
		for (Deque<SnippetRegEx> snippetRegExStack : snippetRegExStacks) {
			SnippetRegEx sre = snippetRegExStack.peek();
			Collection<TokenFreq> snipTokenFreqs = sre.getTokenFrequencies();
			for (TokenFreq stf : snipTokenFreqs) {
				if (TokenType.WORD.equals(stf.getToken().getType()) || TokenType.PUNCTUATION.equals(stf.getToken().getType())) {
					TokenFreq tf = tokenFreqs.get(stf.getToken());
					if (tf == null) {
						tokenFreqs.put(stf.getToken(), stf);
					} else {
						tf.setFreq(Integer.valueOf(tf.getFreq().intValue() + stf.getFreq().intValue()));
					}
				}
			}
		}
		List<TokenFreq> tokenFreqList = new ArrayList<>(tokenFreqs.values());
		Collections.sort(tokenFreqList);
		// Attempt to generalize each term, starting with the least frequent
		for (TokenFreq tf : tokenFreqList) {
			Token token = tf.getToken();
			snippetRegExStacks.parallelStream().forEach((sreStack) -> {
				boolean replaced = false;
				SnippetRegEx newSre = new SnippetRegEx(sreStack.peek());
				for (List<Token> newUnlabeledSegment : newSre.getUnlabeledSegments()) {
					ListIterator<Token> newUlsIt = newUnlabeledSegment.listIterator();
					while (newUlsIt.hasNext()) {
						SnippetRegEx saveSre = new SnippetRegEx(newSre);
						Token newUlsToken = newUlsIt.next();
						if (newUlsToken.equals(token)) {
							boolean changed = false;
							if (TokenType.WORD.equals(newUlsToken.getType())) {
								newUlsIt.set(new Token("[A-Za-z]{1," + ((int)Math.ceil(newUlsToken.getString().length() * 1.2)) + "}?", TokenType.REGEX));
								changed = true;
							} else if (TokenType.PUNCTUATION.equals(newUlsToken.getType())) {
								newUlsIt.set(new Token("\\p{Punct}{1," + ((int)Math.ceil(newUlsToken.getString().length() * 1.2)) + "}?", TokenType.REGEX));
								changed = true;
							}
							if (changed) {
								int beforeScore = (beforeChangeScoreFunction == null ? 1 : beforeChangeScoreFunction.score(snippets, new LSExtractor(saveSre), allowOverMatches));
								int afterScore = (afterChangeScoreFunction == null ? 0 : afterChangeScoreFunction.score(snippets, new LSExtractor(newSre), allowOverMatches));
								if (afterScore < beforeScore) {
									// revert
									newSre = saveSre;
								} else {
									replaced = true;
								}
							}
						}
					}
				}
				if (replaced) {
					sreStack.add(newSre);
				}
			});
		}
		return snippetRegExStacks;
	}

	/**
	 * @param snippetRegExStacks
	 * @throws IOException 
	 */
	private void outputRegexHistory(List<Deque<SnippetRegEx>> snippetRegExStacks, String outputTag) throws IOException {
		try (PrintWriter pw = new PrintWriter("regex-history_" + outputTag + ".txt")) {
			pw.println();
			for (Deque<SnippetRegEx> snippetRegExStack : snippetRegExStacks) {
				pw.println("---------- GS ----------");
				for (SnippetRegEx snippetRegEx : snippetRegExStack) {
					pw.println("----- RS -----");
					pw.println(snippetRegEx.toString());
				}
			}
		}
	}
	
	/**
	 * @param snippetRegExStacks
	 * @throws IOException 
	 */
	private void outputSnippet2Regex(Map<Snippet, Deque<SnippetRegEx>> snippet2regex, String outputTag) throws IOException {
		try (PrintWriter pw = new PrintWriter("snippet-regex_" + outputTag + ".txt")) {
			boolean first = true;
			for (Map.Entry<Snippet, Deque<SnippetRegEx>> snip2re : snippet2regex.entrySet()) {
				if (first) {
					first = false;
				} else {
					pw.println();
				}
				pw.println("Snippet: " + snip2re.getKey().getText());
				String valStr = null;
				Deque<SnippetRegEx> stack = snip2re.getValue();
				if (stack != null) {
					SnippetRegEx val = stack.peek();
					if (val != null) {
						valStr = val.toString();
					}
				}
				pw.println("Regex  : " + valStr);
			}
		}
	}

	/**
	 * Generalize the LS element of each triplet to work for all LSs in the list that won't cause false positives.
	 * @param snippetRegExStacks
	 * @return A new LSTriplet list, with each LS segment replaced by a combination of all LSs in the list that won't cause false positives.
	 */
	private List<Deque<SnippetRegEx>> generalizeLS(
			final Collection<Snippet> snippets,
			final List<Deque<SnippetRegEx>> snippetRegExStacks,
			final boolean allowOverMatches,
			ScoreFunction beforeChangeScoreFunction,
			ScoreFunction afterChangeScoreFunction) {
		Set<List<Token>> lsSet = new HashSet<>();
		for (Deque<SnippetRegEx> sreStack : snippetRegExStacks) {
			SnippetRegEx sre = sreStack.peek();
			lsSet.addAll(sre.getLabeledSegments());
		}
		boolean first = true;
		List<Token> genLS = new ArrayList<>();
		Token orToken = new Token("|", TokenType.REGEX);
		for (List<Token> tokenList : lsSet) {
			if (first) {
				first = false;
			} else {
				genLS.add(orToken);
			}
			genLS.addAll(tokenList);
		}
		for (Deque<SnippetRegEx> ls3stack : snippetRegExStacks) {
			SnippetRegEx beforeSre = ls3stack.peek();
			SnippetRegEx sreCopy = new SnippetRegEx(beforeSre);
			sreCopy.setLabeledSegments(genLS);
			int beforeScore = (beforeChangeScoreFunction == null ? 1 : beforeChangeScoreFunction.score(snippets, new LSExtractor(beforeSre), allowOverMatches));
			int afterScore = (afterChangeScoreFunction == null ? 0 : afterChangeScoreFunction.score(snippets, new LSExtractor(sreCopy), allowOverMatches));
			if (beforeScore <= afterScore){
				ls3stack.push(sreCopy);
			}
		}
		return snippetRegExStacks;
	}

	/**
	 * @param ls3list A list of LSTriplet Deques
	 * @return A new list of LSTriplet Deques with no duplicates (ls3list is not modified).
	 */
	private List<Deque<SnippetRegEx>> removeDuplicates(final List<Deque<SnippetRegEx>> ls3list) {
		List<SnippetRegEx> headList = new ArrayList<>(ls3list.size());
		List<Deque<SnippetRegEx>> nodups = new ArrayList<>(ls3list.size());
		for (Deque<SnippetRegEx> ls3stack : ls3list) {
			SnippetRegEx head = ls3stack.peek();
			if (!headList.contains(head)) {
				headList.add(head);
				nodups.add(ls3stack);
			}
		}
		return nodups;
	}

	private void measureSensitivity(Collection<Snippet> snippets, List<SnippetRegEx> regExList) {
		for (SnippetRegEx regEx : regExList) {
			int count = sensitivityCount(regEx, snippets);
			double sensitivity = ((double)count)/((double)snippets.size());
			regEx.setSensitivity(sensitivity);
		}
	}
	
	private int sensitivityCount(SnippetRegEx regEx, Collection<Snippet> snippets) {
		int count = 0;
		for (Snippet snippt : snippets) {
			Matcher matcher = regEx.getPattern().matcher(snippt.getText());
			while (matcher.find()) {
				count++;
			}
		}
		return count;
	}

	public List<Deque<SnippetRegEx>> replaceDigits(List<Deque<SnippetRegEx>> snippetRegExStacks) {
		snippetRegExStacks.parallelStream().forEach((sreStack) -> {
			SnippetRegEx sre = sreStack.peek();
			SnippetRegEx newSre = new SnippetRegEx(sre);
			boolean changed = newSre.replaceDigits();
			if (changed) {
				sreStack.push(newSre);
			}
		});
		return snippetRegExStacks;
	}

	public List<Deque<SnippetRegEx>> replacePunct(List<Deque<SnippetRegEx>> snippetRegExStacks) {
		snippetRegExStacks.parallelStream().forEach((sreStack) -> {
			SnippetRegEx sre = sreStack.peek();
			SnippetRegEx newSre = new SnippetRegEx(sre);
			boolean changed = newSre.replacePunct();
			if (changed) {
				sreStack.push(newSre);
			}
		});
		return snippetRegExStacks;
	}

	public List<Deque<SnippetRegEx>> replaceWhiteSpace(List<Deque<SnippetRegEx>> snippetRegExStacks) {
		snippetRegExStacks.parallelStream().forEach((ls3stack) -> {
			SnippetRegEx sre = ls3stack.peek();
			SnippetRegEx newSre = new SnippetRegEx(sre);
			boolean changed = newSre.replaceWhiteSpace();
			if (changed) {
				ls3stack.push(newSre);
			}
		});
		return snippetRegExStacks;
	}

	/**
	 * check if we can remove the first regex from bls. Keep on repeating
	 * the process till we can't remove any regex's from the bls's.
	 * @param snippets
	 * @param snippetRegExStacks
	 */
	private void trimRegEx(final Collection<Snippet> snippets, List<Deque<SnippetRegEx>> snippetRegExStacks, boolean allowOverMatches,
			ScoreFunction beforeChangeScoreFunction, ScoreFunction afterChangeScoreFunction) {
		// trim from the front and back, repeat while progress is being made
		snippetRegExStacks.parallelStream().forEach(sreStack -> {
			boolean beginningProgress = false;
			boolean endProgress = false;
			do {
				beginningProgress = false;
				endProgress = false;
				SnippetRegEx beforeSre = sreStack.peek();
				SnippetRegEx sreTrim = new SnippetRegEx(beforeSre);
				if (sreTrim.getFirstSegmentLength() >= sreTrim.getLastSegmentLength()) {
					Token removed = sreTrim.trimFromBeginning();
					if (removed != null) {
						int beforeScore = (beforeChangeScoreFunction == null ? 1 : beforeChangeScoreFunction.score(snippets, new LSExtractor(beforeSre), allowOverMatches));
						int afterScore = (afterChangeScoreFunction == null ? 0 : afterChangeScoreFunction.score(snippets, new LSExtractor(sreTrim), allowOverMatches));
						if (afterScore < beforeScore){
							sreTrim.addToBeginning(removed);
							beginningProgress = false;
						} else {
							beginningProgress = true;
						}
					}
				} else if (sreTrim.getFirstSegmentLength() <= sreTrim.getLastSegmentLength()) {
					Token removed = sreTrim.trimFromEnd();
					if (removed != null) {
						int beforeScore = (beforeChangeScoreFunction == null ? 1 : beforeChangeScoreFunction.score(snippets, new LSExtractor(beforeSre), allowOverMatches));
						int afterScore = (afterChangeScoreFunction == null ? 0 : afterChangeScoreFunction.score(snippets, new LSExtractor(sreTrim), allowOverMatches));
						if (afterScore < beforeScore){
							sreTrim.addToEnd(removed);
							endProgress = false;
						} else {
							endProgress = true;
						}
					}
				}
				if (beginningProgress || endProgress) {
					sreStack.push(sreTrim);
				}
			} while (beginningProgress || endProgress);
		});
	}

	enum RESULT { TP, TN, FP, FN};

	/**
	 * @param testing
	 *            A collection of snippets to use for testing.
	 * @param ex
	 *            The extractor to be tested.
	 * @param allowOverMatches
	 *            If <code>false</code> then predicated and actual values must
	 *            match exactly to be counted as a true positive. If
	 *            <code>true</code> then if the predicted and actual values
	 *            overlap but do not match exactly, it is still counted as a
	 *            true positive.
	 * @param pw
	 *            A PrintWriter for recording output. May be <code>null</code>.
	 * @return The cross-validation score.
	 */
	public CVScore test(Collection<Snippet> testing, LSExtractor ex, boolean allowOverMatches,
			PrintWriter pw) {
		PrintWriter tempLocalPW = null;
		StringWriter sw = null;
		if (pw != null) {
			sw = new StringWriter();
			tempLocalPW = new PrintWriter(sw);
		}
		final PrintWriter localPW = tempLocalPW;
		CVScore score = testing.parallelStream().map((snippet) -> {
			List<MatchedElement> candidates = ex.extract(snippet.getText());
			List<MatchedElement> bestCandidates = REDExtractor.chooseBestCandidates(candidates);
			String predicted = (bestCandidates == null || bestCandidates.size() == 0) ? null : bestCandidates.get(0).getMatch();
			List<String> actual = snippet.getLabeledStrings();
			// Score
			if (predicted == null) {
				if (actual == null || actual.size() == 0) {
					return RESULT.TN;
				} else {
					if (localPW != null) {
						localPW.println("##### FALSE NEGATIVE #####"
							+ "\n--- Test Snippet:"
							+ "\n" + snippet.getText()
							+ "\n>>> Predicted: " + predicted + ", Actual: " + actual);
					}
					return RESULT.FN;
				}
			} else if (actual == null || actual.size() == 0) {
				if (localPW != null) {
					StringBuilder sb = new StringBuilder();
					for (MatchedElement me : bestCandidates) {
						sb.append(me.getMatchingRegex()).append("\n");
					}
					localPW.println("##### FALSE POSITIVE #####"
							+ "\n--- Test Snippet:"
							+ "\n" + snippet.getText()
							+ "\n>>> Predicted: " + predicted + ", Actual: " + actual
							+ "\nPredicting Regexes:"
							+ "\n" + sb.toString());
				}
				return RESULT.FP;
			} else {
				predicted = predicted.trim().toLowerCase();
				boolean match = false;
				if (allowOverMatches) {
					for (String ls : snippet.getLabeledStrings()) {
						ls = ls.toLowerCase();
						if (ls.contains(predicted) || predicted.contains(ls)) {
							match = true;
							break;
						}
					}
				} else {
					if (CVUtils.containsCI(snippet.getLabeledStrings(), predicted)) {
						match = true;
					}
				}
				if (match) {
					return RESULT.TP;
				} else {
					if (localPW != null) {
						StringBuilder sb = new StringBuilder();
						for (MatchedElement me : bestCandidates) {
							sb.append(me.getMatchingRegex()).append("\n");
						}
						localPW.println("##### FALSE POSITIVE #####"
								+ "\n--- Test Snippet:"
								+ "\n" + snippet.getText()
								+ "\n>>> Predicted: " + predicted + ", Actual: " + actual
								+ "\nPredicting Regexes:"
								+ "\n" + sb.toString());
					}
					return RESULT.FP;
				}
			}
		}).reduce( new CVScore(), (s, r) -> {
			switch (r) {
			case TP: s.incrementTp(); break;
			case TN: s.incrementTn(); break;
			case FP: s.incrementFp(); break;
			case FN: s.incrementFn(); break;
			default: throw new RuntimeException("Unknown RESULT: " + r);
			}
			return s;}, (r1, r2) -> {
				return r1;
				}
		);
		if (pw != null && localPW != null && sw != null) {
			localPW.close();
			pw.println();
			pw.append(sw.toString());
			try {
				sw.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return score;
	}

	public boolean checkForTruePositives(Collection<Snippet> testing, LSExtractor ex, boolean allowOverMatches) {
		return testing.parallelStream().map((snippet) -> {
			List<MatchedElement> candidates = ex.extract(snippet.getText());
			List<MatchedElement> bestCandidates = REDExtractor.chooseBestCandidates(candidates);
			String predicted = (bestCandidates == null || bestCandidates.size() == 0) ? null : bestCandidates.get(0).getMatch();
			List<String> actual = snippet.getLabeledStrings();

			if (predicted == null) {
				return Boolean.FALSE;
			} else if (actual == null || actual.size() == 0) {
				return Boolean.FALSE;
			} else {
				predicted = predicted.trim().toLowerCase();
				boolean match = false;
				if (allowOverMatches) {
					for (String ls : snippet.getLabeledStrings()) {
						ls = ls.toLowerCase();
						if (ls.contains(predicted) || predicted.contains(ls)) {
							match = true;
							break;
						}
					}
				} else {
					if (CVUtils.containsCI(snippet.getLabeledStrings(), predicted)) {
						match = true;
					}
				}
				if (match) {
					return Boolean.TRUE;
				} else {
					return Boolean.FALSE;
				}
			}
		}).anyMatch((tp) -> {return tp;});
	}

	public static Map<SnippetRegEx, TripletMatches> findTripletsWithFalsePositives(
			final List<Deque<SnippetRegEx>> snippetRegExStacks, final Collection<Snippet> snippets,
			final Collection<String> labels, final boolean allowOverMatches) {
		Map<SnippetRegEx, TripletMatches> tripsWithFP = new HashMap<>();
		for (Deque<SnippetRegEx> sreStack : snippetRegExStacks) {
			SnippetRegEx sre = sreStack.peek();
			List<Snippet> correct = new ArrayList<>();
			List<Snippet> falsePositive = new ArrayList<>();
			Pattern ls3pattern = sre.getPattern();
			for (Snippet snippet : snippets) {
				List<Snippet> lsCorrectMatch = new ArrayList<>();
				List<Snippet> lsFalseMatch = new ArrayList<>();
				for (LabeledSegment ls : snippet.getLabeledSegments()) {
					if (!allowOverMatches) {
						if (CVUtils.containsCI(labels, ls.getLabel())) {
							Matcher m = ls3pattern.matcher(snippet.getText());
							if (m.find()) {
								String actual = ls.getLabeledString();
								String predicted = m.group(1);
								if ((predicted != null && actual == null)
										|| (predicted != null && !snippet
												.getLabeledStrings().contains(
														predicted.trim()))) {
									lsFalseMatch.add(snippet);
								} else {
									// The regex matched at least one of the
									// snippets labeled segments correctly
									lsCorrectMatch.add(snippet);
									break;
								}
							}
						}
					}
				}
				if (lsCorrectMatch.size() == 0) {
					// The regex did not match any labeled segments correctly
					// count as a false positive.
					correct.addAll(lsCorrectMatch);
					falsePositive.addAll(lsFalseMatch);
				}
			}
			if (falsePositive.size() > 0) {
				tripsWithFP
						.put(sre, new TripletMatches(correct, falsePositive));
			}
		}
		return tripsWithFP;
	}

	/**
	 * @param candidates The possible candidates to choose from.
	 * @return A list of candidates that all produce the best result.
	 */
	public static List<MatchedElement> chooseBestCandidates(List<MatchedElement> candidates) {
		List<MatchedElement> catCandidates = null;
		if (candidates != null && candidates.size() > 0) {
			if (candidates.size() == 1) {
				return candidates;
			} else {
				// Multiple candidates, count their frequencies.
				Map<String, List<MatchedElement>> category2candidates = new HashMap<>();
				Map<String, Integer> category2freq = new HashMap<String, Integer>();
				for (MatchedElement c : candidates) {
					String category = c.getMatch();
					Integer freq = category2freq.get(category);
					List<MatchedElement> catCands = category2candidates.get(category);
					if (freq == null) {
						freq = Integer.valueOf(1);
						catCands = new ArrayList<>();
						category2candidates.put(category, catCands);
					} else {
						freq = Integer.valueOf(freq.intValue() + 1);
					}
					category2freq.put(c.getMatch(), freq);
					catCands.add(c);
				}
				// Sort by frequency
				TreeMap<Integer, List<String>> freq2candidates = new TreeMap<>();
				for (Map.Entry<String, Integer> c2f : category2freq.entrySet()) {
					List<String> freqCandidates = freq2candidates.get(c2f
							.getValue());
					if (freqCandidates == null) {
						freqCandidates = new ArrayList<>();
						freq2candidates.put(c2f.getValue(), freqCandidates);
					}
					freqCandidates.add(c2f.getKey());
				}
				List<String> mostFrequentCandidates = freq2candidates
						.lastEntry().getValue();
				// If there is only one candidate in the group with the highest
				// frequency then use it.
				String category = null;
				if (mostFrequentCandidates.size() == 1) {
					category = mostFrequentCandidates.get(0);
					catCandidates = category2candidates.get(category);
				} else {
					// Multiple candidates with the highest frequency.
					// Choose the longest one, and if there is a tie then choose
					// the largest one lexicographically.
					Collections.sort(mostFrequentCandidates,
							new Comparator<String>() {
								@Override
								public int compare(String o1, String o2) {
									if (o1 == o2) {
										return 0;
									}
									if (o1 == null) {
										return 1;
									}
									if (o2 == null) {
										return -1;
									}
									if (o1.length() == o2.length()) {
										return o2.compareTo(o1);
									}
									return o2.length() - o1.length();
								}
							});
					category = mostFrequentCandidates.get(0);
					catCandidates = category2candidates.get(category);
				}
			}
		}
		return catCandidates;
	}

	public static class TripletMatches {
		private List<Snippet> correct;
		private List<Snippet> falsePositive;

		public TripletMatches(final List<Snippet> correct,
				final List<Snippet> falsePositive) {
			this.correct = correct;
			this.falsePositive = falsePositive;
		}

		public List<Snippet> getCorrect() {
			return correct;
		}

		public void setCorrect(List<Snippet> correct) {
			this.correct = correct;
		}

		public List<Snippet> getFalsePositive() {
			return falsePositive;
		}

		public void setFalsePositive(List<Snippet> falsePositive) {
			this.falsePositive = falsePositive;
		}
	}
	
	interface ScoreFunction {
		int score(Collection<Snippet> testing, LSExtractor ex, boolean allowOverMatches);
	}
	
	class NoFalsePositives implements ScoreFunction {
		@Override
		public int score(Collection<Snippet> testing, LSExtractor ex,
				boolean allowOverMatches) {
			boolean anyFalsePositives =  testing.parallelStream().map((snippet) -> {
				List<MatchedElement> candidates = ex.extract(snippet.getText());
				boolean nn = false;
				if (candidates != null) {
					nn = true;
				}
				List<MatchedElement> bestCandidates = chooseBestCandidates(candidates);
				String predicted = (bestCandidates == null || bestCandidates.size() == 0) ? null : bestCandidates.get(0).getMatch();
				
				// Score
				if (predicted != null) {
					List<String> actual = snippet.getLabeledStrings();
					if (actual == null || actual.size() == 0) {
						return Boolean.TRUE;
					} else {
						predicted = predicted.trim().toLowerCase();
						boolean match = false;
						if (allowOverMatches) {
							for (String ls : snippet.getLabeledStrings()) {
								ls = ls.toLowerCase();
								if (ls.contains(predicted) || predicted.contains(ls)) {
									match = true;
									break;
								}
							}
						} else {
							match = CVUtils.containsCI(snippet.getLabeledStrings(), predicted);
						}
						if (!match) {
							return Boolean.TRUE;
						}

					}
				}
				return Boolean.FALSE;
			}).anyMatch((fp) -> {return fp;});
			return anyFalsePositives ? 0 : 1;
		}
	}
	
	class TPFPDiff implements ScoreFunction {
		@Override
		public int score(Collection<Snippet> testing, LSExtractor ex,
				boolean allowOverMatches) {
			CVScore score = test(testing, ex, allowOverMatches, null);
			return score.getTp() - score.getFp();
		}
	}
}


