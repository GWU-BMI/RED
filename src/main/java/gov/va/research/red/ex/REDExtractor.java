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
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class REDExtractor {

	private static final Logger LOG = LoggerFactory
			.getLogger(REDExtractor.class);
	private static final Pattern PUNCT_PATTERN = Pattern.compile("\\p{Punct}");
	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
	private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");
	private Map<String, Pattern> patternCache = new HashMap<String, Pattern>();

	public List<SnippetRegEx> discoverRegexes(List<File> vttFiles, String label,
			boolean allowOverMatches, String outputFileName) throws IOException {
		// get snippets
		VTTReader vttr = new VTTReader();
		List<Snippet> snippets = new ArrayList<>();
		for (File vttFile : vttFiles) {
			snippets.addAll(vttr.extractSnippets(vttFile, label, true));
		}
		return discoverRegularExpressions(snippets, label, allowOverMatches, outputFileName);
	}

	public List<SnippetRegEx> discoverRegularExpressions(
			final Collection<Snippet> snippets, final String label, final boolean allowOverMatches,
			final String outputFileName) throws IOException {
		Collection<String> labels = new ArrayList<>(1);
		labels.add(label);
		return discoverRegularExpressions(snippets, labels, allowOverMatches, outputFileName);
	}

	public List<SnippetRegEx> discoverRegularExpressions(
			final Collection<Snippet> snippets, final Collection<String> labels,
			final boolean allowOverMatches, final String outputFileName) throws IOException {
		List<Deque<SnippetRegEx>> sreStacks = new ArrayList<>(snippets.size());
		for (Snippet snippet : snippets) {
			if (snippet.getLabeledSegments() != null) {
				for (LabeledSegment ls : snippet.getLabeledSegments()) {
					if (CVUtils.containsCI(labels, ls.getLabel())) {
						Deque<SnippetRegEx> snipStack = new ArrayDeque<>();
						snipStack.push(new SnippetRegEx(snippet));
						sreStacks.add(snipStack);
					}
				}
			}
		}
		if (sreStacks != null && !sreStacks.isEmpty()) {
			
			// Check for false positives. Each ls3 should have at least one true positive, matching the snippet it originated from.
			for (Deque<SnippetRegEx> sreStack : sreStacks) {
				SnippetRegEx sre = sreStack.peek();
				boolean tps = checkForTruePositives(snippets, new LSExtractor(sre), allowOverMatches);
				if (!tps) {
					throw new RuntimeException("No tps for regex, should be at least one: " + sre.toString());
				}
				boolean fps = checkForFalsePositives(snippets, new LSExtractor(sre), allowOverMatches);
				if (fps) {
					throw new RuntimeException("fps for regex: " + sre.toString());
				}
			}
			
			// replace all the digits with their regular expressions.
			replaceDigits(sreStacks);
			// replace the white space with regular expressions.
			replaceWhiteSpace(sreStacks);

			// Check for false positives. Each ls3 should have at least one true positive, matching the snippet it originated from.
			for (Deque<SnippetRegEx> sreStack : sreStacks) {
				SnippetRegEx sre = sreStack.peek();
				boolean tps = checkForTruePositives(snippets, new LSExtractor(sre), allowOverMatches);
				if (!tps) {
					throw new RuntimeException("No tps for regex, should be at least one: " + sre.toString());
				}
				boolean fps = checkForFalsePositives(snippets, new LSExtractor(sre), allowOverMatches);
				if (fps) {
					throw new RuntimeException("fps for regex: " + sre.toString());
				}
			}
			
			// Check for false positives
			Map<SnippetRegEx, TripletMatches> tripsWithFP = findTripletsWithFalsePositives(
					sreStacks, snippets, labels, allowOverMatches);
			if (tripsWithFP != null && tripsWithFP.size() > 0) {
				LOG.warn("False positive regexes found before trimming");
				for (Map.Entry<SnippetRegEx, TripletMatches> twfp : tripsWithFP
						.entrySet()) {
					LOG.warn("RegEx: " + twfp.getKey().toString());
					LOG.warn("Correct matches:");
					for (Snippet correct : twfp.getValue().getCorrect()) {
						LOG.warn("<correct value='"
								+ correct.getLabeledStrings() + "'>\n"
								+ correct.getText() + "\n</correct>");
					}
					LOG.warn("False positive matches:");
					for (Snippet fp : twfp.getValue().getFalsePositive()) {
						Pattern p = Pattern.compile(twfp.getKey()
								.toString(), Pattern.CASE_INSENSITIVE);
						Matcher m = p.matcher(fp.getText());
						m.find();
						LOG.warn("<fp actual='" + fp.getLabeledStrings()
								+ "' predicted='" + m.group(1) + "'>\n"
								+ fp.getText() + "\n</fp>");
					}
				}
			}
			
			sreStacks = removeDuplicates(sreStacks);

			LOG.info("trimming regexes ...");
			trimRegEx(snippets, sreStacks, allowOverMatches);
			LOG.info("... done trimming regexes");
			sreStacks = removeDuplicates(sreStacks);
			
			LOG.info("generalizing LSs ...");
			sreStacks = generalizeLS(snippets, sreStacks, allowOverMatches);
			LOG.info("... done generalizing LSs");
			
			sreStacks = generalizeLFtoMF(snippets, sreStacks, allowOverMatches);
			sreStacks = removeDuplicates(sreStacks);

			outputRegexHistory(sreStacks);

			List<SnippetRegEx> returnList = new ArrayList<>(sreStacks.size());
			for (Deque<SnippetRegEx> sreStack : sreStacks) {
				SnippetRegEx sre = sreStack.peek();
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

			LOG.info("measuring sensitivity ...");
			measureSensitivity(snippets, returnList);
			if (outputFileName != null && !outputFileName.equals("")) {
				File file = new File(outputFileName);
				File fileSensitvity = new File("sensitivity-"+outputFileName);
				if (!file.exists()){
					file.createNewFile();
				}
				if (!fileSensitvity.exists()) {
					fileSensitvity.createNewFile();
				}
				FileWriter fWriter = new FileWriter(file, false);
				PrintWriter pWriter = new PrintWriter(fWriter);
				FileWriter fWriterSens = new FileWriter(fileSensitvity, false);
				PrintWriter pWriterSens = new PrintWriter(fWriterSens);
				for (SnippetRegEx sre : returnList) {
					pWriter.println(sre.toString());
					pWriterSens.println(sre.getSensitivity());
				}
				pWriter.close();
				fWriter.close();
				pWriterSens.close();
				fWriterSens.close();
			}
			LOG.info("... done measuring sensitivity");
			return returnList;
		}
		return null;
	}

	/**
	 * @param snippets Snippets for testing replacements
	 * @param snippetRegExStacks Labeled segment triplets representing regexes
	 * @return
	 */
	private List<Deque<SnippetRegEx>> generalizeLFtoMF(Collection<Snippet> snippets,
			List<Deque<SnippetRegEx>> snippetRegExStacks, boolean allowOverMatches) {
		// build term frequency list
		Map<Token,TokenFreq> tokenFreqs = new HashMap<>();
		for (Deque<SnippetRegEx> snippetRegExStack : snippetRegExStacks) {
			SnippetRegEx sre = snippetRegExStack.peek();
			Collection<TokenFreq> snipTokenFreqs = sre.getTokenFrequencies();
			for (TokenFreq stf : snipTokenFreqs) {
				if (TokenType.WORD.equals(stf.getToken().getType()) || TokenType.PUNCTUATION.equals(stf.getToken().getType())) {
					TokenFreq tf = tokenFreqs.get(stf.getToken());
					if (tf == null) {
						tokenFreqs.put(tf.getToken(), tf);
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
								newUlsIt.set(new Token("\\S{1," + Math.round(newUlsToken.getString().length() * 1.2) + "}", TokenType.REGEX));
								changed = true;
							} else if (TokenType.PUNCTUATION.equals(newUlsToken.getType())) {
								newUlsIt.set(new Token("\\p{Punct}{1," + Math.round(newUlsToken.getString().length() * 1.2) + "}", TokenType.REGEX));
								changed = true;
							}
							if (changed) {
								boolean fp = checkForFalsePositives(snippets, new LSExtractor(newSre), allowOverMatches);
								if (fp) {
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
	private void outputRegexHistory(List<Deque<SnippetRegEx>> snippetRegExStacks) throws IOException {
		try (FileWriter fw = new FileWriter("regex-history-" + System.currentTimeMillis() + ".txt")) {
			try (PrintWriter pw = new PrintWriter(fw)) {
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
	}

	/**
	 * Generalize the LS element of each triplet to work for all LSs in the list that won't cause false positives.
	 * @param snippetRegExStacks
	 * @return A new LSTriplet list, with each LS segment replaced by a combination of all LSs in the list that won't cause false positives.
	 */
	private List<Deque<SnippetRegEx>> generalizeLS(final Collection<Snippet> snippets, final List<Deque<SnippetRegEx>> snippetRegExStacks, final boolean allowOverMatches) {
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
			SnippetRegEx sre = ls3stack.peek();
			SnippetRegEx sreCopy = new SnippetRegEx(sre);
			sreCopy.setLabeledSegments(genLS);
			if (!checkForFalsePositives(snippets, new LSExtractor(sreCopy), allowOverMatches)) {
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
	private void trimRegEx(final Collection<Snippet> snippets, List<Deque<SnippetRegEx>> snippetRegExStacks, boolean allowOverMatches) {
		// trim from the front and back, repeat while progress is being made
		snippetRegExStacks.parallelStream().forEach(sreStack -> {
			boolean beginningProgress = false;
			boolean endProgress = false;
			do {
				beginningProgress = false;
				endProgress = false;
				SnippetRegEx sre = sreStack.peek();
				SnippetRegEx sreTrim = new SnippetRegEx(sre);
				if (sreTrim.getFirstSegmentLength() >= sreTrim.getLastSegmentLength()) {
					Token removed = sreTrim.trimFromBeginning();
					if (removed != null) {
						if (checkForFalsePositives(snippets, new LSExtractor(sreTrim), allowOverMatches)) {
							sreTrim.addToBeginning(removed);
							beginningProgress = false;
						} else {
							beginningProgress = true;
						}
					}
				} else if (sreTrim.getFirstSegmentLength() <= sreTrim.getLastSegmentLength()) {
					Token removed = sreTrim.trimFromEnd();
					if (removed != null) {
						if (checkForFalsePositives(snippets, new LSExtractor(sreTrim), allowOverMatches)) {
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

	/**
	 * @param testing
	 *            A list of snippets to use for testing.
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
	public CVScore testExtractor(List<Snippet> testing, LSExtractor ex, boolean allowOverMatches,
			PrintWriter pw) {
		PrintWriter localPW = null;
		StringWriter sw = null;
		if (pw != null) {
			sw = new StringWriter();
			localPW = new PrintWriter(sw);
		}
		CVScore score = new CVScore();
		for (Snippet snippet : testing) {
			List<MatchedElement> candidates = ex.extract(snippet.getText());
			String predicted = REDExtractor.chooseBestCandidate(candidates);
			List<String> actual = snippet.getLabeledStrings();
			// Score
			if (predicted == null) {
				if (actual == null || actual.size() == 0) {
					score.setTn(score.getTn() + 1);
				} else {
					score.setFn(score.getFn() + 1);
					localPW.println("##### FALSE NEGATIVE #####");
					localPW.println("--- Test Snippet:");
					localPW.println(snippet.getText());
					localPW.println(">>> Predicted: " + predicted + ", Actual: " + actual);
				}
			} else if (actual == null || actual.size() == 0) {
				score.setFp(score.getFp() + 1);
				localPW.println("##### FALSE POSITIVE #####");
				localPW.println("--- Test Snippet:");
				localPW.println(snippet.getText());
				localPW.println(">>> Predicted: " + predicted + ", Actual: " + actual);
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
					score.setTp(score.getTp() + 1);
				} else {
					score.setFp(score.getFp() + 1);
					localPW.println("##### FALSE POSITIVE #####");
					localPW.println("--- Test Snippet:");
					localPW.println(snippet.getText());
					localPW.println(">>> Predicted: " + predicted + ", Actual: " + actual);
				}
			}
		}
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

	public boolean checkForFalsePositives(Collection<Snippet> testing, LSExtractor ex, boolean allowOverMatches) {
		return testing.parallelStream().map((snippet) -> {
			List<MatchedElement> candidates = ex.extract(snippet.getText());
			String predicted = chooseBestCandidate(candidates);
			
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
	}

	public boolean checkForTruePositives(Collection<Snippet> testing, LSExtractor ex, boolean allowOverMatches) {
		return testing.parallelStream().map((snippet) -> {
			List<MatchedElement> candidates = ex.extract(snippet.getText());
			String predicted = REDExtractor.chooseBestCandidate(candidates);
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
	 * @return The best candidate.
	 */
	public static String chooseBestCandidate(List<MatchedElement> candidates) {
		String category = null;
		if (candidates != null && candidates.size() > 0) {
			if (candidates.size() == 1) {
				category = candidates.get(0).getMatch();
			} else {
				// Multiple candidates, count their frequencies.
				Map<String, Integer> candidate2freq = new HashMap<String, Integer>();
				for (MatchedElement c : candidates) {
					Integer freq = candidate2freq.get(c.getMatch());
					if (freq == null) {
						freq = Integer.valueOf(1);
					} else {
						freq = Integer.valueOf(freq.intValue() + 1);
					}
					candidate2freq.put(c.getMatch(), freq);
				}
				// Sort by frequency
				TreeMap<Integer, List<String>> freq2candidates = new TreeMap<>();
				for (Map.Entry<String, Integer> c2f : candidate2freq.entrySet()) {
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
				if (mostFrequentCandidates.size() == 1) {
					category = mostFrequentCandidates.get(0);
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
				}
			}
		}
		return category;
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
	
	class PotentialMatch {
		List<String> terms;
		int count;
		List<LSTriplet> matches;

		public PotentialMatch(List<String> terms, int count, List<LSTriplet> matches) {
			this.terms = terms;
			this.count = count;
			this.matches = matches;
		}

		public String getTermsRegex() {
			if (terms == null || terms.isEmpty()) {
				return " The match is empty";
			}
			StringBuilder temp = new StringBuilder();
			for (int i = 0; i < terms.size(); i++) {
				if (i == terms.size() - 1)
					temp.append(terms.get(i));
				else
					temp.append(terms.get(i) + "\\s{1,50}");
			}
			return temp.toString();
		}
	}
}


