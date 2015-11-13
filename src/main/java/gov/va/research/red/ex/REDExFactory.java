package gov.va.research.red.ex;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
import java.util.regex.Matcher;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.va.research.red.CVResult;
import gov.va.research.red.CVScore;
import gov.va.research.red.CVUtils;
import gov.va.research.red.LabeledSegment;
import gov.va.research.red.MatchedElement;
import gov.va.research.red.Snippet;
import gov.va.research.red.Token;
import gov.va.research.red.TokenType;
import gov.va.research.red.VTTReader;
import gov.va.research.red.ex.SnippetRegEx.TokenFreq;

public class REDExFactory {

	private static final Logger LOG = LoggerFactory
			.getLogger(REDExFactory.class);
	private static final boolean DEBUG = Boolean.valueOf(System.getProperty("debug", String.valueOf(false)));

	public REDExtractor train (
			final Collection<Snippet> snippets, final Collection<String> labels,
			final boolean allowOverMatches, final String outputTag, final boolean caseInsensitive,
			final boolean measureSensitivity,
			final List<String> holdouts,
			final boolean useTier2) throws IOException {
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
			boolean tps = checkForTruePositives(snippets, new REDExtractor(sre, caseInsensitive, holdouts), allowOverMatches);
			if (!tps) {
				throw new RuntimeException(outputTag + ": No tps for regex, should be at least one: " + sre.toString());
			}
			boolean fps = (0 == noFalsePositives.score(snippets, new REDExtractor(sre, caseInsensitive, holdouts), allowOverMatches));
			if (fps) {
				LOG.warn("Inconsistent annotataion? : fps for regex: " + sre.toString());
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
			boolean tps = checkForTruePositives(snippets, new REDExtractor(sre, caseInsensitive, holdouts), allowOverMatches);
			if (!tps) {
				throw new RuntimeException("No tps for regex, should be at least one: " + sre.toString());
			}
			boolean fps = (0 == noFalsePositives.score(snippets, new REDExtractor(sre, caseInsensitive, holdouts), allowOverMatches));
			if (fps) {
				LOG.warn("Inconsistent annotataion? : fps for regex: " + sre.toString());
			}
		}
		
		sreStacks = removeDuplicates(sreStacks);

		// perform tier 1 discovery
		String ot1 = (outputTag == null ? "t1" : outputTag + "_t1");
		List<Deque<SnippetRegEx>> tier1 = abstractIteratively (snippets, sreStacks, labels, allowOverMatches, ot1, caseInsensitive, null, noFalsePositives, holdouts, true);
		outputSnippet2Regex(snippet2regex, ot1);
		outputRegexHistory(sreStacks, ot1);
		
		// make a copy of the tier 1 results
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

		List<Collection<SnippetRegEx>> returnList = new ArrayList<>();
		returnList.add(new ArrayList<>(tier1.size()));
		for (Deque<SnippetRegEx> stack : tier1) {
			SnippetRegEx sre = stack.peek();
			boolean add = true;
			for (SnippetRegEx sreAdded : returnList.get(0)) {
				if (sreAdded.toString().equals(sre.toString())) {
					add = false;
					break;
				}
			}
			if (add) {
				returnList.get(0).add(sre);
			}
		}

		if (useTier2) {
			// perform tier 2 discovery
			TPFPDiff ptfpDiff = new TPFPDiff();
			String ot2 = (outputTag == null ? "t2" : outputTag + "_t2");
			List<Deque<SnippetRegEx>> tier2 = abstractIteratively (snippets, tier1Copy, labels, allowOverMatches, ot2, caseInsensitive, ptfpDiff, ptfpDiff, holdouts, false);
			outputSnippet2Regex(snippet2regex, ot2);
			outputRegexHistory(sreStacks, ot2);

			returnList.add(new ArrayList<>(tier2.size()));
			
			for (Deque<SnippetRegEx> stack : tier2) {
				SnippetRegEx sre = stack.peek();
				boolean add = true;
				for (SnippetRegEx sreAdded : returnList.get(1)) {
					if (sreAdded.toString().equals(sre.toString())) {
						add = false;
						break;
					}
				}
				if (add) {
					returnList.get(1).add(sre);
				}
			}
		}

		if (measureSensitivity) {
			LOG.info(outputTag + ": measuring sensitivity ...");
			measureSensitivity(snippets, returnList, caseInsensitive);
			LOG.info(outputTag + ": ... done measuring sensitivity");
		}
		return new REDExtractor(returnList, "# snippets = " + snippets.size() + "\nlabels = " + labels + "\nallowOverMatches = " + allowOverMatches, caseInsensitive, holdouts, useTier2);
	}

	private List<Deque<SnippetRegEx>> abstractIteratively (
			final Collection<Snippet> snippets,
			final List<Deque<SnippetRegEx>> sreStacks,
			final Collection<String> labels,
			final boolean allowOverMatches,
			final String outputTag,
			final boolean caseInsensitive,
			final ScoreFunction beforeChangeScoreFunction,
			final ScoreFunction afterChangeScoreFunction,
			final List<String> holdouts,
			final boolean generalizeLS) throws IOException {
		String ot = outputTag == null ? "" : outputTag;
		LOG.info(ot + ": trimming regexes ...");
		trimRegEx(snippets, sreStacks, allowOverMatches, caseInsensitive, beforeChangeScoreFunction, afterChangeScoreFunction, holdouts);
		LOG.info(ot + ": ... done trimming regexes");
		List<Deque<SnippetRegEx>> newSreStacks = removeDuplicates(sreStacks);
			
		LOG.info(ot + ": generalizing LF to MF ...");
		newSreStacks = generalizeLFtoMF(snippets, sreStacks, allowOverMatches, caseInsensitive, beforeChangeScoreFunction, afterChangeScoreFunction, holdouts);
		newSreStacks = removeDuplicates(sreStacks);
		LOG.info(ot + ": ... done generalizing LF to MF");
			
		if (generalizeLS) {
			LOG.info(ot + ": generalizing LSs ...");
			newSreStacks = generalizeLS(snippets, sreStacks, allowOverMatches, caseInsensitive, beforeChangeScoreFunction, afterChangeScoreFunction, holdouts);
			LOG.info(ot + ": ... done generalizing LSs");
		}
		
		return newSreStacks;
	}

	/**
	 * @param snippets Snippets for testing replacements
	 * @param snippetRegExStacks Labeled segment triplets representing regexes
	 * @return
	 */
	private List<Deque<SnippetRegEx>> generalizeLFtoMF(Collection<Snippet> snippets,
			List<Deque<SnippetRegEx>> snippetRegExStacks, boolean allowOverMatches, boolean caseInsensitive,
			ScoreFunction beforeChangeScoreFunction, ScoreFunction afterChangeScoreFunction,
			List<String> holdouts) {
		// build term frequency list
		Map<Token,TokenFreq> tokenFreqs = new HashMap<>();
		for (Deque<SnippetRegEx> snippetRegExStack : snippetRegExStacks) {
			SnippetRegEx sre = snippetRegExStack.peek();
			Collection<TokenFreq> snipTokenFreqs = sre.getTokenFrequencies();
			for (TokenFreq stf : snipTokenFreqs) {
				if (TokenType.WORD.equals(stf.getToken().getType()) || TokenType.PUNCTUATION.equals(stf.getToken().getType())) {
					if (!(CVUtils.containsCI(holdouts, stf.getToken().getString()))) {
						TokenFreq tf = tokenFreqs.get(stf.getToken());
						if (tf == null) {
							tokenFreqs.put(stf.getToken(), stf);
						} else {
							tf.setFreq(Integer.valueOf(tf.getFreq().intValue() + stf.getFreq().intValue()));
						}
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
				for (Segment newUnlabeledSegment : newSre.getUnlabeledSegments()) {
					ListIterator<Token> newUlsIt = newUnlabeledSegment.getTokens().listIterator();
					while (newUlsIt.hasNext()) {
						SnippetRegEx saveSre = new SnippetRegEx(newSre);
						Token newUlsToken = newUlsIt.next();
						if (newUlsToken.equals(token)) {
							boolean changed = false;
							if (TokenType.WORD.equals(newUlsToken.getType())) {
								newUlsIt.set(new Token((caseInsensitive ? "[a-z]" : "[A-Za-z]") + "{1," + ((int)Math.ceil(newUlsToken.getString().length() * 1.2)) + "}?", TokenType.REGEX));
								changed = true;
							} else if (TokenType.PUNCTUATION.equals(newUlsToken.getType())) {
								newUlsIt.set(new Token("\\p{Punct}{1," + ((int)Math.ceil(newUlsToken.getString().length() * 1.2)) + "}?", TokenType.REGEX));
								changed = true;
							}
							if (changed) {
								int beforeScore = (beforeChangeScoreFunction == null ? 1 : beforeChangeScoreFunction.score(snippets, new REDExtractor(saveSre, caseInsensitive, holdouts), allowOverMatches));
								int afterScore = (afterChangeScoreFunction == null ? 0 : afterChangeScoreFunction.score(snippets, new REDExtractor(newSre, caseInsensitive, holdouts), allowOverMatches));
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
					if (!DEBUG) {
						sreStack.clear();
					}
					sreStack.push(newSre);
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
		try (PrintWriter pw = new PrintWriter("log/regex-history_" + outputTag + ".txt")) {
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
		new File("log").mkdir();
		try (PrintWriter pw = new PrintWriter("log/snippet-regex_" + outputTag + ".txt")) {
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
			final boolean caseInsensitive,
			ScoreFunction beforeChangeScoreFunction,
			ScoreFunction afterChangeScoreFunction,
			final List<String> holdouts) {
		Set<Segment> lsSet = new HashSet<>();
		for (Deque<SnippetRegEx> sreStack : snippetRegExStacks) {
			SnippetRegEx sre = sreStack.peek();
			lsSet.addAll(sre.getLabeledSegments());
		}
		boolean first = true;
		List<Token> genLS = new ArrayList<>();
		Token orToken = new Token("|", TokenType.REGEX);
		for (Segment ls : lsSet) {
			if (first) {
				first = false;
			} else {
				genLS.add(orToken);
			}
			genLS.addAll(ls.getTokens());
		}
		for (Deque<SnippetRegEx> ls3stack : snippetRegExStacks) {
			SnippetRegEx beforeSre = ls3stack.peek();
			SnippetRegEx sreCopy = new SnippetRegEx(beforeSre);
			sreCopy.setLabeledSegments(new Segment(new ArrayList<>(genLS), true));
			int beforeScore = (beforeChangeScoreFunction == null ? 1 : beforeChangeScoreFunction.score(snippets, new REDExtractor(beforeSre, caseInsensitive, holdouts), allowOverMatches));
			int afterScore = (afterChangeScoreFunction == null ? 0 : afterChangeScoreFunction.score(snippets, new REDExtractor(sreCopy, caseInsensitive, holdouts), allowOverMatches));
			if (beforeScore <= afterScore){
				if (!DEBUG) {
					ls3stack.clear();
				}
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

	private void measureSensitivity(Collection<Snippet> snippets, List<Collection<SnippetRegEx>> rankedRegExLists, boolean caseInsensitive) {
		for (Collection<SnippetRegEx> regexs : rankedRegExLists) {
			for (SnippetRegEx regEx : regexs) {
				int count = sensitivityCount(regEx, snippets, caseInsensitive);
				double sensitivity = ((double)count)/((double)snippets.size());
				regEx.setSensitivity(sensitivity);
			}
		}
	}
	
	private int sensitivityCount(SnippetRegEx regEx, Collection<Snippet> snippets, boolean caseInsensitive) {
		int count = 0;
		for (Snippet snippt : snippets) {
			Matcher matcher = regEx.getPattern(caseInsensitive).matcher(snippt.getText());
			while (matcher.find()) {
				count++;
			}
		}
		return count;
	}

	private List<Deque<SnippetRegEx>> replaceDigits(List<Deque<SnippetRegEx>> snippetRegExStacks) {
		snippetRegExStacks.parallelStream().forEach((sreStack) -> {
			SnippetRegEx sre = sreStack.peek();
			SnippetRegEx newSre = new SnippetRegEx(sre);
			boolean changed = newSre.replaceDigits();
			if (changed) {
				if (!DEBUG) {
					sreStack.clear();
				}
				sreStack.push(newSre);
			}
		});
		return snippetRegExStacks;
	}

	private List<Deque<SnippetRegEx>> replacePunct(List<Deque<SnippetRegEx>> snippetRegExStacks) {
		snippetRegExStacks.parallelStream().forEach((sreStack) -> {
			SnippetRegEx sre = sreStack.peek();
			SnippetRegEx newSre = new SnippetRegEx(sre);
			boolean changed = newSre.replacePunct();
			if (changed) {
				if (!DEBUG) {
					sreStack.clear();
				}
				sreStack.push(newSre);
			}
		});
		return snippetRegExStacks;
	}

	protected List<Deque<SnippetRegEx>> replaceWhiteSpace(List<Deque<SnippetRegEx>> snippetRegExStacks) {
		snippetRegExStacks.parallelStream().forEach((ls3stack) -> {
			SnippetRegEx sre = ls3stack.peek();
			SnippetRegEx newSre = new SnippetRegEx(sre);
			boolean changed = newSre.replaceWhiteSpace();
			if (changed) {
				if (!DEBUG) {
					ls3stack.clear();
				}
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
	private void trimRegEx(final Collection<Snippet> snippets, List<Deque<SnippetRegEx>> snippetRegExStacks, boolean allowOverMatches, boolean caseInsensitive,
			ScoreFunction beforeChangeScoreFunction, ScoreFunction afterChangeScoreFunction, List<String> holdouts) {
		// trim from the front and back, repeat while progress is being made
		snippetRegExStacks.parallelStream().forEach(sreStack -> {
			boolean beginningProgress = false;
			boolean endProgress = false;
			do {
				beginningProgress = false;
				endProgress = false;
				SnippetRegEx beforeSre = sreStack.peek();
				SnippetRegEx sreTrim = new SnippetRegEx(beforeSre);
				// Trim from the front or the back, whichever is longer or is not terminated by a holdout word
				Token headToken = sreTrim.getBeginningToken();
				boolean headEligible = headToken != null && (!(TokenType.WORD.equals(headToken.getType()) && CVUtils.containsCI(holdouts, headToken.getString())));
				if (headEligible && sreTrim.getFirstSegmentLength() >= sreTrim.getLastSegmentLength()) {
					Token removed = sreTrim.trimFromBeginning();
					if (removed != null) {
						int beforeScore = (beforeChangeScoreFunction == null ? 1 : beforeChangeScoreFunction.score(snippets, new REDExtractor(beforeSre, caseInsensitive, holdouts), allowOverMatches));
						int afterScore = (afterChangeScoreFunction == null ? 0 : afterChangeScoreFunction.score(snippets, new REDExtractor(sreTrim, caseInsensitive, holdouts), allowOverMatches));
						if (afterScore < beforeScore){
							sreTrim.addToBeginning(removed);
							beginningProgress = false;
						} else {
							beginningProgress = true;
						}
					}
				} else {
					Token tailToken = sreTrim.getEndToken();
					boolean tailEligible = tailToken != null && (!(TokenType.WORD.equals(tailToken.getType()) && CVUtils.containsCI(holdouts, tailToken.getString())));
					if (tailEligible && sreTrim.getFirstSegmentLength() <= sreTrim.getLastSegmentLength()) {
						Token removed = sreTrim.trimFromEnd();
						if (removed != null) {
							int beforeScore = (beforeChangeScoreFunction == null ? 1 : beforeChangeScoreFunction.score(snippets, new REDExtractor(beforeSre, caseInsensitive, holdouts), allowOverMatches));
							int afterScore = (afterChangeScoreFunction == null ? 0 : afterChangeScoreFunction.score(snippets, new REDExtractor(sreTrim, caseInsensitive, holdouts), allowOverMatches));
							if (afterScore < beforeScore){
								sreTrim.addToEnd(removed);
								endProgress = false;
							} else {
								endProgress = true;
							}
						}
					}
				}
				if (beginningProgress || endProgress) {
					if (!DEBUG) {
						sreStack.clear();
					}
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
	public CVScore test(Collection<Snippet> testing, REDExtractor ex, boolean allowOverMatches,
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
			String predicted = REDExFactory.chooseBestCandidates(candidates);
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
					for (MatchedElement me : candidates) {
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
						for (MatchedElement me : candidates) {
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
			return s;
			}, (r1, r2) -> {
				if (r1 != r2) {
					r1.add(r2);
				}
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

	private boolean checkForTruePositives(Collection<Snippet> testing, REDExtractor ex, boolean allowOverMatches) {
		return testing.parallelStream().map((snippet) -> {
			List<MatchedElement> candidates = ex.extract(snippet.getText());
			String predicted = REDExFactory.chooseBestCandidates(candidates);
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

	/**
	 * @param candidates The candidate matches choose from.
	 * @return The best match string.
	 */
	private static String chooseBestCandidates(List<MatchedElement> candidates) {
		String bestMatch = null;
		if (candidates != null && candidates.size() > 0) {
			if (candidates.size() == 1) {
				bestMatch = candidates.get(0).getMatch();
			} else {
				// Multiple candidates, sort by confidence.
				Map<String, Double> match2Confidence = new HashMap<>();
				for (MatchedElement candidate : candidates) {
					Double confidence = match2Confidence.get(candidate.getMatch());
					if (confidence == null) {
						match2Confidence.put(candidate.getMatch(), candidate.getConfidence());
					} else {
						match2Confidence.put(candidate.getMatch(), confidence + candidate.getConfidence());
					}
				}
				List<Map.Entry<String,Double>> matchConfidences = new ArrayList<>(match2Confidence.entrySet());
				// Reverse sort
				Collections.sort(matchConfidences, new Comparator<Map.Entry<String,Double>>() {
					@Override
					public int compare(Map.Entry<String,Double> o1, Map.Entry<String,Double> o2) {
						if (o1 == null) {
							if (o2 == null) {
								return 0;
							}
							return 1;
						}
						if (o2 == null) {
							return -1;
						}
						return Double.compare(o2.getValue(), o1.getValue());
					}
				});
				double maxConfidence = matchConfidences.get(0).getValue();
				List<String> bestMatches = new ArrayList<>();
				int i = 0;
				Map.Entry<String,Double> entry = matchConfidences.get(i);
				while (i < matchConfidences.size() && maxConfidence == entry.getValue()) {
					bestMatches.add(entry.getKey());
					i++;
				}

				// If there is only one best match then use it.
				if (bestMatches.size() == 1) {
					bestMatch = bestMatches.get(0);
				} else {
					// Multiple best matches.
					// Choose the longest one, and if there is a tie then choose
					// the largest one lexicographically.
					Collections.sort(bestMatches,
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
					bestMatch = bestMatches.get(0);
				}
			}
		}
		return bestMatch;
	}
	
	private interface ScoreFunction {
		int score(Collection<Snippet> testing, REDExtractor ex, boolean allowOverMatches);
	}
	
	private class NoFalsePositives implements ScoreFunction {
		@Override
		public int score(Collection<Snippet> testing, REDExtractor ex,
				boolean allowOverMatches) {
			boolean anyFalsePositives =  testing.parallelStream().map((snippet) -> {
				Collection<MatchedElement> candidates = ex.extract(snippet.getText());
				Set<String> predicted = new HashSet<>();
				if (candidates != null) {
					for (MatchedElement me : candidates) {
						predicted.add(me.getMatch().trim());
					}
				}
				
				// Score
				if (predicted != null && predicted.size() != 0) {
					List<String> actual = snippet.getLabeledStrings();
					if (actual == null || actual.size() == 0) {
						return Boolean.TRUE;
					} else {
						if (CVUtils.containsAnyCI(snippet.getLabeledStrings(), predicted, allowOverMatches)) {
							return Boolean.FALSE;
						}
						return Boolean.TRUE;
					}
				}
				return Boolean.FALSE;
			}).anyMatch((fp) -> {return fp;});
			return anyFalsePositives ? 0 : 1;
		}
	}
	
	private class TPFPDiff implements ScoreFunction {
		@Override
		public int score(Collection<Snippet> testing, REDExtractor ex,
				boolean allowOverMatches) {
			CVScore score = test(testing, ex, allowOverMatches, null);
			return score.getTp() - score.getFp();
		}
	}
	
	public REDExtractor buildModel(
			final Collection<Snippet> snippets, final Collection<String> labels,
			final boolean allowOverMatches, String outputTag, Path outputModelPath,
			boolean caseInsensitive, List<String> holdouts, boolean useTier2, final boolean debug) throws IOException {
		REDExtractor rex = train(snippets, labels, allowOverMatches, outputTag, caseInsensitive, true, holdouts, useTier2);
		REDExtractor.dump(rex, outputModelPath);
		return rex;
	}
	
	public static void main(String[] args) throws ConfigurationException, IOException, URISyntaxException {
		if (args.length != 2) {
			System.out.println("Arguments: [buildmodel|crossvalidate] <properties file>");
		} else {
			String op = args[0];
			String propFilename = args[1];
			Configuration conf = new PropertiesConfiguration(propFilename);
			List<Object> vttfileObjs = conf.getList("vtt.file");
			List<File> vttfiles = new ArrayList<>(vttfileObjs.size());
			for (Object vf : vttfileObjs) {
				File f = new File((String)vf);
				if (f.exists()) {
					vttfiles.add(new File((String)vf));
				} else {
					throw new FileNotFoundException((String)vf);
				}
			}
			List<Object> labelObjs = conf.getList("label");
			List<String> labels = new ArrayList<>(labelObjs.size());
			for (Object label : labelObjs) {
				labels.add((String)label);
			}
			int folds = conf.getInt("folds");
			Boolean allowOvermatches = conf.getBoolean("allow.overmatches", Boolean.TRUE);
			Boolean caseInsensitive = conf.getBoolean("case.insensitive", Boolean.TRUE);
			Boolean stopAfterFirstFold = conf.getBoolean("stop.after.first.fold", Boolean.FALSE);
			Boolean shuffle = conf.getBoolean("shuffle", Boolean.TRUE);
			int limit = conf.getInt("snippet.limit", -1);
			String modelOutputFile = conf.getString("model.output.file");
			List<Object> holdoutObjs = conf.getList("holdout");
			List<String> holdouts = null;
			if (holdoutObjs == null) {
				holdouts = new ArrayList<>(0);
			} else {
				holdouts = new ArrayList<>(holdoutObjs.size());
				for (Object hoo : holdoutObjs) {
					holdouts.add(hoo.toString());
				}
			}
			Boolean useTier2 = conf.getBoolean("use.tier2", Boolean.TRUE);

			if ("crossvalidate".equalsIgnoreCase(op)) {
				REDExCrossValidator rexcv = new REDExCrossValidator();
				List<CVResult> results = rexcv.crossValidate(vttfiles, labels, folds, allowOvermatches, caseInsensitive, holdouts, useTier2, stopAfterFirstFold, shuffle, limit);
	
				// Display results
				int i = 0;
				for (CVResult s : results) {
					if (s != null) {
						LOG.info("\n--- Run " + (i++) + " ---\n" + s.getScore().getEvaluation());
					}
				}
				CVResult aggregate = CVResult.aggregate(results);
				LOG.info("\n--- Aggregate ---\n" + aggregate.getScore().getEvaluation());
				LOG.info("# Regexes Discovered: " + aggregate.getRegExes().size());
				String regexOutputFile = conf.getString("regex.output.file");
				if (regexOutputFile != null) {
					try (FileWriter fw = new FileWriter(regexOutputFile)) {
						try (PrintWriter pw = new PrintWriter(fw)) {
							for (String regex : aggregate.getRegExes()) {
								pw.println(regex);
							}
						}
					}
				}
			} else if ("buildmodel".equalsIgnoreCase(op)) {
				VTTReader vttr = new VTTReader();
				// get snippets
				List<Snippet> snippets = new ArrayList<>();
				for (File vttFile : vttfiles) {
					Collection<Snippet> fileSnippets = vttr.findSnippets(vttFile, labels, caseInsensitive);
					snippets.addAll(fileSnippets);
				}
				LOG.info("Building model using " + snippets.size() + " snippets from " + vttfiles +  " files.\n"
						+ "\nallow.overmatches: " + allowOvermatches
						+ "\ncase.insensitive: " + caseInsensitive
						+ "\nshuffle: " + shuffle
						+ "\nsnippet.limit: " + limit
						+ "\nmodel.output.file: " + modelOutputFile);
				
				// randomize the order of the snippets
				if (shuffle) {
					Collections.shuffle(snippets);
				}
				
				// limit the number of snippets
				if (limit > 0 && limit < snippets.size()) {
					List<Snippet> limited = new ArrayList<>(limit);
					for (int i = 0; i < limit; i++) {
						limited.add(snippets.get(i));
					}
					snippets = limited;
				}

				LOG.info("training ...");
				REDExtractor rex = new REDExFactory().train(snippets, labels, allowOvermatches, "m", caseInsensitive, true, holdouts, useTier2);
				LOG.info("... done training.");
				LOG.info("Writing model file ...");
				Path modelFilePath = FileSystems.getDefault().getPath("", modelOutputFile);
				if (Files.exists(modelFilePath)) {
					throw new IOException("Output model file already exists: " + modelOutputFile);
				}
				REDExtractor.dump(rex, modelFilePath);
				LOG.info("... wrote model file to " + modelOutputFile);
			} else {
				System.out.println("Operation " + op + " not recognized. Must be buildmodel or crossvalidate.");
			}
		}
	}
}
