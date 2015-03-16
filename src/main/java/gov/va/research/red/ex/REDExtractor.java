package gov.va.research.red.ex;

import gov.va.research.red.CVScore;
import gov.va.research.red.CVUtils;
import gov.va.research.red.LSTriplet;
import gov.va.research.red.LabeledSegment;
import gov.va.research.red.MatchedElement;
import gov.va.research.red.Snippet;
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
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class REDExtractor {

	private static final LSTriplet LS3_DUPLICATE = new LSTriplet(null,"DUPLICATE",null);
	private static final List<LSTriplet> STACK_ENDS = Arrays.asList(new LSTriplet[] { LS3_DUPLICATE });
	private static final Logger LOG = LoggerFactory
			.getLogger(REDExtractor.class);
	private static final Pattern PUNCT_PATTERN = Pattern.compile("\\p{Punct}");
	private static final Pattern REGEX_SPECIAL_CHARACTERS_PATTERN = Pattern.compile("[\\.\\^\\$\\*\\+\\?\\(\\)\\[\\{\\\\\\|\\-\\]]");
	private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
	private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");
	private Map<String, Pattern> patternCache = new HashMap<String, Pattern>();

	public List<LSTriplet> discoverRegexes(List<File> vttFiles, String label,
			String outputFileName) throws IOException {
		// get snippets
		VTTReader vttr = new VTTReader();
		List<Snippet> snippets = new ArrayList<>();
		for (File vttFile : vttFiles) {
			snippets.addAll(vttr.extractSnippets(vttFile, label));
		}
		return discoverRegularExpressions(snippets, label, outputFileName);
	}

	public List<LSTriplet> discoverRegularExpressions(
			final List<Snippet> snippets, final String label,
			final String outputFileName) throws IOException {
		Collection<String> labels = new ArrayList<>(1);
		labels.add(label);
		return discoverRegularExpressions(snippets, labels, outputFileName);
	}

	public List<LSTriplet> discoverRegularExpressions(
			final List<Snippet> snippets, final Collection<String> labels,
			final String outputFileName) throws IOException {
		List<Deque<LSTriplet>> ls3list = new ArrayList<>(snippets.size());
		for (Snippet snippet : snippets) {
			if (snippet.getLabeledSegments() != null) {
				for (LabeledSegment ls : snippet.getLabeledSegments()) {
					if (CVUtils.containsCI(labels, ls.getLabel())) {
						Deque<LSTriplet> ls3stack = new ArrayDeque<>();
						ls3stack.push(LSTriplet.valueOf(snippet.getText(), ls));
						if (ls3stack.peek().getLS().endsWith(".")) {
							LOG.error("LS ends in period: " + ls3stack.peek().toStringRegEx());
						}
						ls3list.add(ls3stack);
					}
				}
			}
		}
		if (ls3list != null && !ls3list.isEmpty()) {
			// escape all regular expression special characters.
			escapeRegexSpecialChars(ls3list);
			// replace all the digits in the LS with their regular expressions.
			replaceDigitsLS(ls3list);
			// replace the digits in BLS and ALS with their regular expressions.
			replaceDigitsBLSALS(ls3list);
			// replace the white spaces with regular expressions.
			replaceWhiteSpaces(ls3list);

			// Check for false positives
			Map<LSTriplet, TripletMatches> tripsWithFP = findTripletsWithFalsePositives(
					ls3list, snippets, labels);
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
			trimRegEx(snippets, ls3list);
			LOG.info("... done trimming regexes");
			ls3list = removeDuplicates(ls3list);
			
			LOG.info("generalizing LSs ...");
			ls3list = generalizeLS(snippets, ls3list);
			LOG.info("... done generalizing LSs");
			
			ls3list = generalizeLFtoMF(snippets, ls3list);
			ls3list = removeDuplicates(ls3list);

			outputRegexHistory(ls3list);

			List<LSTriplet> returnList = new ArrayList<LSTriplet>(ls3list.size());
			for (Deque<LSTriplet> ls3stack : ls3list) {
				LSTriplet ls3 = ls3stack.peek();
				if (!isStackEnd(ls3)) {
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
	
	private List<Deque<LSTriplet>> escapeRegexSpecialChars(List<Deque<LSTriplet>> ls3list) {
		ls3list.parallelStream().forEach((ls3stack) -> {
			LSTriplet ls3 = ls3stack.peek();
			if (!isStackEnd(ls3)) {
				String newBLS = escapeRegexSpecialChars(ls3.getBLS());
				String newLS = escapeRegexSpecialChars(ls3.getLS());
				String newALS = escapeRegexSpecialChars(ls3.getALS());
				ls3stack.push(new LSTriplet(newBLS, newLS, newALS));
			}
		});
		return ls3list;
	}

	private String escapeRegexSpecialChars(String str) {
		Matcher blsMatcher = REGEX_SPECIAL_CHARACTERS_PATTERN.matcher(str);
		int prevEnd = 0;
		StringBuilder sb = new StringBuilder();
		while (blsMatcher.find()) {
			sb.append(str.substring(prevEnd, blsMatcher.start()));
			sb.append("\\" + blsMatcher.group());
			prevEnd = blsMatcher.end();
		}
		sb.append(str.substring(prevEnd));
		return sb.toString();
	}

	/**
	 * @param snippets Snippets for testing replacements
	 * @param ls3list Labeled segment triplets representing regexes
	 * @return
	 */
	private List<Deque<LSTriplet>> generalizeLFtoMF(List<Snippet> snippets,
			List<Deque<LSTriplet>> ls3list) {
		// build term frequency list
		Map<String,TermFreq> termFreqs = new HashMap<>();
		for (Deque<LSTriplet> ls3stack : ls3list) {
			LSTriplet ls3 = ls3stack.peek();
			if (!isStackEnd(ls3)) {
				String[] blsTerms = ls3.getBLS().split("\\\\s\\{1,50\\}|\\\\p\\{Punct\\}|\\\\d\\+|\\b");
				String[] alsTerms = ls3.getALS().split("\\\\s\\{1,50\\}|\\\\p\\{Punct\\}|\\\\d\\+|\\b");
				List<String> terms = new ArrayList<>(Arrays.asList(blsTerms));
				terms.addAll(Arrays.asList(alsTerms));
				for (String term : terms) {
					if (term == null
							|| term.length() == 0
							|| (term.length() == 1 && Character.isDigit(term.charAt(0)))
							|| term.equals(",")
							|| term.equals("{")
							|| term.equals("}")) {
						continue;
					}
					TermFreq tf = termFreqs.get(term);
					if (tf == null) {
						tf = new TermFreq(term, Integer.valueOf(1));
						termFreqs.put(term, tf);
					} else {
						tf.setFreq(Integer.valueOf(tf.getFreq().intValue() + 1));
					}
				}
			}
		}
		List<TermFreq> termFreqList = new ArrayList<>(termFreqs.values());
		Collections.sort(termFreqList);
		// Attempt to generalize each term, starting with the least frequent
		Pattern escPunctPattern = Pattern.compile("\\\\\\p{Punct}");
		for (TermFreq tf : termFreqList) {
			String term = tf.getTerm();
			ls3list.parallelStream().filter((ls3stack) -> !isStackEnd(ls3stack.peek())).forEach((ls3stack) -> {
				boolean replaced = false;
				LSTriplet newLS3 = new LSTriplet(ls3stack.peek());
				Pattern termPattern = Pattern.compile("\\b" + term + "\\b");
				Matcher blsMatcher = termPattern.matcher(newLS3.getBLS());
				if (blsMatcher.find()) {
					if (escPunctPattern.matcher(term).matches()) {
						newLS3.setBLS(blsMatcher.replaceAll("\\p{Punct}"));
					} else {
						newLS3.setBLS(blsMatcher.replaceAll("\\\\S{1," + Math.round(term.length() * 1.2) + "}"));
					}
					List<LSTriplet> singleLS3 = new ArrayList<>(1);
					singleLS3.add(newLS3);
					LSExtractor lsEx = new LSExtractor(singleLS3);
					boolean fps = checkForFalsePositives(snippets, lsEx);
					if (!fps) {
						replaced = true;
					}
				}
				Matcher alsMatcher = termPattern.matcher(newLS3.getALS());
				if (alsMatcher.find()) {
					if (escPunctPattern.matcher(term).matches()) {
						newLS3.setALS(alsMatcher.replaceAll("\\\\p{Punct}"));
					} else {
						newLS3.setALS(alsMatcher.replaceAll("\\\\S{1," + Math.round(term.length() * 1.2) + "}"));
					}
					List<LSTriplet> singleLS3 = new ArrayList<>(1);
					singleLS3.add(newLS3);
					LSExtractor lsEx = new LSExtractor(singleLS3);
					boolean fps = checkForFalsePositives(snippets, lsEx);
					if (!fps) {
						replaced = true;
					}
				}
				if (replaced) {
					ls3stack.add(newLS3);
				}
			});
		}
		return ls3list;
	}
	
	private class TermFreq implements Comparable<TermFreq> {
		private String term;
		private Integer freq;

		public TermFreq(String term, Integer freq) {
			this.term = term;
			this.freq = freq;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		@Override
		public int compareTo(TermFreq o) {
			return ((freq == null ? Integer.MIN_VALUE : freq) - (o.freq == null ? Integer.MIN_VALUE : o.freq));
		}

		public String getTerm() {
			return term;
		}

		public void setTerm(String term) {
			this.term = term;
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

	private class PotentialMatches {
		public List<PotentialMatch> potentialBLSMatches;
		public List<PotentialMatch> potentialALSMatches;
	}
	
	/**
	 * Generalize the LS element of each triplet to work for all LSs in the list that won't cause false positives.
	 * @param ls3list
	 * @return A new LSTriplet list, with each LS segment replaced by a combination of all LSs in the list that won't cause false positives.
	 */
	private List<Deque<LSTriplet>> generalizeLS(final List<Snippet> snippets, final List<Deque<LSTriplet>> ls3list) {
		Set<String> lsSet = new HashSet<>();
		for (Deque<LSTriplet> ls3stack : ls3list) {
			LSTriplet ls3 = ls3stack.peek();
			if (!isStackEnd(ls3)) {
				lsSet.add(ls3stack.peek().getLS());
			}
		}
		for (Deque<LSTriplet> ls3stack : ls3list) {
			LSTriplet ls3 = ls3stack.peek();
			if (!isStackEnd(ls3)) {
				LSTriplet ls3copy = new LSTriplet(ls3);
				String genLS = String.join("|", lsSet);
				ls3copy.setLS(genLS);
				List<LSTriplet> singleTriplet = new ArrayList<>(1);
				singleTriplet.add(ls3copy);
				LSExtractor ex = new LSExtractor(singleTriplet);
				if (!checkForFalsePositives(snippets, ex)) {
					ls3stack.push(ls3copy);
				}
			}
		}
		return ls3list;
	}

	/**
	 * @param ls3list
	 * @return
	 */
	private List<Deque<LSTriplet>> removeDuplicates(final List<Deque<LSTriplet>> ls3list) {
		List<LSTriplet> headList = new ArrayList<>(ls3list.size());
		for (Deque<LSTriplet> ls3stack : ls3list) {
			LSTriplet head = ls3stack.peek();
			if (isStackEnd(head)) {
				continue;
			}
			if (headList.contains(head)) {
				ls3stack.push(LS3_DUPLICATE);
			} else {
				headList.add(head);
				ls3stack.push(head);
			}
		}
		return ls3list;
	}

	private static boolean isStackEnd(final LSTriplet ls3) {
		return STACK_ENDS.contains(ls3);
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

	private void replacePotentialMatches(List<PotentialMatch> potentialMatches,
			List<Deque<LSTriplet>> ls3list, boolean processBLS,
			final List<Snippet> snippets) {
		for (PotentialMatch match : potentialMatches) {
			if (match.count == 1) {
				for (LSTriplet triplet : match.matches) {
					String termsRegex = match.getTermsRegex();
					for (Deque<LSTriplet> ls3stack : ls3list) {
						LSTriplet ls3 = ls3stack.peek();
						if (!isStackEnd(ls3)) {
							if (processBLS && ls3.getBLS().equals(triplet.getBLS())) {
								LSTriplet newLS3 = new LSTriplet(ls3);
								String replaceRegex = "\\b(?<!\\\\)" + termsRegex + "\\b";
								String replacement = "\\\\S{1," + termsRegex.length() + "}";
								newLS3.setBLS(newLS3.getBLS().replaceAll(replaceRegex,replacement));
								List<LSTriplet> regEx = new ArrayList<LSTriplet>();
								regEx.add(newLS3);
								LSExtractor leExt = new LSExtractor(regEx);
								if (!checkForFalsePositives(snippets, leExt)) {
									ls3stack.add(newLS3);
								}
							} else if (ls3.getALS().equals(triplet.getALS())){
								LSTriplet newLS3 = new LSTriplet(ls3);
								String replaceRegex = "\\b(?<!\\\\)" + termsRegex + "\\b";
								String replacement = "\\\\S{1," + termsRegex.length() + "}";
								newLS3.setALS(newLS3.getALS().replaceAll(replaceRegex,replacement));
								List<LSTriplet> regEx = new ArrayList<LSTriplet>();
								regEx.add(newLS3);
								LSExtractor leExt = new LSExtractor(regEx);
								if (!checkForFalsePositives(snippets, leExt)) {
									ls3stack.add(newLS3);
								}
							}
						}
					}
				}
			}
		}
	}

	public List<Deque<LSTriplet>> replacePunct(List<Deque<LSTriplet>> ls3list) {
		ls3list.parallelStream().forEach((ls3stack) -> {
			LSTriplet ls3 = ls3stack.peek();
			if (!isStackEnd(ls3)) {
				String newLS = PUNCT_PATTERN.matcher(ls3.getLS()).replaceAll("\\\\p{Punct}");
				String newBLS = PUNCT_PATTERN.matcher(ls3.getBLS()).replaceAll("\\\\p{Punct}");
				String newALS = PUNCT_PATTERN.matcher(ls3.getALS()).replaceAll("\\\\p{Punct}");
				ls3stack.push(new LSTriplet(newBLS, newLS, newALS));
			}
		});
		return ls3list;
	}

	// replace digits with '\d+'
	public List<Deque<LSTriplet>> replaceDigitsLS(List<Deque<LSTriplet>> ls3list) {
		ls3list.parallelStream().forEach((ls3stack) -> {
			LSTriplet ls3 = ls3stack.peek();
			if (!isStackEnd(ls3)) {
				String newLS = DIGIT_PATTERN.matcher(ls3.getLS()).replaceAll("\\\\d+");
				ls3stack.push(new LSTriplet(ls3.getBLS(), newLS, ls3.getALS()));
			}
		});
		return ls3list;
	}

	// replace digits with '\d+'
	public List<Deque<LSTriplet>> replaceDigitsBLSALS(List<Deque<LSTriplet>> ls3list) {
		ls3list.parallelStream().forEach((ls3stack) -> {
			LSTriplet ls3 = ls3stack.peek();
			if (!isStackEnd(ls3)) {
				String newBLS = DIGIT_PATTERN.matcher(ls3.getBLS()).replaceAll("\\\\d+");
				String newALS = DIGIT_PATTERN.matcher(ls3.getALS()).replaceAll("\\\\d+");
				ls3stack.push(new LSTriplet(newBLS, ls3.getLS(), newALS));
			}
		});
		return ls3list;
	}

	// replace white spaces with 's{1,10}'
	public List<Deque<LSTriplet>> replaceWhiteSpaces(List<Deque<LSTriplet>> ls3list) {
		ls3list.parallelStream().forEach((ls3stack) -> {
			LSTriplet ls3 = ls3stack.peek();
			if (!isStackEnd(ls3)) {
				String newBLS = WHITESPACE_PATTERN.matcher(ls3.getBLS()).replaceAll("\\\\s{1,50}");
				String newLS = WHITESPACE_PATTERN.matcher(ls3.getLS()).replaceAll("\\\\s{1,50}");
				String newALS = WHITESPACE_PATTERN.matcher(ls3.getALS()).replaceAll("\\\\s{1,50}");
				ls3stack.push(new LSTriplet(newBLS, newLS, newALS));
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
	private void trimRegEx(final List<Snippet> snippets, List<Deque<LSTriplet>> ls3list) {
		// trim from the front and back, repeat while progress is being made
		while (ls3list.parallelStream().map(ls3stack -> {
				boolean blsProgress = false;
				boolean alsProgress = false;
				LSTriplet ls3 = ls3stack.peek();
				if (!isStackEnd(ls3)) {
					LSTriplet ls3trim = new LSTriplet(ls3);
					if (ls3trim.getBLS().length() >= ls3trim.getALS().length()) {
						String origBls = ls3trim.getBLS();
						if (ls3trim.getBLS() != null && !ls3trim.getBLS().equals("")) {
							String trimmedRegex = trimFirstRegex(ls3trim.getBLS());
							ls3trim.setBLS(trimmedRegex);
							List<LSTriplet> newRegExList = new ArrayList<LSTriplet>();
							newRegExList.add(ls3trim);
							LSExtractor leEx = new LSExtractor(newRegExList);
							if (checkForFalsePositives(snippets, leEx)) {
								ls3trim.setBLS(origBls);
								blsProgress = false;
							} else {
								blsProgress = true;
							}
						}
					} else if (ls3trim.getBLS().length() <= ls3trim.getALS().length()) {
						String origAls = ls3trim.getALS();
						if (ls3trim.getALS() != null && !ls3trim.getALS().equals("")) {
							String trimmedRegex = trimLastRegex(ls3trim.getALS());
							ls3trim.setALS(trimmedRegex);
							List<LSTriplet> newRegExList = new ArrayList<LSTriplet>();
							newRegExList.add(ls3trim);
							LSExtractor leExt = new LSExtractor(newRegExList);
							boolean fps = checkForFalsePositives(snippets, leExt);
							if (fps) {
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
				}
				return (blsProgress || alsProgress);
			}).anyMatch((progress) -> progress)) {
		}
	}

	private class LSTripletTrim {
		LSTriplet ls3;
		boolean blsPrevTrimSuccess;
		boolean alsPrevTrimSuccess;
		public LSTripletTrim(LSTriplet ls3, boolean blsPrevTrimSuccess, boolean alsPrevTrimSuccess) {
			this.ls3 = ls3;
			this.blsPrevTrimSuccess = blsPrevTrimSuccess;
			this.alsPrevTrimSuccess = alsPrevTrimSuccess;
		}
	}

	/**
	 * @param als
	 * @return
	 */
	String trimLastRegex(String als) {
		String alsWithoutLastRegex = null;
		if (als.lastIndexOf("\\") == -1)
			alsWithoutLastRegex = "";
		else {
			int lastIndex = als.lastIndexOf("\\");
			if (lastIndex > 0 && als.charAt(lastIndex - 1) == '\\') {
				if (!(lastIndex > 1 && als.charAt(lastIndex - 2) == '\\')) {
					lastIndex = lastIndex - 1;					
				}
			}
			int index = lastIndex;
			index++;
			while (index < als.length()) {
				char temp = als.charAt(index++);
				if (temp == '+' || temp == '}')
					break;
			}
			if (index == als.length()) {
				if (lastIndex == 0)
					alsWithoutLastRegex = "";
				else
					alsWithoutLastRegex = als.substring(0,
							lastIndex);
			} else {
				alsWithoutLastRegex = als.substring(0, index);
			}
		}
		return alsWithoutLastRegex;
	}

	/**
	 * @param bls
	 * @return
	 */
	String trimFirstRegex(String bls) {
		char firstChar = bls.charAt(0);
		String blsWithoutFirstRegex = null;
		if (firstChar != '\\') {
			if (bls.indexOf("\\") != -1)
				blsWithoutFirstRegex = bls.substring(
						bls.indexOf("\\"), bls.length());
			else
				blsWithoutFirstRegex = "";
		} else {
			int index = 1;
			while (index < bls.length()) {
				char temp = bls.charAt(index++);
				if (temp == '+' || temp == '}')
					break;
			}
			if (index == bls.length())
				blsWithoutFirstRegex = "";
			else
				blsWithoutFirstRegex = bls.substring(index,
						bls.length());
		}
		return blsWithoutFirstRegex;
	}

	// /**
	// * For every group it determines the frequent terms. It then replaces the
	// frequent terms by the regular expression \bfrequent term\b.
	// * It replaces the terms that are not frequent by .*{1,frequent term's
	// length}
	// * @param group The group of LSTriplets on which we are performing the
	// operation.
	// * @param snippetGroups The group of all snippets.
	// */
	// private void processGroupTrimVersion(List<LSTriplet> group,
	// Map<String,List<LSTriplet>> tempGroupMap, boolean processBLS, final
	// List<Snippet> snippets){
	// Map<String, List<LSTriplet>> freqMap = new HashMap<String,
	// List<LSTriplet>>();
	// for(LSTriplet triplet : group)
	// updateMFTMapTrimVersion(triplet, processBLS, freqMap);
	// List<Map.Entry<String, List<LSTriplet>>> entryList = new
	// ArrayList<Map.Entry<String, List<LSTriplet>>>();
	// for(Map.Entry<String, List<LSTriplet>> entry : freqMap.entrySet()){
	// if(entryList.isEmpty())
	// entryList.add(entry);
	// else{
	// int i=0;
	// for(i=0;i<entryList.size();i++){
	// Map.Entry<String, List<LSTriplet>> entryListElem = entryList.get(i);
	// if(entry.getValue().size() < entryListElem.getValue().size())
	// break;
	// }
	// entryList.add(i, entry);
	// }
	// }
	// for(Map.Entry<String, List<LSTriplet>> entry : entryList){
	// List<LSTriplet> value = entry.getValue();
	// String key = entry.getKey();
	// String bls=null,als=null;
	// for(LSTriplet triplet : value){
	// if(processBLS){
	// bls = triplet.getBLS();
	// //if(!key.equals("S")){
	// triplet.setBLS(triplet.getBLS().replaceAll("\\b(?<!\\\\)"+key+"\\b",
	// "\\\\S{1,"+key.length()+"}"));//triplet.getBLS().replaceAll("?:"+key,
	// "(?:"+key+")");
	// List<LSTriplet> regEx = new ArrayList<LSTriplet>();
	// regEx.add(triplet);
	// leExt.setRegExpressions(regEx);
	// //CVScore cvScore = cv.testExtractor(snippets, leExt);
	// if(checkForFalsePositives(snippets, leExt))
	// triplet.setBLS(bls);
	// }else{
	// als = triplet.getALS();
	// //if(!key.equals("S")){
	// triplet.setALS(triplet.getALS().replaceAll("\\b(?<!\\\\)"+key+"\\b",
	// "\\\\S{1,"+key.length()+"}"));//triplet.getALS().replaceAll("?:"+key,
	// "(?:"+key+")");
	// List<LSTriplet> regEx = new ArrayList<LSTriplet>();
	// regEx.add(triplet);
	// leExt.setRegExpressions(regEx);
	// //CVScore cvScore = cv.testExtractor(snippets, leExt);
	// if(checkForFalsePositives(snippets, leExt))
	// triplet.setALS(als);
	// }
	// }
	// }
	// }
	//
	// /**
	// * Finds out all the groups that have sizes greater than 1. Calls
	// processGroup on those groups.
	// * @param tripletGroups A hashmap containing the groups. Key is LS and the
	// value is a list of LSTriplet's.
	// */
	// private void processSnippetGroupsTrimVersion(Map<String,List<LSTriplet>>
	// tripletGroups, final List<Snippet> snippets){
	// java.util.Iterator<List<LSTriplet>> iteratorSnippetGroups =
	// tripletGroups.values().iterator();
	// Map<String, List<LSTriplet>> tempGroupMap = new HashMap<String,
	// List<LSTriplet>>();
	// while(iteratorSnippetGroups.hasNext()){
	// List<LSTriplet> group = iteratorSnippetGroups.next();
	// //if(group.size() > 1){
	// processGroupTrimVersion(group, tempGroupMap, true,snippets);
	// //}
	// }
	//
	// //repeat the above steps for ALS.
	// iteratorSnippetGroups = tripletGroups.values().iterator();
	// tempGroupMap = new HashMap<String, List<LSTriplet>>();
	// while(iteratorSnippetGroups.hasNext()){
	// List<LSTriplet> group = iteratorSnippetGroups.next();
	// //if(group.size() > 1){
	// processGroupTrimVersion(group, tempGroupMap, false,snippets);
	// //}
	// }
	// }

	/**
	 * Creates a map of terms contained inside the BLS/ALS.
	 * 
	 * @param triplet
	 *            The triplet on which the processing is being performed.
	 * @param processingBLS
	 *            Specifies whether the processing is to be performed on BLS/ALS
	 * @param termToTripletMap
	 *            a map containing a term as the key and a list of triplets
	 *            containing that term as the value.
	 */
	private void updateTermToTripletMap(LSTriplet triplet,
			boolean processingBLS, Map<String, List<LSTriplet>> termToTripletMap) {
		String phrase = "";
		if (processingBLS) {
			phrase = triplet.getBLS();
		} else {
			phrase = triplet.getALS();
		}
		String[] termArray = phrase
				.split("\\\\s\\{1,50\\}|\\\\p\\{Punct\\}|\\\\d\\+");
		for (String term : termArray) {
			if (!term.equals(" ") && !term.equals("")) {
				List<LSTriplet> termContainingTriplets = termToTripletMap.get(term);
				if (termContainingTriplets == null) {
					termContainingTriplets = new ArrayList<LSTriplet>();
					termToTripletMap.put(term, termContainingTriplets);
				}
				termContainingTriplets.add(triplet);
			}
		}
	}

	public List<String> checkPattern(List<LSTriplet> listLS, File vttFile)
			throws IOException {
		List<Pattern> patterns = new ArrayList<Pattern>();
		for (LSTriplet item : listLS) {
			patterns.add(Pattern.compile(item.toStringRegEx(), Pattern.CASE_INSENSITIVE));

		}
		VTTReader vtt = new VTTReader();
		Collection<Snippet> cSp = vtt.extractSnippets(vttFile);
	    int max=0;
	    List<String> ret=null;
		for (Pattern p : patterns) {
			List<String> mat = new ArrayList<String>();
			for (Snippet item : cSp) {

				Matcher matcher = p.matcher(item.getText());
				if (matcher.find())
					mat.add(matcher.group(1));
			}
			if(mat.size()>max){
				ret=mat;
			}
			
		}

		return ret;

	}
	
	
	
	
	
	
	
	
	

	/**
	 * checks if any permutation of the terms matches against any bls or als if
	 * it does it records it,
	 * 
	 * @param prefixList
	 * @param termList
	 * @param ls3List
	 * @param processBLS
	 * @param potentialList
	 */
	private List<PotentialMatch> performPermuation(Collection<String> termList, List<Deque<LSTriplet>> ls3List, boolean processBLS) {
		List<PotentialMatch> potentialMatches = new ArrayList<>();
		for (String term : termList) {
			List<String> firstTerm = new ArrayList<>(1);
			firstTerm.add(term);
			PotentialMatch match = findMatch(firstTerm, ls3List, processBLS);
			if (match.count > 0) {
				potentialMatches.add(match);
			}
		}
		List<PotentialMatch> terminalPotentialMatches = new ArrayList<>();
		do {
			List<PotentialMatch> newPotentialMatches = new ArrayList<>();
			for (PotentialMatch potentialMatch : potentialMatches) {
				for (String term : termList) {
					List<String> newTerms = new ArrayList<String>(potentialMatch.terms);
					newTerms.add(term);
					PotentialMatch match = findMatch(newTerms, ls3List, processBLS);
					if (match.count > 0) {
						newPotentialMatches.add(match);
					}
				}
			}
			terminalPotentialMatches.addAll(potentialMatches);
			potentialMatches = newPotentialMatches;
		} while (potentialMatches.size() > 0);
		return terminalPotentialMatches;
	}

	/**
	 * checks if the permutation calculated earlier matches against any of the
	 * bls or als.
	 * 
	 * @param termList
	 * @param ls3List
	 * @param processBLS
	 * @return
	 */
	private PotentialMatch findMatch(List<String> termList,
			List<Deque<LSTriplet>> ls3List, boolean processBLS) {
		StringBuilder concatString = new StringBuilder("");
		int count = 0;
		List<LSTriplet> triplets = new ArrayList<>();
		for (int i = 0; i < termList.size(); i++) {
			if (i == termList.size() - 1) {
				concatString.append(termList.get(i));
			} else {
				concatString.append(termList.get(i) + "\\s{1,50}");
			}
		}
		for (Deque<LSTriplet> ls3stack : ls3List) {
			LSTriplet ls3 = ls3stack.peek();
			if (!isStackEnd(ls3)) {
				String matchAgainst = null;
				if (processBLS) {
					matchAgainst = ls3.getBLS();
				} else {
					matchAgainst = ls3.getALS();
				}
				if (matchAgainst != null && matchAgainst.contains(concatString)) {
					count++;
					triplets.add(ls3);
				}
			}
		}
		PotentialMatch match = new PotentialMatch(termList, count, triplets);
		return match;
	}

	/**
	 * finds out all the terms in all the bls and als. It then checks to see if
	 * any permutation of the terms matches against any bls or als. If matches
	 * it records its in a list of potential matches.
	 * 
	 * @param ls3List
	 *            the original list of triplets
	 * @param potentialListBLS
	 *            records the list of potential matches for bls
	 * @param potentialListALS
	 *            records the list of potential matches for als
	 */
	private PotentialMatches treeReplacementLogic(List<Deque<LSTriplet>> ls3List) {
		PotentialMatches potentialMatches = new PotentialMatches();
		Set<String> blsTermSet = new HashSet<>();
		for (Deque<LSTriplet> ls3stack : ls3List) {
			LSTriplet ls3 = ls3stack.peek();
			if (!isStackEnd(ls3)) {
				String[] termArray = ls3.getBLS()
						.split("\\\\s\\{1,50\\}|\\\\p\\{Punct\\}|\\\\d\\+");
				for (String term : termArray) {
					if (!term.equals(" ") && !term.equals("")) {
						blsTermSet.add(term);
					}
				}
			}
		}
		potentialMatches.potentialBLSMatches = performPermuation(/*termList*/blsTermSet, ls3List, true);

		Set<String> alsTermSet = new HashSet<>();
		for (Deque<LSTriplet> ls3stack : ls3List) {
			LSTriplet ls3 = ls3stack.peek();
			if (!isStackEnd(ls3)) {
				String[] termArray = ls3.getALS()
						.split("\\\\s\\{1,50\\}|\\\\p\\{Punct\\}|\\\\d\\+");
				for (String term : termArray) {
					if (!term.equals(" ") && !term.equals("")) {
						alsTermSet.add(term);
					}
				}
			}
		}
		potentialMatches.potentialALSMatches = performPermuation(alsTermSet, ls3List, false);
		return potentialMatches;
	}

	/**
	 * @param testing A list of snippets to use for testing.
	 * @param ex The extractor to be tested.
	 * @param pw A PrintWriter for recording output. May be <code>null</code>.
	 * @return The cross-validation score.
	 */
	public CVScore testExtractor(List<Snippet> testing, LSExtractor ex,
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
				if (snippet.getLabeledStrings().contains(predicted.trim())) {
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

	public boolean checkForFalsePositives(List<Snippet> testing, LSExtractor ex) {
		return testing.parallelStream().map((snippet) -> {
//			List<MatchedElement> candidates = ex.extract(snippet.getText());
//			String predicted = chooseBestCandidate(candidates);
			MatchedElement me = ex.extractFirst(snippet.getText());
			String predicted = (me == null ? null : me.getMatch());

			// Score
			if (predicted != null) {
				List<String> actual = snippet.getLabeledStrings();
				if (actual == null || actual.size() == 0) {
					return true;
				} else {
					if (!actual.contains(predicted.trim())) {
						return true;
					}
				}
			}
			return false;
		}).anyMatch((fp) -> fp);
	}

	public static Map<LSTriplet, TripletMatches> findTripletsWithFalsePositives(
			final List<Deque<LSTriplet>> ls3list, final List<Snippet> snippets,
			final String label) {
		Collection<String> labels = new ArrayList<>(1);
		labels.add(label);
		return findTripletsWithFalsePositives(ls3list, snippets, labels);
	}

	public static Map<LSTriplet, TripletMatches> findTripletsWithFalsePositives(
			final List<Deque<LSTriplet>> ls3list, final List<Snippet> snippets,
			final Collection<String> labels) {
		Map<LSTriplet, TripletMatches> tripsWithFP = new HashMap<>();
		for (Deque<LSTriplet> ls3stack : ls3list) {
			LSTriplet ls3 = ls3stack.peek();
			if (!isStackEnd(ls3)) {
				List<Snippet> correct = new ArrayList<>();
				List<Snippet> falsePositive = new ArrayList<>();
				Pattern ls3pattern = Pattern.compile(ls3.toStringRegEx(), Pattern.CASE_INSENSITIVE);
				for (Snippet snippet : snippets) {
					List<Snippet> lsCorrectMatch = new ArrayList<>();
					List<Snippet> lsFalseMatch = new ArrayList<>();
					for (LabeledSegment ls : snippet.getLabeledSegments()) {
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


