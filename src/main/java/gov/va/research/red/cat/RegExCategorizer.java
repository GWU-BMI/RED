package gov.va.research.red.cat;

import gov.va.research.red.CVScore;
import gov.va.research.red.LabeledSegment;
import gov.va.research.red.RegEx;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
public class RegExCategorizer {
	
	private static final Map<RegEx, Pattern> patternCache = new HashMap<RegEx, Pattern>();
	private int leastfreqWordRemovalLevel = 10;
	private static final String POSITIVE = "POSITIVE";
	private static final String NEGATIVE = "NEGATIVE";
	private static final boolean PERFORM_TRIMMING = true;
	private static final boolean PERFORM_TRIMMING_OVERALL = true;
	private static final boolean PERFORM_USELESS_REMOVAL_OVERALL = true;
	private static final boolean PERFORM_USELESS_REMOVAL = true;
	private static final boolean FILE_TRANSFER = true;
		
	public Map<String, Collection<RegEx>> findRegexesAndSaveInFile (
			final File vttFile, List<String> yesLabels, List<String> noLabels,
			final String classifierOutputFileName, boolean printScore) throws IOException {
		VTTReader vttr = new VTTReader();
		List<Snippet> snippetsYes = new ArrayList<>();
		for (String yesLabel : yesLabels) {
			snippetsYes.addAll(vttr.extractSnippets(vttFile, yesLabel));
		}
		List<Snippet> snippetsNo = new ArrayList<>();
		for (String noLabel : noLabels) {
			snippetsNo.addAll(vttr.extractSnippets(vttFile, noLabel));
		}
		List<Snippet> snippetsNoLabel = new ArrayList<Snippet>();
		snippetsNoLabel.addAll(vttr.extractSnippets(vttFile));
		List<Snippet>snippetsYesAndUnlabeled = new ArrayList<>(snippetsYes.size() + snippetsNoLabel.size());
		snippetsYesAndUnlabeled.addAll(snippetsYes);
		snippetsYesAndUnlabeled.addAll(snippetsNoLabel);
		List<Snippet> snippetsNoAndUnlabeled = new ArrayList<>(snippetsNo.size() + snippetsNoLabel.size());
		snippetsNoAndUnlabeled.addAll(snippetsNo);
		snippetsNoAndUnlabeled.addAll(snippetsNoLabel);
		Map<String, Collection<RegEx>> rtMap =  extractRegexClassifications(snippetsYes, snippetsNo, snippetsNoLabel, yesLabels, noLabels);
		if (FILE_TRANSFER) {
			printSnippetsFile(snippetsYes, yesLabels, "Positive");
			printSnippetsFile(snippetsNo, noLabels, "Negative");
			Map<String,Integer> wordFreqMapPos = new HashMap<>();
			Map<String,Integer> wordFreqMapNeg = new HashMap<>();
			freqPrinter(wordFreqMapPos, "Positive");
			freqPrinter(wordFreqMapNeg, "Negative");
		}
		return rtMap;
	}
	
	public Map<String, Collection<RegEx>> extractRegexClassifications(List<Snippet> snippetsYesM, List<Snippet> snippetsNoM, List<Snippet> snippetsNoLabelM, List<String> yesLabelsM, List<String> noLabelsM) throws IOException {
		List<Snippet> snippetsYes = snippetsYesM;
		List<Snippet> snippetsNo = snippetsNoM;
		List<Snippet> snippetsNoLabel = snippetsNoLabelM;
		List<String> yesLabels = yesLabelsM;
		List<String> noLabels = noLabelsM;
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
		
		Collection<RegEx> initialPositiveRegExs = initialize(snippetsYes, yesLabels);
		Collection<RegEx> initialNegativeRegExs = initialize(snippetsNo, noLabels);
		removeDuplicates(initialPositiveRegExs);
		removeDuplicates(initialNegativeRegExs);

		Iterator<RegEx> it = initialPositiveRegExs.iterator();
		while (it.hasNext()) {
			RegEx reg = it.next();
			if (anyMatches(reg, snippetsNoAndUnlabeled, yesLabels)) {
				it.remove();
			}
		}
		
		it = initialNegativeRegExs.iterator();
		while (it.hasNext()) {
			RegEx reg = it.next();
			if (anyMatches(reg, snippetsYesAndUnlabeled, noLabels)) {
				it.remove();
			}
		}
		initialPositiveRegExs = removeDuplicates(initialPositiveRegExs);
		initialNegativeRegExs = removeDuplicates(initialNegativeRegExs);
		
		
		Map<String,Integer> wordFreqMapPos = createFrequencyMap(initialPositiveRegExs);
		Map<String,Integer> wordFreqMapNeg = createFrequencyMap(initialNegativeRegExs);
		removeLeastFrequent(initialPositiveRegExs, snippetsNoAndUnlabeled, yesLabels, wordFreqMapPos);
		removeLeastFrequent(initialNegativeRegExs, snippetsYesAndUnlabeled, noLabels, wordFreqMapNeg);
		initialPositiveRegExs = removeDuplicates(initialPositiveRegExs);
		initialNegativeRegExs = removeDuplicates(initialNegativeRegExs);
		
		
		if (PERFORM_TRIMMING_OVERALL) {
			performTrimming(initialPositiveRegExs, snippetsNoAndUnlabeled, yesLabels);
			performTrimming(initialNegativeRegExs, snippetsYesAndUnlabeled, noLabels);
			initialPositiveRegExs = removeDuplicates(initialPositiveRegExs);
			initialNegativeRegExs = removeDuplicates(initialNegativeRegExs);
		}
		
		
		// collapsing
		
		/*for (RegEx regEx : initialPositiveRegExs) {
			collapse(regEx, true);
		}
		
		for (RegEx regEx : initialNegativeRegExs) {
			collapse(regEx, false);
		}
		removeDuplicates(initialPositiveRegExs);
		removeDuplicates(initialNegativeRegExs);*/
		
		// end collapsing
		
		if (PERFORM_USELESS_REMOVAL_OVERALL) {
			for (RegEx regEx : initialPositiveRegExs) {
				uselessRegRemover(regEx, snippetsNoAndUnlabeled, yesLabels);
			}
			for (RegEx regEx : initialNegativeRegExs) {
				uselessRegRemover(regEx, snippetsYesAndUnlabeled, noLabels);
			}
		}
		
		for (RegEx regEx : initialPositiveRegExs) {
			overallCollapser(regEx);
		}
		for (RegEx regEx : initialNegativeRegExs) {
			overallCollapser(regEx);
		}
		initialPositiveRegExs = removeDuplicates(initialPositiveRegExs);
		initialNegativeRegExs = removeDuplicates(initialNegativeRegExs);
		
		
		for (RegEx regEx : initialPositiveRegExs) {
			int specifity = 0;
			for (Snippet snippet : snippetsYes) {
				Pattern p = patternCache.get(regEx);
				if (p == null) {
					p = Pattern.compile(regEx.getRegEx(), Pattern.CASE_INSENSITIVE);
					patternCache.put(regEx, p);
				}
				Matcher matcher = p.matcher(snippet.getText());
				if (matcher.find()) {
					specifity++;
				}
			}
			regEx.setSpecifity((double)specifity/initialPositiveRegExs.size());
		}
		
		for (RegEx regEx : initialNegativeRegExs) {
			int specifity = 0;
			for (Snippet snippet : snippetsNo) {
				Pattern p = patternCache.get(regEx);
				if (p == null) {
					p = Pattern.compile(regEx.getRegEx(), Pattern.CASE_INSENSITIVE);
					patternCache.put(regEx, p);
				}
				Matcher matcher = p.matcher(snippet.getText());
				if (matcher.find()) {
					specifity++;
				}
			}
			regEx.setSpecifity((double)specifity/initialNegativeRegExs.size());
		}
		
		/*for (RegEx regEx : initialPositiveRegExs) {
			regEx.setRegEx("(?i)"+regEx.getRegEx());
		}
		
		for (RegEx regEx : initialNegativeRegExs) {
			regEx.setRegEx("(?i)"+regEx.getRegEx());
		}*/
		
		//cvScore = testClassifier(snippetsAll, initialPositiveRegExs, initialNegativeRegExs, null, yesLabels);
		/*System.out.println("Pos regex");
		for (RegEx regEx : initialPositiveRegExs) {
			System.out.println(regEx.getRegEx()+"\t"+regEx.getSpecifity()+"\n");
		}
		System.out.println("\nNeg regex");
		for (RegEx regEx : initialNegativeRegExs) {
			System.out.println(regEx.getRegEx()+"\t"+regEx.getSpecifity()+"\n");
		}*/
		/*System.out.println(cvScore.getEvaluation());*/
		Map<String, Collection<RegEx>> positiveAndNegativeRegEx = new HashMap<String, Collection<RegEx>>();
		positiveAndNegativeRegEx.put(POSITIVE, initialPositiveRegExs);
		positiveAndNegativeRegEx.put(NEGATIVE, initialNegativeRegExs);
		
		return positiveAndNegativeRegEx;
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
				LabeledSegment lSeg = snippet.getLabeledSegment(label);
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

	private void uselessRegRemover(RegEx regEx, List<Snippet> negSnippets, List<String> posLabels){		
		String regExStr = regEx.getRegEx();
		StringBuilder regExStrBld = new StringBuilder(regExStr);
		String backup = null;
		int start = 0,end =0;
		while (true) {
			backup = regExStrBld.toString();
			while (start < backup.length() && backup.charAt(start) != '\\' && backup.charAt(start) != '[') {
				start++;
				end++;
			}
			if (start == backup.length()) {
				break;
			}
			while (end < backup.length() && backup.charAt(end) != '}' && backup.charAt(end) != '+') {
				end++;
			}
			end++;
			if (end == backup.length()) {
				break;
			}
			regExStrBld.replace(start, end, "");
			regEx.setRegEx(regExStrBld.toString());
			if (!anyMatches(regEx, negSnippets, posLabels)) {
				end = start;
			} else {
				start = end;
				regEx.setRegEx(backup);
				regExStrBld = new StringBuilder(backup);
			}
		}
	}
	
	private void overallCollapser(RegEx regEx) {
		String regStr = regEx.getRegEx();
		int splitter = 0;
		while (regStr.substring(splitter, regStr.length()).contains("[A-Za-z ]")) {
			int startIndex = regStr.substring(splitter, regStr.length()).indexOf("[A-Za-z ]");
			startIndex = startIndex + splitter;
			int start = startIndex;
			int tempEnd,tempStart,sum=0;
			tempStart = startIndex+12;
			tempEnd = tempStart + 2;
			sum += Integer.parseInt(regStr.substring(tempStart, tempEnd));
			boolean found = false;
			int end = startIndex+15;
			splitter = end;
			while (end <= regEx.getRegEx().length()-15) {
				boolean breakCond = true;
				if (regStr.substring(end, end+9).equals("[A-Za-z ]")) {
					end = end + 15;
					tempStart = end - 3;
					tempEnd = tempStart + 2;
					sum += Integer.parseInt(regStr.substring(tempStart, tempEnd));
					breakCond = false;
					found = true;
				}
				if (breakCond) {
					break;
				}
			}
			if (found) {
				StringBuilder regStrBld = new StringBuilder(regStr);
				if (sum < 10) {
					regStrBld.replace(start, end, "[A-Za-z ]{1,0"+sum+"}");
				}else {
					regStrBld.replace(start, end, "[A-Za-z ]{1,"+sum+"}");
				}
				splitter = start + 15;
				regEx.setRegEx(regStrBld.toString());
				regStr = regStrBld.toString();
			}
		}
	}
	
	private void collapserPunct(RegEx regEx, List<Snippet> negSnippets, List<String> posLabels) {
		String regStr = regEx.getRegEx();
		int splitter = 0;
		while (regStr.substring(splitter, regStr.length()).contains("[A-Za-z ]")) {
			int startIndex = regStr.substring(splitter, regStr.length()).indexOf("[A-Za-z ]");
			startIndex = startIndex + splitter;
			int tempEnd,tempStart,sum=0;
			tempStart = startIndex+12;
			tempEnd = tempStart + 2;
			sum += Integer.parseInt(regStr.substring(tempStart, tempEnd));
			boolean found = false;
			int start = startIndex;
			while (start > 0) {
				boolean breakCond = true;
				if (start > 7 && regStr.substring(start - 8, start).equals("\\s{1,10}")) {
					breakCond = false;
					sum += 8;
					start = start - 8;
				}
				if (start > 8 && regStr.substring(start - 9, start).equals("\\p{Punct}")) {
					breakCond = false;
					sum += 5;
					found = true;
					start = start - 9;
				}
				if (breakCond) {
					break;
				}
			}
			int end = startIndex+15;
			splitter = end;
			while (end <= regEx.getRegEx().length()) {
				boolean breakCond = true;
				if ((end+8) <= regEx.getRegEx().length() && regStr.substring(end, end+8).equals("\\s{1,10}")) {
					end = end + 8;
					sum += 8;
					breakCond = false;
				}
				if ((end+9) <= regEx.getRegEx().length() && regStr.substring(end, end+9).equals("\\p{Punct}")) {
					breakCond = false;
					sum += 5;
					found = true;
					end = end + 9;
				}
				if (breakCond) {
					break;
				}
			}
			if (found) {
				StringBuilder regStrBld = new StringBuilder(regStr);
				if (sum < 10) {
					regStrBld.replace(start, end, "[A-Za-z \\p{Punct}]{1,0"+sum+"}");
				}else {
					regStrBld.replace(start, end, "[A-Za-z \\p{Punct}]{1,"+sum+"}");
				}
				regEx.setRegEx(regStrBld.toString());
				if (!anyMatches(regEx, negSnippets, posLabels)) {
					regStr = regStrBld.toString();
					splitter = start + 24;
				} else {
					regEx.setRegEx(regStr);
					splitter = startIndex + 15;
				}
			}
		}
		
		regStr = regEx.getRegEx();
		splitter = 0;
		boolean foundSpace = false;
		while (regStr.substring(splitter, regStr.length()).contains("[A-Za-z]")) {
			int startIndex = regStr.substring(splitter, regStr.length()).indexOf("[A-Za-z]");
			startIndex = startIndex + splitter;
			int tempEnd,tempStart,sum=0;
			tempStart = startIndex+11;
			tempEnd = tempStart + 2;
			sum += Integer.parseInt(regStr.substring(tempStart, tempEnd));
			boolean found = false;
			int start = startIndex;
			while (start > 0) {
				boolean breakCond = true;
				if (start > 7 && regStr.substring(start - 8, start).equals("\\s{1,10}")) {
					breakCond = false;
					sum += 8;
					start = start - 8;
					foundSpace = true;
				}
				if (start > 8 && regStr.substring(start - 9, start).equals("\\p{Punct}")) {
					breakCond = false;
					sum += 5;
					found = true;
					start = start - 9;
				}
				if (breakCond) {
					break;
				}
			}
			int end = startIndex+14;
			splitter = end;
			while (end <= regEx.getRegEx().length()) {
				boolean breakCond = true;
				if ((end+8) <= regEx.getRegEx().length() && regStr.substring(end, end+8).equals("\\s{1,10}")) {
					end = end + 8;
					sum += 8;
					breakCond = false;
					foundSpace = true;
				}
				if ((end+9) <= regEx.getRegEx().length() && regStr.substring(end, end+9).equals("\\p{Punct}")) {
					breakCond = false;
					sum += 5;
					found = true;
					end = end + 9;
				}
				if (breakCond) {
					break;
				}
			}
			if (found && foundSpace) {
				StringBuilder regStrBld = new StringBuilder(regStr);
				if (sum < 10) {
					regStrBld.replace(start, end, "[A-Za-z \\p{Punct}]{1,0"+sum+"}");
				}else {
					regStrBld.replace(start, end, "[A-Za-z \\p{Punct}]{1,"+sum+"}");
				}
				regEx.setRegEx(regStrBld.toString());
				if (!anyMatches(regEx, negSnippets, posLabels)) {
					regStr = regStrBld.toString();
					splitter = start + 24;
				} else {
					regEx.setRegEx(regStr);
					splitter = startIndex + 14;
				}
			}
			else if (found) {
				StringBuilder regStrBld = new StringBuilder(regStr);
				if (sum < 10) {
					regStrBld.replace(start, end, "[A-Za-z\\p{Punct}]{1,0"+sum+"}");
				}else {
					regStrBld.replace(start, end, "[A-Za-z\\p{Punct}]{1,"+sum+"}");
				}
				regEx.setRegEx(regStrBld.toString());
				if (!anyMatches(regEx, negSnippets, posLabels)) {
					regStr = regStrBld.toString();
					splitter = start + 23;
				} else {
					regEx.setRegEx(regStr);
					splitter = startIndex + 14;
				}
			}
		}
	}
	
	private void collapse(RegEx regEx, List<Snippet> negSnippets, List<String> posLabels) {
		String regStr = regEx.getRegEx();
		int initialStart=0,initalEnd = 0;
		while (regStr.contains("[a-zA-Z]")) {
			int startIndex = regStr.indexOf("[a-zA-Z]");
			int start = startIndex;
			int tempStart,tempEnd, totalSum;
			tempStart = startIndex + 11;
			tempEnd = tempStart + 2;
			totalSum = Integer.parseInt(regStr.substring(tempStart, tempEnd));
			initialStart = start;
			boolean found = false;
			while (start > 7) {
				boolean breakCond = true;
				if (regStr.substring(start-8, start).equals("\\s{1,10}")) {
					start = start - 8;
					breakCond = false;
					found = true;
					totalSum += 10;
				}
				if (start > 13) {
					if (regStr.substring(start-14, start-6).equals("[a-zA-Z]")) {
						start = start - 14;
						tempStart = start + 11;
						tempEnd = tempStart + 2;
						totalSum += Integer.parseInt(regStr.substring(tempStart, tempEnd));
						breakCond = false;
						found = true;
					}
				}
				if (breakCond) {
					break;
				}
			}
			int end = startIndex+14;
			initalEnd = end;
			while (end <= regEx.getRegEx().length()-8) {
				boolean breakCond = true;
				if (regStr.substring(end, end + 8).equals("\\s{1,10}")) {
					end = end + 8;
					breakCond = false;
					found = true;
					totalSum += 10;
				}
				if (end <=  regEx.getRegEx().length()-14) {
					if (regStr.substring(end, end+8).equals("[a-zA-Z]")) {
						end = end + 14;
						tempStart = end-3;
						tempEnd = tempStart + 2;
						totalSum += Integer.parseInt(regStr.substring(tempStart, tempEnd));
						breakCond = false;
						found = true;
					}
				}
				if (breakCond) {
					break;
				}
			}
			if (found) {
				StringBuilder regStrBld = new StringBuilder(regStr);
				totalSum = totalSum + 10;
				if (totalSum < 10) {
					regStrBld.replace(start, end, "[A-Za-z ]{1,0"+totalSum+"}");
				}else {
					regStrBld.replace(start, end, "[A-Za-z ]{1,"+totalSum+"}");
				}
				regEx.setRegEx(regStrBld.toString());
				overallCollapser(regEx);
				if (!anyMatches(regEx, negSnippets, posLabels)) {
					regStr = regStrBld.toString();
				} else {
					regStrBld = new StringBuilder(regStr);
					regStrBld.replace(initialStart, initalEnd-6, "[A-Za-z]");
					regEx.setRegEx(regStrBld.toString());
					regStr = regStrBld.toString();
				}
			} else {
				StringBuilder regStrBld = new StringBuilder(regStr);
				regStrBld.replace(start, end-6, "[A-Za-z]");
				regEx.setRegEx(regStrBld.toString());
				regStr = regStrBld.toString();
			}
		}
	}
	
	private List<RegEx> initialize(List<Snippet> snippets, List<String> labels) {
		List<RegEx> initialRegExs = null;
		initialRegExs = new ArrayList<RegEx>(snippets.size());
		initializeInitialRegEx(snippets, labels, initialRegExs);

		replacePunct(initialRegExs);
		
		replaceDigits(initialRegExs);
		
		replaceWhiteSpaces(initialRegExs);
		
		return initialRegExs;
	}
		
	private void removeLeastFrequent(Collection<RegEx> initialPositiveRegExs, List<Snippet> negSnippets, List<String> posLabels, Map<String,Integer> wordFreqMapPos) {
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
		leastfreqWordRemovalLevel = size;
		for (int i=0; i < leastfreqWordRemovalLevel; i++) {
			Entry<String, Integer> leastFreqEntry = sortedEntryList.get(i);
			for (RegEx regEx : initialPositiveRegExs) {
				String regExStr = regEx.getRegEx();
				String replacedString = null;
				if (leastFreqEntry.getKey().length() < 10) {
					String replaceRegex = "(?i)}"+leastFreqEntry.getKey()+"\\\\";
					String withString = "}[a-zA-Z]{1,0"+leastFreqEntry.getKey().length()+"}\\\\";
					replacedString = regExStr.replaceAll(replaceRegex, withString);
					       replaceRegex = "(?i)}"+leastFreqEntry.getKey()+"\\[";
					withString = "}[a-zA-Z]{1,0"+leastFreqEntry.getKey().length()+"}[";
					replacedString = replacedString.replaceAll(replaceRegex, withString);
					int start = 0;
					while (start < replacedString.length() && replacedString.charAt(start) != '\\' && replacedString.charAt(start) != '[') {
						start++;
					}
					if (leastFreqEntry.getKey().equalsIgnoreCase(replacedString.substring(0, start))) {
						replacedString = "[a-zA-Z]{1,0"+leastFreqEntry.getKey().length()+"}"+replacedString.substring(start, replacedString.length());
					}
					int end = replacedString.length();
					end--;
					while ( end > 0 && replacedString.charAt(end) != '}' && replacedString.charAt(end) != '+') {
						end--;
					}
					if (leastFreqEntry.getKey().equalsIgnoreCase(replacedString.substring(end+1, replacedString.length()))) {
						replacedString = replacedString.substring(0, end+1)+"[a-zA-Z]{1,0"+leastFreqEntry.getKey().length()+"}";
					}
				} else {
					replacedString = regExStr.replaceAll("(?i)}"+leastFreqEntry.getKey()+"\\\\", "}[a-zA-Z]{1,"+leastFreqEntry.getKey().length()+"}\\\\");
					replacedString = replacedString.replaceAll("(?i)}"+leastFreqEntry.getKey()+"\\[", "}[a-zA-Z]{1,"+leastFreqEntry.getKey().length()+"}[");
					int start = 0;
					while (start < replacedString.length() && replacedString.charAt(start) != '\\' && replacedString.charAt(start) != '[') {
						start++;
					}
					if (leastFreqEntry.getKey().equalsIgnoreCase(replacedString.substring(0, start))) {
						replacedString = "[a-zA-Z]{1,"+leastFreqEntry.getKey().length()+"}"+replacedString.substring(start, replacedString.length());
					}
					int end = replacedString.length();
					end--;
					while ( end > 0 && replacedString.charAt(end) != '}' && replacedString.charAt(end) != '+') {
						end--;
					}
					if (leastFreqEntry.getKey().equalsIgnoreCase(replacedString.substring(end+1, replacedString.length()))) {
						replacedString = replacedString.substring(0, end+1)+"[a-zA-Z]{1,"+leastFreqEntry.getKey().length()+"}";
					}
				}
				RegEx temp = new RegEx(replacedString);
				boolean fps = anyMatches(temp, negSnippets, posLabels);
				if (!fps) {
					regEx.setRegEx(replacedString);
					collapse(regEx, negSnippets, posLabels);
					collapserPunct(regEx, negSnippets, posLabels);
					if (PERFORM_TRIMMING) {
						performTrimmingIndividual(regEx, negSnippets, posLabels);
					}
					if (PERFORM_USELESS_REMOVAL) {
						uselessRegRemover(regEx, negSnippets, posLabels);
					}
				}
			}
		}
	}
	
	/**
	 * @param temp
	 * @return
	 */
	private boolean anyMatches(RegEx regex, Collection<Snippet> snippets, Collection<String> excludeLabels) {
		Pattern pattern = patternCache.get(regex);
		if (pattern == null) {
			pattern = Pattern.compile(regex.getRegEx(), Pattern.CASE_INSENSITIVE);
			patternCache.put(regex, pattern);
		}
		for (Snippet snippet : snippets) {
			if (snippet.getLabeledSegments() != null) {
				for (LabeledSegment ls : snippet.getLabeledSegments()) {
					if (!excludeLabels.contains(ls.getLabel())) {
						String labeledString = ls.getLabeledString();
						if (labeledString != null && !labeledString.equals("")) {
							Matcher m = pattern.matcher(labeledString);
							if (m.find()) {
								return true;
							}
						}
					}
				}
			}
			Matcher m = pattern.matcher(snippet.getText());
			if (m.find()) {
				return true;
			}
		}
		return false;
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
	
	private void performTrimming(Collection<RegEx> initialPositiveRegExs, List<Snippet> negSnippets, List<String> posLabels) {
		for (RegEx regEx : initialPositiveRegExs) {
			boolean frontTrim = true;
			boolean backTrim = true;
			while (true) {
				if (frontTrim) {
					RegEx frontTrimRegEx = frontTrim(regEx);
					if (frontTrimRegEx == null) {
						frontTrim = false;
					} else {
						if (anyMatches(frontTrimRegEx, negSnippets, posLabels)) {
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
						if (anyMatches(backTrimRegEx, negSnippets, posLabels)) {
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
		}
	}
	
	private void performTrimmingIndividual(RegEx regEx, List<Snippet> negSnippets, List<String> posLabels) {
		boolean frontTrim = true;
		boolean backTrim = true;
		if (frontTrim) {
			RegEx frontTrimRegEx = frontTrim(regEx);
			if (frontTrimRegEx == null) {
				frontTrim = false;
			} else {
				if (anyMatches(frontTrimRegEx, negSnippets, posLabels)) {
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
				if (anyMatches(backTrimRegEx, negSnippets, posLabels)) {
					backTrim = false;
				} else {
					regEx.setRegEx(backTrimRegEx.getRegEx());
				}
			}
		}
	}
	
	private RegEx frontTrim(RegEx regEx) {
		String regExStr = regEx.getRegEx();
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
		return new RegEx(regExStr.substring(cutLocation));
	}
	
	private RegEx backTrim(RegEx regEx) {
		String regExStr = regEx.getRegEx();
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
		return new RegEx(regExStr.substring(0,cutLocation));
	}
	
	private void initializeInitialRegEx(final List<Snippet> snippets,
			List<String> labels, List<RegEx> initialRegExs) {
		for (Snippet snippet : snippets) {
			for (String label : labels) {
				LabeledSegment labeledSegment = snippet.getLabeledSegment(label);
				if ( labeledSegment != null ) {
					String regExString = labeledSegment.getLabeledString();
					if (regExString != null && !regExString.isEmpty()) {
						initialRegExs.add(new RegEx(regExString));
					}
				}
			}
		}
	}
	
	public CVScore testClassifier(List<Snippet> testing, Collection<RegEx> regularExpressions, Collection<RegEx> negativeRegularExpressions, CategorizerTester tester, List<String> labels) throws IOException {
		CVScore score = new CVScore();
		if(tester == null)
			tester = new CategorizerTester();
		for(Snippet testSnippet : testing){
			boolean actual = false;
			for(String label : labels){
				LabeledSegment actualSegment = testSnippet.getLabeledSegment(label);
				if(actualSegment != null) {
					actual = true;
					break;
				}
			}
			boolean predicted = tester.test(regularExpressions, negativeRegularExpressions, testSnippet, actual);
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
			x.setRegEx(x.getRegEx().replaceAll("\\p{Punct}","\\\\p{Punct}"));
		}
		return regexColl;
	}
}
