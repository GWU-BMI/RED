package gov.va.research.red.cat;

import gov.va.research.red.CVScore;
import gov.va.research.red.LabeledSegment;
import gov.va.research.red.RegEx;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * starting off with un-labeled segments
 * 
 * please check if no label snippet getter works properly
 */
public class REDCategorizer {
	
	private static final String P_PUNCT = "\\p{Punct}";
	static final Map<RegEx, Pattern> patternCache = new HashMap<RegEx, Pattern>();
	private static final boolean PERFORM_TRIMMING = false;
	private static final boolean PERFORM_TRIMMING_OVERALL = true;
	//private static final boolean PERFORM_USELESS_REMOVAL = true;
	private static final boolean FILE_TRANSFER = false;
	private static final String COLLAPSE_PATTERN = "\\(\\?:\\[A-Z\\]\\{1,\\d+\\}\\(\\?:\\\\s\\{1,\\d+\\}\\|\\\\p\\{Punct\\}\\\\s\\{1,\\d+\\}\\)\\)\\{1,\\d+\\}";
	private static final Pattern STARTS_WITH_COLLAPSE_PATTERN = Pattern.compile("^"+COLLAPSE_PATTERN);
	private static final Pattern ENDS_WITH_COLLAPSE_PATTERN = Pattern.compile(COLLAPSE_PATTERN+"$");

	
	/**
	 * Reads a vtt file and generates collections of positive and negative regular expressions for classification.
	 * @param vttFile The VTT file containing positive and negative snippets.
	 * @param yesLabels The snippet labels to consider positive.
	 * @param noLabels The snippet labels to consider negative.
	 * @return A two entry map containing collections of regular expressions, one collection matching positive snippets and one collection matching negative snippets.
	 * @throws IOException if there is a problem reading the vttFile
	 */
	public Map<String, Collection<RegEx>> findRegexesAndOutputResults (
			final File vttFile, List<String> yesLabels, List<String> noLabels) throws IOException {
		VTTReader vttr = new VTTReader();
		List<Snippet> snippetsYes = new ArrayList<>();
		for (String yesLabel : yesLabels) {
			snippetsYes.addAll(vttr.extractSnippets(vttFile, yesLabel, true));
		}
		//check if there is a labeled segment starting with "MPRESSION" (missing "I" from "IMPRESSION")
		/*for (Snippet s: snippetsYes) {
			LabeledSegment ls = s.getLabeledSegment("yes");
			if (ls!=null){
				String segment = ls.getLabeledString();
				System.out.println(segment); 
			}
		}*/
		List<Snippet> snippetsNo = new ArrayList<>();
		for (String noLabel : noLabels) {
			snippetsNo.addAll(vttr.extractSnippets(vttFile, noLabel, true));
		}
		List<Snippet> snippetsNoLabel = new ArrayList<Snippet>();
		snippetsNoLabel.addAll(vttr.extractSnippets(vttFile, true));
		System.out.println("snippetsYes.size = "+snippetsYes.size());
		System.out.println("snippetsNo.size = "+snippetsNo.size());
		System.out.println("snippetsNoLabel.size = "+snippetsNoLabel.size());
		List<Snippet>snippetsYesAndUnlabeled = new ArrayList<>(snippetsYes.size() + snippetsNoLabel.size());
		snippetsYesAndUnlabeled.addAll(snippetsYes);
		snippetsYesAndUnlabeled.addAll(snippetsNoLabel);
		List<Snippet> snippetsNoAndUnlabeled = new ArrayList<>(snippetsNo.size() + snippetsNoLabel.size());
		snippetsNoAndUnlabeled.addAll(snippetsNo);
		snippetsNoAndUnlabeled.addAll(snippetsNoLabel);
		Map<String, Collection<RegEx>> rtMap =  generateRegexClassifications(snippetsYes, snippetsNo, snippetsNoLabel, yesLabels, noLabels);
		/*if (FILE_TRANSFER) {
			printSnippetsFile(snippetsYes, yesLabels, "Positive");
			printSnippetsFile(snippetsNo, noLabels, "Negative");
			Map<String,Integer> wordFreqMapPos = new HashMap<>();
			Map<String,Integer> wordFreqMapNeg = new HashMap<>();
			freqPrinter(wordFreqMapPos, "Positive");
			freqPrinter(wordFreqMapNeg, "Negative");
		}
		
		List<Snippet> snippetsYesNo = new ArrayList<>();
		snippetsYesNo.addAll(snippetsYes);
		snippetsYesNo.addAll(snippetsNo);
		PrintWriter pw = new PrintWriter(new File("CategorizerError.txt"));
		CVScore testScore = testClassifier(snippetsYesNo, rtMap.get("true"), rtMap.get("false"), null, yesLabels, noLabels,pw);
		pw.close();
		System.out.println("--- Test on the original dataset ---");
		System.out.println(testScore.getEvaluation()); //*/
		/*testScore = testClassifier2(snippetsYesNo, rtMap.get("true"), rtMap.get("false"), null, yesLabels, noLabels);
		System.out.println("--- Test on the original dataset ---");
		System.out.println(testScore.getEvaluation()); //*/
		return rtMap;
	}
	
	/**
	 * Create regular expressions for classification of positive and negative snippets
	 * @param snippetsYesM Positive snippets.
	 * @param snippetsNoM Negative snippets.
	 * @param snippetsNoLabelM Snippets that are neither positive or negative.
	 * @param yesLabelsM Labels considered positive.
	 * @param noLabelsM Labels considered negative
	 * @return A two entry map containing collections of regular expressions, one collection matching positive snippets and one collection matching negative snippets.
	 */
	public Map<String, Collection<RegEx>> generateRegexClassifications(List<Snippet> snippetsYesM, List<Snippet> snippetsNoM, List<Snippet> snippetsNoLabelM, Collection<String> yesLabelsM, Collection<String> noLabelsM) {
		List<Snippet> snippetsYes = snippetsYesM;
		List<Snippet> snippetsNo = snippetsNoM;
		List<Snippet> snippetsNoLabel = snippetsNoLabelM;
		Collection<String> yesLabels = yesLabelsM;
		Collection<String> noLabels = noLabelsM;
		List<Snippet> snippetsYesAndUnlabeled = new ArrayList<>(snippetsYes.size() + snippetsNoLabel.size());
		snippetsYesAndUnlabeled.addAll(snippetsYes);
		snippetsYesAndUnlabeled.addAll(snippetsNoLabel);
		List<Snippet> snippetsNoAndUnlabeled = new ArrayList<>(snippetsNo.size() + snippetsNoLabel.size());
		snippetsNoAndUnlabeled.addAll(snippetsNo);
		snippetsNoAndUnlabeled.addAll(snippetsNoLabel);
		if (snippetsYes == null || snippetsNo == null)
			return null;
		List<Snippet> snippetsAll = new ArrayList<Snippet>();
		snippetsAll.addAll(snippetsYes);
		snippetsAll.addAll(snippetsNo);
		snippetsAll.addAll(snippetsNoLabel);
		
		List<String> segs = new ArrayList();
		List<String> inits = new ArrayList();
		Collection<RegEx> initialPositiveRegExs = initialize(snippetsYes, yesLabels, segs, inits);
		Collection<RegEx> initialNegativeRegExs = initialize(snippetsNo, noLabels, null, null);
		Map<String, List<Integer>> init2orig = new HashMap<String, List<Integer>>();
		for (int i=0; i<inits.size();i++) {
			String regExStr = inits.get(i);
			if (!init2orig.containsKey(regExStr)) {
				init2orig.put(regExStr, new ArrayList<Integer>());
			}
			init2orig.get(regExStr).add(i);
		}

		// Remove any duplicates
		initialPositiveRegExs = removeDuplicates(initialPositiveRegExs);
		initialNegativeRegExs = removeDuplicates(initialNegativeRegExs);

		// Remove any regexes that have false positives initially
		Iterator<RegEx> it = initialPositiveRegExs.iterator();
		while (it.hasNext()) {
			RegEx reg = it.next();
			if (SnippetRegexMatcher.anyMatches(reg, snippetsNoAndUnlabeled, yesLabels)) {
				it.remove();
			}
		}
		it = initialNegativeRegExs.iterator();
		while (it.hasNext()) {
			RegEx reg = it.next();
			if (SnippetRegexMatcher.anyMatches(reg, snippetsYesAndUnlabeled, noLabels)) {
				it.remove();
			}
		}
		//System.out.println(initialPositiveRegExs.size());
		// Collapse
		//collapse(initialPositiveRegExs, yesLabels, snippetsNoAndUnlabeled);
		//collapse(initialNegativeRegExs, noLabels, snippetsYesAndUnlabeled);
		Map<String,List<String>> rlf2init = new HashMap<String,List<String>>();
		// Generalize words when possible without causing FPs, from least to most frequent
		Map<String,Integer> wordFreqMapPos = createFrequencyMap(initialPositiveRegExs);
		Map<String,Integer> wordFreqMapNeg = createFrequencyMap(initialNegativeRegExs);
		removeLeastFrequent4(initialPositiveRegExs, snippetsNoAndUnlabeled, yesLabels, wordFreqMapPos, rlf2init);
		removeLeastFrequent4(initialNegativeRegExs, snippetsYesAndUnlabeled, noLabels, wordFreqMapNeg, null);
		initialPositiveRegExs = removeDuplicates(initialPositiveRegExs);
		initialNegativeRegExs = removeDuplicates(initialNegativeRegExs);
		//System.out.println(rlf2init.size()+" = "+initialPositiveRegExs.size()+" ?");
		
		Map<String,List<String>> clps2rlf = new HashMap<String,List<String>>();
		collapse(initialPositiveRegExs, yesLabels, snippetsNoAndUnlabeled, clps2rlf);
		collapse(initialNegativeRegExs, noLabels, snippetsYesAndUnlabeled, null);
		
		initialPositiveRegExs = removeDuplicates(initialPositiveRegExs);
		initialNegativeRegExs = removeDuplicates(initialNegativeRegExs);
		
		Map<String,List<String>> trim2clps = new HashMap<String,List<String>>();
		if (PERFORM_TRIMMING_OVERALL) {
			performTrimming(initialPositiveRegExs, snippetsNoAndUnlabeled, yesLabels, trim2clps);
			performTrimming(initialNegativeRegExs, snippetsYesAndUnlabeled, noLabels, null);
			//initialPositiveRegExs = removeDuplicates(initialPositiveRegExs);
			//initialNegativeRegExs = removeDuplicates(initialNegativeRegExs);
		}

		// Collapse
		//collapse(initialPositiveRegExs, yesLabels, snippetsNoAndUnlabeled);
		//collapse(initialNegativeRegExs, noLabels, snippetsYesAndUnlabeled);
		
		// Remove duplicates
		initialPositiveRegExs = removeDuplicates(initialPositiveRegExs);
		initialNegativeRegExs = removeDuplicates(initialNegativeRegExs);
		
		/*int n = 0;
		for (String trim: trim2clps.keySet()) {
			n ++;
			if (n<=20)
				continue;
			if (n>30)
				break;
			System.out.println(n+") "+trim);
			for (String clps: trim2clps.get(trim)) {
				System.out.println("\t"+clps);
				for (String rlf: clps2rlf.get(clps)) {
					System.out.println("\t\t"+rlf);
					for (String init: rlf2init.get(rlf)) {
						System.out.println("\t\t\t"+init);
						for (int i: init2orig.get(init)) {
							System.out.println("\t\t\t\t"+segs.get(i));
						}
					}
				}
			}
		}//*/
		
		
		// Calculate specificity
		calculateSensitivity(snippetsYes, initialPositiveRegExs);
		calculateSensitivity(snippetsNo, initialNegativeRegExs);//*/

		//collapse(initialPositiveRegExs, yesLabels, snippetsNoAndUnlabeled);
		//collapse(initialNegativeRegExs, noLabels, snippetsYesAndUnlabeled);
		Map<String, Collection<RegEx>> positiveAndNegativeRegEx = new HashMap<String, Collection<RegEx>>();
		positiveAndNegativeRegEx.put(Boolean.TRUE.toString(), initialPositiveRegExs);
		positiveAndNegativeRegEx.put(Boolean.FALSE.toString(), initialNegativeRegExs);
		
		return positiveAndNegativeRegEx;
	}

	/**
	 * @param labelToExcludeFromNegFPCheck
	 * @param snippetsToCheckForFP
	 * @param regExsToCollapse
	 */
	private void collapse(Collection<RegEx> regExsToCollapse,
			Collection<String> labelToExcludeFromNegFPCheck,
			List<Snippet> snippetsToCheckForFP, Map<String, List<String>> map) {
		for (RegEx regEx : regExsToCollapse) {
			String oldRegExStr = regEx.getRegEx();
			Collapser.collapse(regEx, snippetsToCheckForFP, labelToExcludeFromNegFPCheck);
			if (map==null)
				continue;
			String newRegExStr = regEx.getRegEx();
			if (!map.containsKey(newRegExStr)) {
				map.put(newRegExStr, new ArrayList<String>());
			}
			map.get(newRegExStr).add(oldRegExStr);
		}
	}

	/**
	 * @param snippets
	 * @param regexes
	 */
	private void calculateSensitivity(List<Snippet> snippets,
			Collection<RegEx> regexes) {
		for (RegEx regEx : regexes) {
			int count = 0;
			for (Snippet snippet : snippets) {
				Pattern p = patternCache.get(regEx);
				if (p == null) {
					p = Pattern.compile(regEx.getRegEx(), Pattern.CASE_INSENSITIVE);
					patternCache.put(regEx, p);
				}
				Matcher matcher = p.matcher(snippet.getText());
				if (matcher.find()) {
					count++;
				}
			}
			regEx.setSensitivity((double)count/snippets.size());
			//regEx.setSensitivity(count);
		}
	}


	private void printSnippetsFile(Collection<Snippet> snippets, Collection<String> labels, String baseFilename) throws IOException {
		File file = new File(baseFilename + "_Snippets");
		File fileLS = new File(baseFilename + "_Labeled_Segments");
		if (!file.exists()) {
			file.createNewFile();
		}
		if (!fileLS.exists()) {
			fileLS.createNewFile();
		}
		FileWriter snipWriter = new FileWriter(file);
		FileWriter lsWriter = new FileWriter(fileLS);
		for (Snippet snippet : snippets) {
			snipWriter.write("SNIPPET\n\n");
			snipWriter.write(snippet.getText());
			snipWriter.write("\n\n\n");
			for (String label : labels) {
				LabeledSegment lSeg = snippet.getPosLabeledSegment(label);
				if (lSeg != null) {
					String lSegStr = lSeg.getLabeledString();
					if (lSegStr != null && !lSegStr.equals("")) {
						lsWriter.write("LABELED SEGMENT\n\n");
						lsWriter.write(lSegStr);
						lsWriter.write("\n\n\n");
					}
				}
			}
		}
		snipWriter.close();
		lsWriter.close();
	}
	
	private void freqPrinter(Map<String,Integer> wordFreqMap, String baseFilename) throws IOException {
		File file = new File(baseFilename + "_Labeled_Segment_freq");
		if (!file.exists()) {
			file.createNewFile();
		}
		FileWriter writer = new FileWriter(file);
		
		Set<Entry<String, Integer>> entries = wordFreqMap.entrySet();
		List<Entry<String, Integer>> sortedEntryList = new ArrayList<Map.Entry<String,Integer>>(entries);
		Collections.sort(sortedEntryList, new Comparator<Entry<String, Integer>>() {

			@Override
			public int compare(Entry<String, Integer> o1,
					Entry<String, Integer> o2) {
				if (o1.getValue() > o2.getValue()) {
					return -1;
				} else if (o1.getValue() < o2.getValue()) {
					return 1;
				}
				return 0;
			}
		});
		
		for (Entry<String, Integer> entry : sortedEntryList) {
			writer.write(entry.getKey());
			writer.write("\n\n\n");
		}
		writer.close();
	}
	
	private Collection<RegEx> removeDuplicates(Collection<RegEx> regExes) {
		return new HashSet<RegEx>(regExes);
	}
	
	private List<RegEx> initialize(Collection<Snippet> snippets, Collection<String> labels, List<String> segs, List<String> inits) {
		List<RegEx> initialRegExs = null;
		initialRegExs = new ArrayList<RegEx>(snippets.size());
		initializeInitialRegEx(snippets, labels, initialRegExs);
		if (segs!=null) {
			for (RegEx regEx: initialRegExs) {
				segs.add(regEx.getRegEx().replaceAll("\\s+", " "));
			}
		}
		replacePunct(initialRegExs);
		
		replaceDigits(initialRegExs);
		
		replaceWhiteSpaces(initialRegExs);
		
		if (inits!=null) {
			for (RegEx regEx: initialRegExs) {
				inits.add(regEx.getRegEx());
			}
		}
		
		return initialRegExs;
	}
		
	private void removeLeastFrequent(Collection<RegEx> initialPositiveRegExs, Collection<Snippet> negSnippets, Collection<String> posLabels, Map<String,Integer> wordFreqMapPos) {
		Set<Entry<String, Integer>> entries = wordFreqMapPos.entrySet();
		List<Entry<String, Integer>> sortedEntryList = new ArrayList<Map.Entry<String,Integer>>(entries);
		Collections.sort(sortedEntryList, new Comparator<Entry<String, Integer>>() {
			@Override
			public int compare(Entry<String, Integer> o1,
					Entry<String, Integer> o2) {
				if (o1.getValue() > o2.getValue()) {
					return 1;
				} else if (o1.getValue() < o2.getValue()) {
					return -1;
				}
				return 0;
			}
		});
		int size = sortedEntryList.size();
		for (int i=0; i < size; i++) {
			Entry<String, Integer> leastFreqEntry = sortedEntryList.get(i);
			String leastFrequent = leastFreqEntry.getKey();
			for (RegEx regEx : initialPositiveRegExs) {
				String regExStr = regEx.getRegEx();
				String replacedString = null;
				if (regExStr.contains(leastFrequent)) {
					String replaceRegex = "}" + leastFrequent + "\\\\";
					String withString = "}[A-Z]{1," + leastFrequent.length() + "}\\\\";
					replacedString = Pattern.compile(replaceRegex, Pattern.CASE_INSENSITIVE).matcher(regExStr).replaceAll(withString);
					replaceRegex = "}" + leastFrequent + "\\[";
					withString = "}[A-Z]{1," + leastFrequent.length() + "}[";
					replacedString = Pattern.compile(replaceRegex, Pattern.CASE_INSENSITIVE).matcher(replacedString).replaceAll(withString);
					int start = 0;
					while (start < replacedString.length() && replacedString.charAt(start) != '\\' && replacedString.charAt(start) != '[') {
						start++;
					}
					if (leastFrequent.equalsIgnoreCase(replacedString.substring(0, start))) {
						replacedString = "[A-Z]{1," + leastFrequent.length() + "}" + replacedString.substring(start, replacedString.length());
					}
					int end = replacedString.length();
					end--;
					while ( end > 0 && replacedString.charAt(end) != '}' && replacedString.charAt(end) != '+') {
						end--;
					}
					if (leastFrequent.equalsIgnoreCase(replacedString.substring(end+1, replacedString.length()))) {
						replacedString = replacedString.substring(0, end+1) + "[A-Z]{1," + leastFrequent.length() + "}";
					}
					RegEx temp = new RegEx(replacedString);
					boolean fps = SnippetRegexMatcher.anyMatches(temp, negSnippets, posLabels);
					if (!fps) {
						regEx.setRegEx(replacedString);
						if (PERFORM_TRIMMING) {
							performTrimmingIndividual(regEx, negSnippets, posLabels);
						}
					}
				}
			}
		}
	}
	
	private void removeLeastFrequent2(Collection<RegEx> initialPositiveRegExs, Collection<Snippet> negSnippets, Collection<String> posLabels, Map<String,Integer> wordFreqMapPos) {
		Set<Entry<String, Integer>> entries = wordFreqMapPos.entrySet();
		List<Entry<String, Integer>> sortedEntryList = new ArrayList<Map.Entry<String,Integer>>(entries);
		Collections.sort(sortedEntryList, new Comparator<Entry<String, Integer>>() {
			@Override
			public int compare(Entry<String, Integer> o1,
					Entry<String, Integer> o2) {
				if (o1.getValue() > o2.getValue()) {
					return 1;
				} else if (o1.getValue() < o2.getValue()) {
					return -1;
				}
				return 0;
			}
		});
		int size = sortedEntryList.size();
		for (int i=0; i < size; i++) {
			Entry<String, Integer> leastFreqEntry = sortedEntryList.get(i);
			String leastFrequent = leastFreqEntry.getKey();
			for (RegEx regEx : initialPositiveRegExs) {
				String regExStr = regEx.getRegEx();
				String replacedString = null;
				if (leastFrequent.length()==1) {
					Matcher m = Pattern.compile("}"+leastFrequent+"\\b",Pattern.CASE_INSENSITIVE).matcher(regExStr);
					if (m.find()) {
						replacedString = m.replaceAll("}[A-Z]{1,"+leastFrequent.length()+"}");
					}
					if (replacedString==null) {
						m = Pattern.compile("^"+leastFrequent+"\\b",Pattern.CASE_INSENSITIVE).matcher(regExStr);
					} else {
						m = Pattern.compile("^"+leastFrequent+"\\b",Pattern.CASE_INSENSITIVE).matcher(replacedString);
					}
					if (m.find()) {
						replacedString = m.replaceAll("[A-Z]{1,"+leastFrequent.length()+"}");
					}
				} else {
					Matcher m = Pattern.compile("\\b"+leastFrequent+"\\b",Pattern.CASE_INSENSITIVE).matcher(regExStr);
					if (m.find()) {
						replacedString = m.replaceAll("[A-Z]{1,"+leastFrequent.length()+"}");
					}
				}
				if (replacedString==null)
					continue;
				RegEx temp = new RegEx(replacedString);
				boolean fps = SnippetRegexMatcher.anyMatches(temp, negSnippets, posLabels);
				if (!fps) {
					regEx.setRegEx(replacedString);
					if (PERFORM_TRIMMING) {
						performTrimmingIndividual(regEx, negSnippets, posLabels);
					} 
				}
			}
		}
	}
	
	private void removeLeastFrequent3(Collection<RegEx> initialPositiveRegExs, Collection<Snippet> negSnippets, Collection<String> posLabels, Map<String,Integer> wordFreqMapPos) {
		Set<Entry<String, Integer>> entries = wordFreqMapPos.entrySet();
		List<Entry<String, Integer>> sortedEntryList = new ArrayList<Map.Entry<String,Integer>>(entries);
		Collections.sort(sortedEntryList, new Comparator<Entry<String, Integer>>() {
			@Override
			public int compare(Entry<String, Integer> o1,
					Entry<String, Integer> o2) {
				if (o1.getValue() > o2.getValue()) {
					return 1;
				} else if (o1.getValue() < o2.getValue()) {
					return -1;
				}
				return 0;
			}
		});
		int size = sortedEntryList.size();
		for (RegEx regEx : initialPositiveRegExs) {
			for (int i=0; i < size; i++) {
				Entry<String, Integer> leastFreqEntry = sortedEntryList.get(i);
				String leastFrequent = leastFreqEntry.getKey();
				String regExStr = regEx.getRegEx();
				String replacedString = null;
				if (leastFrequent.length()==1) {
					Matcher m = Pattern.compile("}"+leastFrequent+"\\b",Pattern.CASE_INSENSITIVE).matcher(regExStr);
					if (m.find()) {
						replacedString = m.replaceAll("}[A-Z]{1,"+leastFrequent.length()+"}");
					}
					if (replacedString==null) {
						m = Pattern.compile("^"+leastFrequent+"\\b",Pattern.CASE_INSENSITIVE).matcher(regExStr);
					} else {
						m = Pattern.compile("^"+leastFrequent+"\\b",Pattern.CASE_INSENSITIVE).matcher(replacedString);
					}
					if (m.find()) {
						replacedString = m.replaceAll("[A-Z]{1,"+leastFrequent.length()+"}");
					}
				} else {
					Matcher m = Pattern.compile("\\b"+leastFrequent+"\\b",Pattern.CASE_INSENSITIVE).matcher(regExStr);
					if (m.find()) {
						replacedString = m.replaceAll("[A-Z]{1,"+leastFrequent.length()+"}");
					}
				}
				if (replacedString==null)
					continue;
				RegEx temp = new RegEx(replacedString);
				boolean fps = SnippetRegexMatcher.anyMatches(temp, negSnippets, posLabels);
				if (!fps) {
					regEx.setRegEx(replacedString);
					if (PERFORM_TRIMMING) {
						performTrimmingIndividual(regEx, negSnippets, posLabels);
					} 
				}
			}
		}
	}
	
	private void removeLeastFrequent4(Collection<RegEx> initialPositiveRegExs, Collection<Snippet> negSnippets, Collection<String> posLabels, Map<String,Integer> wordFreqMapPos, Map<String, List<String>> map) {
		Set<Entry<String, Integer>> entries = wordFreqMapPos.entrySet();
		List<Entry<String, Integer>> sortedEntryList = new ArrayList<Map.Entry<String,Integer>>(entries);
		Collections.sort(sortedEntryList, new Comparator<Entry<String, Integer>>() {
			@Override
			public int compare(Entry<String, Integer> o1,
					Entry<String, Integer> o2) {
				if (o1.getValue() > o2.getValue()) {
					return 1;
				} else if (o1.getValue() < o2.getValue()) {
					return -1;
				}
				return 0;
			}
		});
		int size = sortedEntryList.size();
		Matcher m;
		for (RegEx regEx : initialPositiveRegExs) {
			String oldRegExStr = regEx.getRegEx();
			for (int i=0; i < size; i++) {
				Entry<String, Integer> leastFreqEntry = sortedEntryList.get(i);
				String leastFrequent = leastFreqEntry.getKey();
				String regExStr = regEx.getRegEx();
				boolean matchFound = false;
				if (leastFrequent.length()==1) {
					m = Pattern.compile("}"+leastFrequent+"\\b",Pattern.CASE_INSENSITIVE).matcher(regExStr);
					if (m.find()) {
						regExStr = m.replaceAll("}[A-Z]{1,"+leastFrequent.length()+"}");
						matchFound = true;
					}
					m = Pattern.compile("^"+leastFrequent+"\\b",Pattern.CASE_INSENSITIVE).matcher(regExStr);
					if (m.find()) {
						regExStr = m.replaceAll("[A-Z]{1,"+leastFrequent.length()+"}");
						matchFound = true;
					}
				} else {
					m = Pattern.compile("\\b"+leastFrequent+"\\b",Pattern.CASE_INSENSITIVE).matcher(regExStr);
					if (m.find()) {
						regExStr = m.replaceAll("[A-Z]{1,"+leastFrequent.length()+"}");
						matchFound = true;
					}
				}
				if (!matchFound) {
					continue;
				}
				RegEx tempRegEx = new RegEx(regExStr);
				boolean fps = SnippetRegexMatcher.anyMatches(tempRegEx, negSnippets, posLabels);
				if (!fps) {
					regEx.setRegEx(regExStr);
					if (PERFORM_TRIMMING) {
						performTrimmingIndividual(regEx, negSnippets, posLabels);
					} 
				}
			}
			if (map==null)
				continue;
			String newRegExStr = regEx.getRegEx();
			if (!map.containsKey(newRegExStr)) {
				map.put(newRegExStr, new ArrayList<String>());
			}
			map.get(newRegExStr).add(oldRegExStr);
		}
	}
	
	private Map<String,Integer> createFrequencyMap(Collection<RegEx> initialRegExs) {
		Map<String,Integer> wordFreqMap = new HashMap<>();
		for (RegEx regEx : initialRegExs) {
			String[] regExStrArray = regEx.getRegEx().split("\\\\s\\{1,10\\}|\\\\p\\{Punct\\}|\\\\d\\{1,50\\}");
			for (String word : regExStrArray) {
				String lowerCase = word.toLowerCase();
				if (!lowerCase.equals("")) {
					if (wordFreqMap.containsKey(lowerCase)) {
						int count = wordFreqMap.get(lowerCase);
						wordFreqMap.put(lowerCase, ++count);
					} else {
						wordFreqMap.put(lowerCase, 1);
					}
				}
			}
		}
		return wordFreqMap;
	}
	
	private void performTrimming(Collection<RegEx> initialPositiveRegExs, Collection<Snippet> negSnippets, Collection<String> posLabels, Map<String, List<String>> map) {
		for (RegEx regEx : initialPositiveRegExs) {
			boolean frontTrim = true;
			boolean backTrim = true;
			String oldRegExStr = regEx.getRegEx();
			while (true) {
				if (frontTrim) {
					RegEx frontTrimRegEx = frontTrim(regEx);
					if (frontTrimRegEx == null) {
						frontTrim = false;
					} else {
						if (SnippetRegexMatcher.anyMatches(frontTrimRegEx, negSnippets, posLabels)) {
							frontTrim = false;
						} else {
							regEx.setRegEx(frontTrimRegEx.getRegEx());
						}
					}
				}
				if (backTrim) {
					RegEx backTrimRegEx = backTrim(regEx);
					if (backTrimRegEx == null) {
						backTrim = false;
					} else {
						if (SnippetRegexMatcher.anyMatches(backTrimRegEx, negSnippets, posLabels)) {
							backTrim = false;
						} else {
							regEx.setRegEx(backTrimRegEx.getRegEx());
						}
					}
				}
				if (!frontTrim && !backTrim) {
					break;
				}
			}
			if (map==null)
				continue;
			String newRegExStr = regEx.getRegEx();
			if (!map.containsKey(newRegExStr)) {
				map.put(newRegExStr, new ArrayList<String>());
			}
			map.get(newRegExStr).add(oldRegExStr);
		}
	}
	
	private void performTrimmingIndividual(RegEx regEx, Collection<Snippet> negSnippets, Collection<String> posLabels) {
		boolean frontTrim = true;
		boolean backTrim = true;
		if (frontTrim) {
			RegEx frontTrimRegEx = frontTrim(regEx);
			if (frontTrimRegEx == null) {
				frontTrim = false;
			} else {
				if (SnippetRegexMatcher.anyMatches(frontTrimRegEx, negSnippets, posLabels)) {
					frontTrim = false;
				} else {
					regEx.setRegEx(frontTrimRegEx.getRegEx());
				}
			}
		}
		if (backTrim) {
			RegEx backTrimRegEx = backTrim(regEx);
			if (backTrimRegEx == null) {
				backTrim = false;
			} else {
				if (SnippetRegexMatcher.anyMatches(backTrimRegEx, negSnippets, posLabels)) {
					backTrim = false;
				} else {
					regEx.setRegEx(backTrimRegEx.getRegEx());
				}
			}
		}
	}
	
	private RegEx frontTrim(RegEx regEx) {
		String regExStr = regEx.getRegEx();
		Matcher m = STARTS_WITH_COLLAPSE_PATTERN.matcher(regExStr);
		String newRegExStr = null;
		if (m.find()) {
			newRegExStr = regExStr.substring(m.end());
		} else {
			char start = regExStr.charAt(0);
			int cutLocation=1;
			if (start == '\\' || start == '[') {
				while(true){
					if (cutLocation == regExStr.length()) {
						return null;
					}
					char currentChar = regExStr.charAt(cutLocation++);
					if (currentChar == '+' || currentChar == '}') {
						break;
					}
				}
			} else {
				return null;
			}
			newRegExStr = regExStr.substring(cutLocation);
		}
		return new RegEx(newRegExStr);
	}
	
	private RegEx backTrim(RegEx regEx) {
		String regExStr = regEx.getRegEx();
		Matcher m = ENDS_WITH_COLLAPSE_PATTERN.matcher(regExStr);
		String newRegExStr = null;
		if (m.find()) {
			newRegExStr = regExStr.substring(0, m.start());
		} else {
			char end = regExStr.charAt(regExStr.length()-1);
			int cutLocation=regExStr.length()-2;
			boolean foundAZRegEx = false;
			if (end == '+' || end == '}') {
				while(true){
					if (cutLocation < 0) {
						return null;
					}
					char currentChar = regExStr.charAt(cutLocation--);
					if (!foundAZRegEx && currentChar == '\\') {
						cutLocation = cutLocation+1;
						break;
					}
					if (foundAZRegEx && currentChar == '[') {
						cutLocation = cutLocation+1;
						break;
					}
					if (currentChar == ']') {
						foundAZRegEx = true;
					}
				}
			} else {
				return null;
			}
			newRegExStr = regExStr.substring(0,cutLocation);
		}
		return new RegEx(newRegExStr);
	}
	
	private void initializeInitialRegEx(final Collection<Snippet> snippets,
			Collection<String> labels, Collection<RegEx> initialRegExs) {
		for (Snippet snippet : snippets) {
			for (String label : labels) {
				LabeledSegment labeledSegment = snippet.getPosLabeledSegment(label);
				if ( labeledSegment != null ) {
					String regExString = labeledSegment.getLabeledString();
					if (regExString != null && !regExString.isEmpty()) {
						//initialRegExs.add(new RegEx(regExString));
						initialRegExs.add(new RegEx(regExString.toLowerCase())); //convert to lowercase.
					}
				}
			}
		}
	}
	
	public CVScore testClassifier(List<Snippet> testing, Collection<RegEx> regularExpressions, Collection<RegEx> negativeRegularExpressions, CategorizerTester tester, Collection<String> yesLabels, Collection<String> noLabels, PrintWriter pw) throws IOException {
		System.out.println("::: test on snippets :::");
		CVScore score = new CVScore();
		if(tester == null)
			tester = new CategorizerTester();
		for(Snippet testSnippet : testing){
			boolean actual = false;
			//String segment = "";
			for(String label : yesLabels){
				LabeledSegment actualSegment = testSnippet.getPosLabeledSegment(label);
				if(actualSegment != null) {
					actual = true;
					//segment = actualSegment.getLabeledString();
					break;
				}
			}
			boolean predicted = tester.test(regularExpressions, negativeRegularExpressions, testSnippet, actual, pw);
			if(actual && predicted)
				score.setTp(score.getTp() + 1);
			else if(!actual && !predicted)
				score.setTn(score.getTn() + 1);
			else if(predicted && !actual)
				score.setFp(score.getFp() + 1);
			else if(!predicted && actual)
				score.setFn(score.getFn() + 1);
		}
		return score;
	}
	
	public CVScore testClassifier2(List<Snippet> testing, Collection<RegEx> regularExpressions, Collection<RegEx> negativeRegularExpressions, CategorizerTester tester, Collection<String> yesLabels, Collection<String> noLabels) throws IOException {
		System.out.println("::: test on labeled segments :::");
		CVScore score = new CVScore();
		if(tester == null)
			tester = new CategorizerTester();
		for(Snippet testSnippet : testing){
			boolean actual = false;
			String segment = "";
			for(String label : yesLabels){
				LabeledSegment actualSegment = testSnippet.getPosLabeledSegment(label);
				if(actualSegment != null) {
					actual = true;
					segment = actualSegment.getLabeledString();
					break;
				}
			}
			if (!actual){
				for(String label : noLabels){
					LabeledSegment actualSegment = testSnippet.getPosLabeledSegment(label);
					if(actualSegment != null) {
						//actual = true;
						segment = actualSegment.getLabeledString();
						break;
					}
				}
			}
			if (segment == "") continue;
			boolean predicted = tester.test2(regularExpressions, negativeRegularExpressions, segment, actual);
			if(actual && predicted)
				score.setTp(score.getTp() + 1);
			else if(!actual && !predicted)
				score.setTn(score.getTn() + 1);
			else if(predicted && !actual)
				score.setFp(score.getFp() + 1);
			else if(!predicted && actual)
				score.setFn(score.getFn() + 1);
		}
		return score;
	}
	
	public Collection<RegEx> replaceDigits(Collection<RegEx> regexColl)
	{
		for(RegEx x : regexColl)
		{
			x.setRegEx(x.getRegEx().replaceAll("\\d+","\\\\d{1,50}"));
		}
		return regexColl;
	}
	
	public Collection<RegEx> replaceWhiteSpaces(Collection<RegEx> regexColl)
	{
		for(RegEx x : regexColl)
		{
			x.setRegEx(x.getRegEx().replaceAll("\\s+","\\\\s{1,10}"));
		}
		return regexColl;
	}
	
	public Collection<RegEx> replacePunct(Collection<RegEx> regexColl)
	{
		for(RegEx x : regexColl)
		{
			x.setRegEx(x.getRegEx().replaceAll(P_PUNCT,"\\\\p{Punct}"));
		}
		return regexColl;
	}
}
