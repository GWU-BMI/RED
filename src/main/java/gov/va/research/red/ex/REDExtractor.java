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

	public List<LSTriplet> discoverRegexes(List<File> vttFiles, String label,
			boolean allowOverMatches, String outputFileName) throws IOException {
		// get snippets
		VTTReader vttr = new VTTReader();
		List<Snippet> snippets = new ArrayList<>();
		for (File vttFile : vttFiles) {
			snippets.addAll(vttr.extractSnippets(vttFile, label, true));
		}
		return discoverRegularExpressions(snippets, label, allowOverMatches, outputFileName);
	}

	public List<LSTriplet> discoverRegularExpressions(
			final List<Snippet> snippets, final String label, final boolean allowOverMatches,
			final String outputFileName) throws IOException {
		Collection<String> labels = new ArrayList<>(1);
		labels.add(label);
		return discoverRegularExpressions(snippets, labels, allowOverMatches, outputFileName);
	}

	public List<LSTriplet> discoverRegularExpressions(
			final List<Snippet> snippets, final Collection<String> labels,
			final boolean allowOverMatches, final String outputFileName) throws IOException {
		List<Deque<LSTriplet>> ls3list = new ArrayList<>(snippets.size());
		for (Snippet snippet : snippets) {
			if (snippet.getLabeledSegments() != null) {
				for (LabeledSegment ls : snippet.getLabeledSegments()) {
					if (CVUtils.containsCI(labels, ls.getLabel())) {
						Deque<LSTriplet> ls3stack = new ArrayDeque<>();
						ls3stack.push(LSTriplet.valueOf(snippet.getText(), ls));
						ls3list.add(ls3stack);
					}
				}
			}
		}
		if (ls3list != null && !ls3list.isEmpty()) {
			// Check for false positives. Each ls3 should have at least one true positive, matching the snippet it originated from.
			for (Deque<LSTriplet> ls3stack : ls3list) {
				LSTriplet ls3 = ls3stack.peek();
				checkForFalsePositives(snippets, ls3, allowOverMatches);
			}
			
			// replace all the digits in the LS with their regular expressions.
			replaceDigitsLS(ls3list);
			// replace the digits in BLS and ALS with their regular expressions.
			replaceDigitsBLSALS(ls3list);
			// replace the white spaces with regular expressions.
			replaceWhiteSpaces(ls3list);

			// Check for false positives
			Map<LSTriplet, TripletMatches> tripsWithFP = findTripletsWithFalsePositives(
					ls3list, snippets, labels, allowOverMatches);
			if (tripsWithFP != null && tripsWithFP.size() > 0) {
				LOG.warn("False positive regexes found before trimming");
				for (Map.Entry<LSTriplet, TripletMatches> twfp : tripsWithFP
						.entrySet()) {
					LOG.warn("RegEx: " + twfp.getKey().toStringRegEx());
					LOG.warn("Correct matches:");
					for (Snippet correct : twfp.getValue().getCorrect()) {
						LOG.warn("<correct value='"
								+ correct.getLabeledStrings() + "'>\n"
								+ correct.getText() + "\n</correct>");
					}
					LOG.warn("False positive matches:");
					for (Snippet fp : twfp.getValue().getFalsePositive()) {
						Pattern p = Pattern.compile(twfp.getKey()
								.toStringRegEx(), Pattern.CASE_INSENSITIVE);
						Matcher m = p.matcher(fp.getText());
						m.find();
						LOG.warn("<fp actual='" + fp.getLabeledStrings()
								+ "' predicted='" + m.group(1) + "'>\n"
								+ fp.getText() + "\n</fp>");
					}
				}
			}
			
			ls3list = removeDuplicates(ls3list);

			LOG.info("trimming regexes ...");
			trimRegEx(snippets, ls3list, allowOverMatches);
			LOG.info("... done trimming regexes");
			ls3list = removeDuplicates(ls3list);
			
			LOG.info("generalizing LSs ...");
			ls3list = generalizeLS(snippets, ls3list, allowOverMatches);
			LOG.info("... done generalizing LSs");
			
			ls3list = generalizeLFtoMF(snippets, ls3list, allowOverMatches);
			ls3list = removeDuplicates(ls3list);

			outputRegexHistory(ls3list);

			List<LSTriplet> returnList = new ArrayList<LSTriplet>(ls3list.size());
			for (Deque<LSTriplet> ls3stack : ls3list) {
				LSTriplet ls3 = ls3stack.peek();
				boolean add = true;
				for (LSTriplet tripletAdded : returnList) {
					if (tripletAdded.toStringRegEx().equals(ls3.toStringRegEx())) {
						add = false;
						break;
					}
				}
				if (add) {
					returnList.add(ls3);
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
				for (LSTriplet triplet : returnList) {
					pWriter.println(triplet.toString());
					pWriterSens.println(triplet.getSensitivity());
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
	 * @param ls3list Labeled segment triplets representing regexes
	 * @return
	 */
	private List<Deque<LSTriplet>> generalizeLFtoMF(List<Snippet> snippets,
			List<Deque<LSTriplet>> ls3list, boolean allowOverMatches) {
		// build term frequency list
		Map<Token,TokenFreq> tokenFreqs = new HashMap<>();
		for (Deque<LSTriplet> ls3stack : ls3list) {
			LSTriplet ls3 = ls3stack.peek();
			List<List<Token>> tokenLists = new ArrayList<>();
			tokenLists.add(ls3.getBLS());
			tokenLists.add(ls3.getLS());
			tokenLists.add(ls3.getALS());

			for (List<Token> tokenList : tokenLists) {
				for (Token t : tokenList) {
					if (TokenType.WORD.equals(t.getType()) || TokenType.PUNCTUATION.equals(t.getType())) {
						TokenFreq tf = tokenFreqs.get(t);
						if (tf == null) {
							tf = new TokenFreq(t, Integer.valueOf(1));
							tokenFreqs.put(t, tf);
						} else {
							tf.setFreq(Integer.valueOf(tf.getFreq().intValue() + 1));
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
			ls3list.parallelStream().forEach((ls3stack) -> {
				boolean replaced = false;
				LSTriplet newLS3 = new LSTriplet(ls3stack.peek());
				ListIterator<Token> newBlsIt = newLS3.getBLS().listIterator();
				while (newBlsIt.hasNext()) {
					LSTriplet saveLS3 = new LSTriplet(newLS3);
					Token newBlsToken = newBlsIt.next();
					if (newBlsToken.equals(token)) {
						boolean changed = false;
						if (TokenType.WORD.equals(newBlsToken.getType())) {
							newBlsIt.set(new Token("\\S{1," + Math.round(newBlsToken.getString().length() * 1.2) + "}", TokenType.REGEX));
							changed = true;
						} else if (TokenType.PUNCTUATION.equals(newBlsToken.getType())) {
							newBlsIt.set(new Token("\\p{Punct}{1," + Math.round(newBlsToken.getString().length() * 1.2) + "}", TokenType.REGEX));
							changed = true;
						}
						if (changed) {
							boolean fp = checkForFalsePositives(snippets, newLS3, allowOverMatches);
							if (fp) {
								// revert
								newLS3 = saveLS3;
							} else {
								replaced = true;
							}
						}
					}
				}
				ListIterator<Token> newAlsIt = newLS3.getALS().listIterator();
				while (newAlsIt.hasNext()) {
					LSTriplet saveLS3 = new LSTriplet(newLS3);
					Token newAlsToken = newAlsIt.next();
					if (newAlsToken.equals(token)) {
						boolean changed = false;
						if (TokenType.WORD.equals(newAlsToken.getType())) {
							newAlsIt.set(new Token("\\S{1," + Math.round(newAlsToken.getString().length() * 1.2) + "}", TokenType.REGEX));
							changed = true;
						} else if (TokenType.PUNCTUATION.equals(newAlsToken.getType())) {
							newAlsIt.set(new Token("\\p{Punct}{1," + Math.round(newAlsToken.getString().length() * 1.2) + "}", TokenType.REGEX));
							changed = true;
						}
						if (changed) {
							boolean fp = checkForFalsePositives(snippets, newLS3, allowOverMatches);
							if (fp) {
								// revert
								newLS3 = saveLS3;
							} else {
								replaced = true;
							}
						}
					}
				}
				if (replaced) {
					ls3stack.add(newLS3);
				}
			});
		}
		return ls3list;
	}
	
	private class TokenFreq implements Comparable<TokenFreq> {
		private Token token;
		private Integer freq;

		public TokenFreq(Token token, Integer freq) {
			this.token = token;
			this.freq = freq;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		@Override
		public int compareTo(TokenFreq o) {
			return ((freq == null ? Integer.MIN_VALUE : freq) - (o.freq == null ? Integer.MIN_VALUE : o.freq));
		}

		public Token getToken() {
			return token;
		}

		public void setToken(Token token) {
			this.token = token;
		}

		public Integer getFreq() {
			return freq;
		}

		public void setFreq(Integer freq) {
			this.freq = freq;
		}
	}

	/**
	 * @param ls3list
	 * @throws IOException 
	 */
	private void outputRegexHistory(List<Deque<LSTriplet>> ls3list) throws IOException {
		try (FileWriter fw = new FileWriter("regex-history-" + System.currentTimeMillis() + ".txt")) {
			try (PrintWriter pw = new PrintWriter(fw)) {
				pw.println();
				for (Deque<LSTriplet> ls3stack : ls3list) {
					pw.println("---------- GS ----------");
					for (LSTriplet ls3 : ls3stack) {
						pw.println("----- RS -----");
						pw.println(ls3.toStringRegEx());
					}
				}
			}
		}
	}

	/**
	 * Generalize the LS element of each triplet to work for all LSs in the list that won't cause false positives.
	 * @param ls3list
	 * @return A new LSTriplet list, with each LS segment replaced by a combination of all LSs in the list that won't cause false positives.
	 */
	private List<Deque<LSTriplet>> generalizeLS(final List<Snippet> snippets, final List<Deque<LSTriplet>> ls3list, final boolean allowOverMatches) {
		Set<List<Token>> lsSet = new HashSet<>();
		for (Deque<LSTriplet> ls3stack : ls3list) {
			LSTriplet ls3 = ls3stack.peek();
			lsSet.add(ls3.getLS());
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
		for (Deque<LSTriplet> ls3stack : ls3list) {
			LSTriplet ls3 = ls3stack.peek();
			LSTriplet ls3copy = new LSTriplet(ls3);
			ls3copy.setLS(genLS);
			if (!checkForFalsePositives(snippets, ls3copy, allowOverMatches)) {
				ls3stack.push(ls3copy);
			}
		}
		return ls3list;
	}

	/**
	 * @param ls3list A list of LSTriplet Deques
	 * @return A new list of LSTriplet Deques with no duplicates (ls3list is not modified).
	 */
	private List<Deque<LSTriplet>> removeDuplicates(final List<Deque<LSTriplet>> ls3list) {
		List<LSTriplet> headList = new ArrayList<>(ls3list.size());
		List<Deque<LSTriplet>> nodups = new ArrayList<>(ls3list.size());
		for (Deque<LSTriplet> ls3stack : ls3list) {
			LSTriplet head = ls3stack.peek();
			if (!headList.contains(head)) {
				headList.add(head);
				nodups.add(ls3stack);
			}
		}
		return nodups;
	}

	private void measureSensitivity(List<Snippet> snippets, List<LSTriplet> regExList) {
		for (LSTriplet triplet : regExList) {
			int count = sensitivityCount(triplet, snippets);
			double sensitivity = ((double)count)/((double)snippets.size());
			triplet.setSensitivity(sensitivity);
		}
	}
	
	private int sensitivityCount(LSTriplet regEx, List<Snippet> snippets) {
		Pattern pattern = patternCache.get(regEx.toStringRegEx());
		int count = 0;
		if (pattern == null) {
			pattern = Pattern.compile(regEx.toStringRegEx(), Pattern.CASE_INSENSITIVE);
			patternCache.put(regEx.toStringRegEx(), pattern);
		}
		for (Snippet snippt : snippets) {
			Matcher matcher = pattern.matcher(snippt.getText());
			if (matcher.find()) {
				count++;
			}
		}
		return count;
	}

	public List<Deque<LSTriplet>> replaceDigitsLS(List<Deque<LSTriplet>> ls3list) {
		ls3list.parallelStream().forEach((ls3stack) -> {
			LSTriplet ls3 = ls3stack.peek();
			LSTriplet newLS3 = new LSTriplet(ls3);
			boolean changed = false;
			ListIterator<Token> lsIt = newLS3.getLS().listIterator();
			while (lsIt.hasNext()) {
				Token t = lsIt.next();
				if (TokenType.INTEGER.equals(t.getType())) {
					lsIt.set(new Token("\\d+", TokenType.REGEX));
					changed = true;
				}
			}
			if (changed) {
				ls3stack.push(newLS3);
			}
		});
		return ls3list;
	}

	public List<Deque<LSTriplet>> replaceDigitsBLSALS(List<Deque<LSTriplet>> ls3list) {
		ls3list.parallelStream().forEach((ls3stack) -> {
			LSTriplet ls3 = ls3stack.peek();
			LSTriplet newLS3 = new LSTriplet(ls3);
			boolean changed = false;
			ListIterator<Token> blsIt = newLS3.getBLS().listIterator();
			while (blsIt.hasNext()) {
				Token t = blsIt.next();
				if (TokenType.INTEGER.equals(t.getType())) {
					blsIt.set(new Token("\\d+", TokenType.REGEX));
					changed = true;
				}
			}
			ListIterator<Token> alsIt = newLS3.getALS().listIterator();
			while (alsIt.hasNext()) {
				Token t = alsIt.next();
				if (TokenType.INTEGER.equals(t.getType())) {
					alsIt.set(new Token("\\d+", TokenType.REGEX));
					changed = true;
				}
			}
			if (changed) {
				ls3stack.push(newLS3);
			}
		});
		return ls3list;
	}

	public List<Deque<LSTriplet>> replaceWhiteSpaces(List<Deque<LSTriplet>> ls3list) {
		ls3list.parallelStream().forEach((ls3stack) -> {
			LSTriplet ls3 = ls3stack.peek();
			LSTriplet newLS3 = new LSTriplet(ls3);
			boolean changed = false;
			List<List<Token>> tokenLists = new ArrayList<>(3);
			tokenLists.add(newLS3.getBLS());
			tokenLists.add(newLS3.getLS());
			tokenLists.add(newLS3.getALS());
			for (List<Token> tokenList : tokenLists) {
				ListIterator<Token> tIt = tokenList.listIterator();
				while (tIt.hasNext()) {
					Token t = tIt.next();
					if (TokenType.WHITESPACE.equals(t.getType())) {
						tIt.set(new Token("\\s{1," + Math.round(t.getString().length() * 1.2) + "}", TokenType.REGEX));
						changed = true;
					}
				}
			}
			if (changed) {
				ls3stack.push(newLS3);
			}
		});
		return ls3list;
	}

	/**
	 * check if we can remove the first regex from bls. Keep on repeating
	 * the process till we can't remove any regex's from the bls's.
	 * @param snippets
	 * @param ls3list
	 */
	private void trimRegEx(final List<Snippet> snippets, List<Deque<LSTriplet>> ls3list, boolean allowOverMatches) {
		// trim from the front and back, repeat while progress is being made
		ls3list.parallelStream().forEach(ls3stack -> {
			boolean blsProgress = false;
			boolean alsProgress = false;
			do {
				blsProgress = false;
				alsProgress = false;
				LSTriplet ls3 = ls3stack.peek();
				LSTriplet ls3trim = new LSTriplet(ls3);
				if (ls3trim.getBLS().size() >= ls3trim.getALS().size()) {
					List<Token> origBls = new ArrayList<>(ls3trim.getBLS());
					if (ls3trim.getBLS() != null && !ls3trim.getBLS().isEmpty()) {
						ls3trim.getBLS().remove(0);
						if (checkForFalsePositives(snippets, ls3trim, allowOverMatches)) {
							ls3trim.setBLS(origBls);
							blsProgress = false;
						} else {
							blsProgress = true;
						}
					}
				} else if (ls3trim.getBLS().size() <= ls3trim.getALS().size()) {
					List<Token> origAls = new ArrayList<>(ls3trim.getALS());
					if (ls3trim.getALS() != null && !ls3trim.getALS().isEmpty()) {
						ls3trim.getALS().remove(ls3trim.getALS().size() - 1);
						if (checkForFalsePositives(snippets, ls3trim, allowOverMatches)) {
							ls3trim.setALS(origAls);
							alsProgress = false;
						} else {
							alsProgress = true;
						}
					}
				}
				if (blsProgress || alsProgress) {
					ls3stack.push(ls3trim);
				}
			} while (blsProgress || alsProgress);
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
//			MatchedElement me = ex.extractFirst(snippet.getText());
//			String predicted = (me == null ? null : me.getMatch());
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

	public boolean checkForFalsePositives(List<Snippet> testing, LSExtractor ex, boolean allowOverMatches) {
		return testing.parallelStream().map((snippet) -> {
			List<MatchedElement> candidates = ex.extract(snippet.getText());
			String predicted = chooseBestCandidate(candidates);
//			MatchedElement me = ex.extractFirst(snippet.getText());
//			String predicted = (me == null ? null : me.getMatch());

			// Score
			if (predicted != null) {
				List<String> actual = snippet.getLabeledStrings();
				if (actual == null || actual.size() == 0) {
					return Boolean.TRUE;
				} else {
					predicted = predicted.trim().toLowerCase();
					if (allowOverMatches) {
						for (String ls : snippet.getLabeledStrings()) {
							ls = ls.toLowerCase();
							if (ls.contains(predicted) || predicted.contains(ls)) {
								return Boolean.TRUE;
							}
						}
					} else {
						if (CVUtils.containsCI(snippet.getLabeledStrings(), predicted)) {
							return Boolean.TRUE;
						}
					}
					return Boolean.FALSE;
				}
			}
			return Boolean.FALSE;
		}).anyMatch((fp) -> {return fp;});
	}

	public boolean checkForFalsePositives(List<Snippet> testing, LSTriplet ls3, boolean allowOverMatches) {
		LSExtractor lsEx = new LSExtractor(Arrays.asList(new LSTriplet[] { ls3 }));
		return checkForFalsePositives(testing, lsEx, allowOverMatches);
	}

	public static Map<LSTriplet, TripletMatches> findTripletsWithFalsePositives(
			final List<Deque<LSTriplet>> ls3list, final List<Snippet> snippets,
			final Collection<String> labels, final boolean allowOverMatches) {
		Map<LSTriplet, TripletMatches> tripsWithFP = new HashMap<>();
		for (Deque<LSTriplet> ls3stack : ls3list) {
			LSTriplet ls3 = ls3stack.peek();
			List<Snippet> correct = new ArrayList<>();
			List<Snippet> falsePositive = new ArrayList<>();
			Pattern ls3pattern = Pattern.compile(ls3.toStringRegEx(), Pattern.CASE_INSENSITIVE);
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
						.put(ls3, new TripletMatches(correct, falsePositive));
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


