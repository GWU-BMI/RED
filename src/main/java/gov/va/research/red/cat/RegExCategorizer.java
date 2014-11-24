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
	
	private List<Snippet> snippetsYes;
	private List<Snippet> snippetsNo;
	private List<Snippet> snippetsNoLabel;
	private List<RegEx> initialPositiveRegExs;
	private List<RegEx> initialNegativeRegExs;
	private Map<RegEx, Pattern> patternCache = new HashMap<RegEx, Pattern>();
	private Map<String, Integer> wordFreqMapPos = new HashMap<String, Integer>();
	private Map<String, Integer> wordFreqMapNeg = new HashMap<String, Integer>();
	private int mostfreqWordRemovalLevel = 10;
	private int leastfreqWordRemovalLevel = 10;
	private List<String> yesLabels;
	private List<String> noLabels;
	private Map<RegEx, List<LabeledSegment>> reg2Ls = new HashMap<RegEx, List<LabeledSegment>>();
	private Map<LabeledSegment, List<RegEx>> lS2Reg = new HashMap<LabeledSegment, List<RegEx>>();
	private Map<RegEx, List<LabeledSegment>> reg2LsNeg = new HashMap<RegEx, List<LabeledSegment>>();
	private Map<LabeledSegment, List<RegEx>> lS2RegNeg = new HashMap<LabeledSegment, List<RegEx>>();
	private String POSITIVE = "POSITIVE";
	private String NEGATIVE = "NEGATIVE";
	private boolean PERFORM_TRIMMING = true;
	private boolean PERFORM_TRIMMING_OVERALL = true;
	private boolean PERFORM_USELESS_REMOVAL_OVERALL = true;
	private boolean PERFORM_USELESS_REMOVAL = true;
	private boolean FILE_TRANSFER = true;
	private boolean NOT_KEYWORD_TRAINING = true;
	
	public Map<String, List<RegEx>> findRegexesAndSaveInFile (
			final File vttFile, List<String> yesLabelsParam, List<String> noLabelsParam,
			final String classifierOutputFileName, boolean printScore) throws IOException {
		VTTReader vttr = new VTTReader();
		yesLabels = yesLabelsParam;
		noLabels = noLabelsParam;
		snippetsYes = new ArrayList<>();
		for (String yesLabel : yesLabelsParam) {
			snippetsYes.addAll(vttr.extractSnippets(vttFile, yesLabel));
		}
		snippetsNo = new ArrayList<>();
		for (String noLabel : noLabelsParam) {
			snippetsNo.addAll(vttr.extractSnippets(vttFile, noLabel));
		}
		snippetsNoLabel = new ArrayList<Snippet>();
		snippetsNoLabel.addAll(vttr.extractSnippets(vttFile));
		Map<String, List<RegEx>> rtMap =  extractRegexClassifications(snippetsYes, snippetsNo, snippetsNoLabel, yesLabels, noLabels);
		if (FILE_TRANSFER) {
			posSnippetsFile();
			negSnippetsFile();
			freqPrinterPos();
			freqPrinterNeg();
		}
		return rtMap;
	}
	
	public Map<String, List<RegEx>> extractRegexClassifications(List<Snippet> snippetsYesM, List<Snippet> snippetsNoM, List<Snippet> snippetsNoLabelM, List<String> yesLabelsM, List<String> noLabelsM) throws IOException {
		snippetsYes = snippetsYesM;
		snippetsNo = snippetsNoM;
		snippetsNoLabel = snippetsNoLabelM;
		yesLabels = yesLabelsM;
		noLabels = noLabelsM;
		if (snippetsYes == null || snippetsNo == null)
			return null;
		List<Snippet> snippetsAll = new ArrayList<Snippet>();
		snippetsAll.addAll(snippetsYes);
		snippetsAll.addAll(snippetsNo);
		snippetsAll.addAll(snippetsNoLabel);
		//System.out.println(snippetsAll.size());
		CVScore cvScore = null;
		
		initialPositiveRegExs = initialize(true);
		initialNegativeRegExs = initialize(false);
		removeDuplicates(true);
		removeDuplicates(false);

		Iterator<RegEx> it = initialPositiveRegExs.iterator();
		while (it.hasNext()) {
			RegEx reg = it.next();
			int negscore = calculateNegativeScore(reg, true, false);
			negscore += calculateNoLabelScore(reg);
			if (negscore > 0) {
				it.remove();
			}
		}
		
		it = initialNegativeRegExs.iterator();
		while (it.hasNext()) {
			RegEx reg = it.next();
			int posscore = calculatePositiveScore(reg, false, false);
			posscore += calculateNoLabelScore(reg);
			if (posscore > 0) {
				it.remove();
			}
		}
		removeDuplicates(true);
		removeDuplicates(false);
		
		
		//System.out.println("\nREPLACEMENT and COLLAPSSING\n");
		createFrequencyMapPos();
		createFrequencyMapNeg();
		removeLeastFrequentPos();
		removeLeastFrequentNeg();
		removeDuplicates(true);
		removeDuplicates(false);
		
		
		if (PERFORM_TRIMMING_OVERALL) {
			//System.out.println("\nTRIMMING\n");
			performTrimming(true);
			performTrimming(false);
			removeDuplicates(true);
			removeDuplicates(false);
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
			//System.out.println("\nUSELESS REMOVER\n");
			for (RegEx regEx : initialPositiveRegExs) {
				uselessRegRemover(regEx, true);
			}
			for (RegEx regEx : initialNegativeRegExs) {
				uselessRegRemover(regEx, false);
			}
		}
		
		for (RegEx regEx : initialPositiveRegExs) {
			overallCollapser(regEx, true);
		}
		for (RegEx regEx : initialNegativeRegExs) {
			overallCollapser(regEx, false);
		}
		removeDuplicates(true);
		removeDuplicates(false);
		
		
		for (RegEx regEx : initialPositiveRegExs) {
			int specifity = 0;
			for (Snippet snippet : snippetsYes) {
				Pattern p = patternCache.get(regEx);
				if (p == null) {
					p = Pattern.compile("(?i)"+regEx.getRegEx());
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
					p = Pattern.compile("(?i)"+regEx.getRegEx());
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
		Map<String, List<RegEx>> positiveAndNegativeRegEx = new HashMap<String, List<RegEx>>();
		positiveAndNegativeRegEx.put(POSITIVE, initialPositiveRegExs);
		positiveAndNegativeRegEx.put(NEGATIVE, initialNegativeRegExs);
		
		return positiveAndNegativeRegEx;
	}
	
	private void posSnippetsFile() throws IOException {
		File file = new File("Positive_Snippets");
		File fileLS = new File("Positive_Labeled_Segments");
		if (!file.exists()) {
			file.createNewFile();
		}
		if (!fileLS.exists()) {
			fileLS.createNewFile();
		}
		FileWriter snipWriter = new FileWriter(file);
		FileWriter lsWriter = new FileWriter(fileLS);
		for (Snippet snippet : snippetsYes) {
			snipWriter.write("SNIPPET\n\n");
			snipWriter.write(snippet.getText());
			snipWriter.write("\n\n\n");
			for (String label : yesLabels) {
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
	
	private void freqPrinterPos() throws IOException {
		File file = new File("Positive_Labeled_Segment_freq");
		if (!file.exists()) {
			file.createNewFile();
		}
		FileWriter writer = new FileWriter(file);
		
		Set<Entry<String, Integer>> entries = wordFreqMapPos.entrySet();
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
	
	private void freqPrinterNeg() throws IOException {
		File file = new File("Negative_Labeled_Segment_freq");
		if (!file.exists()) {
			file.createNewFile();
		}
		FileWriter writer = new FileWriter(file);
		
		Set<Entry<String, Integer>> entries = wordFreqMapNeg.entrySet();
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
	
	private void negSnippetsFile() throws IOException {
		File file = new File("Negative_Snippets");
		File fileLS = new File("Negative_Labeled_Segments");
		if (!file.exists()) {
			file.createNewFile();
		}
		if (!fileLS.exists()) {
			fileLS.createNewFile();
		}
		FileWriter snipWriter = new FileWriter(file);
		FileWriter lsWriter = new FileWriter(fileLS);
		for (Snippet snippet : snippetsNo) {
			snipWriter.write("SNIPPET\n\n");
			snipWriter.write(snippet.getText());
			snipWriter.write("\n\n\n");
			for (String label : noLabels) {
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
	
	private void removeDuplicates(boolean positive) {
		Set<RegEx> setToRemoveDupl = new HashSet<RegEx>();
		if (positive) {
			setToRemoveDupl.addAll(initialPositiveRegExs);
			initialPositiveRegExs = new ArrayList<RegEx>(setToRemoveDupl);
		} else {
			setToRemoveDupl.addAll(initialNegativeRegExs);
			initialNegativeRegExs = new ArrayList<RegEx>(setToRemoveDupl);
		}
	}
	
	private void uselessRegRemover(RegEx regEx, boolean positive){		
		String old = regEx.getRegEx();
		String regExStr = regEx.getRegEx();
		StringBuilder regExStrBld = new StringBuilder(regExStr);
		String backup = null;
		int start = 0,end =0;
		while (true) {
			backup = regExStrBld.toString();
			int posScore = calculatePositiveScore(regEx, positive, false);
			int negScore = calculateNegativeScore(regEx, positive, false);
			int noLabelScore = calculateNoLabelScore(regEx);
			if (positive) {
				negScore += noLabelScore;
				//regEx.setSpecifity(posScore);
			} else {
				posScore += noLabelScore;
				//regEx.setSpecifity(negScore);
			}
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
			//if (regExStrBld.charAt(start) == '\\' || regExStrBld.charAt(start) == '[') {
				regExStrBld.replace(start, end, "");
				regEx.setRegEx(regExStrBld.toString());
				int tempPosScore = calculatePositiveScore(regEx, positive, false);
				int tempNegScore = calculateNegativeScore(regEx, positive, false);
				int tempnoLabelScore = calculateNoLabelScore(regEx);
				if (positive) {
					tempNegScore += tempnoLabelScore;
					//regEx.setSpecifity(posScore);
				} else {
					tempPosScore += tempnoLabelScore;
					//regEx.setSpecifity(negScore);
				}
				if (positive) {
					if (tempPosScore >= posScore && tempNegScore <= negScore) {
						end = start;
					} else {
						start = end;
						regEx.setRegEx(backup);
						regExStrBld = new StringBuilder(backup);
					}
				} else {
					if (tempPosScore <= posScore && tempNegScore >= negScore) {
						end = start;
					} else {
						start = end;
						regEx.setRegEx(backup);
						regExStrBld = new StringBuilder(backup);
					}
				}
			/*} else {
				start = end;
			}*/
		}
		/*String regStr = regEx.getRegEx(); 
		if (!regStr.equals(old) && regStr.contains("trochanteric")) {
			System.out.println(regStr);
		}*/
	}
	
	private void overallCollapser(RegEx regEx, boolean positive) {
		//int posScore = calculatePositiveScore(regEx, positive, false);
		//int negScore = calculateNegativeScore(regEx, positive, false);
		String regStr = regEx.getRegEx();
		int initialStart=0,initalEnd = 0;
		int splitter = 0;
		while (regStr.substring(splitter, regStr.length()).contains("[A-Za-z ]")) {
			int startIndex = regStr.substring(splitter, regStr.length()).indexOf("[A-Za-z ]");
			startIndex = startIndex + splitter;
			int start = startIndex;
			int tempEnd,tempStart,sum=0;
			tempStart = startIndex+12;
			tempEnd = tempStart + 2;
			sum += Integer.parseInt(regStr.substring(tempStart, tempEnd));
			int n=0;
			boolean found = false;
			int end = startIndex+15;
			splitter = end;
			initalEnd = end;
			while (end <= regEx.getRegEx().length()-15) {
				boolean breakCond = true;
				if (regStr.substring(end, end+9).equals("[A-Za-z ]")) {
					end = end + 15;
					tempStart = end - 3;
					tempEnd = tempStart + 2;
					sum += Integer.parseInt(regStr.substring(tempStart, tempEnd));
					breakCond = false;
					found = true;
					n++;
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
				/*int tempPosScore = calculatePositiveScore(regEx, positive, false);
				int tempNegScore = calculateNegativeScore(regEx, positive, false);
				if (positive) {
					if (tempPosScore >= posScore && tempNegScore <= negScore) {
						regStr = regStrBld.toString();
						splitter = 0;
					}
				} else {
					if (tempPosScore <= posScore && tempNegScore >= negScore) {
						regStr = regStrBld.toString();
						splitter = 0;
					}
				}*/
			}
		}
	}
	
	private void collapserPunct(RegEx regEx, boolean positive) {
		int posScore = calculatePositiveScore(regEx, positive, false);
		int negScore = calculateNegativeScore(regEx, positive, false);
		int noLabelScore = calculateNoLabelScore(regEx);
		if (positive) {
			negScore += noLabelScore;
			//regEx.setSpecifity(posScore);
		} else {
			posScore += noLabelScore;
			//regEx.setSpecifity(negScore);
		}
		String regStr = regEx.getRegEx();
		int initialStart=0,initalEnd = 0;
		int splitter = 0;
		while (regStr.substring(splitter, regStr.length()).contains("[A-Za-z ]")) {
			int startIndex = regStr.substring(splitter, regStr.length()).indexOf("[A-Za-z ]");
			startIndex = startIndex + splitter;
			int tempEnd,tempStart,sum=0;
			tempStart = startIndex+12;
			tempEnd = tempStart + 2;
			sum += Integer.parseInt(regStr.substring(tempStart, tempEnd));
			int n=0;
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
			initalEnd = end;
			while (end <= regEx.getRegEx().length()) {
				boolean breakCond = true;
				if ((end+8) <= regEx.getRegEx().length() && regStr.substring(end, end+8).equals("\\s{1,10}")) {
					end = end + 8;
					sum += 8;
					breakCond = false;
					n++;
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
				int tempPosScore = calculatePositiveScore(regEx, positive, false);
				int tempNegScore = calculateNegativeScore(regEx, positive, false);
				int tempnoLabelScore = calculateNoLabelScore(regEx);
				if (positive) {
					tempNegScore += tempnoLabelScore;
					//regEx.setSpecifity(posScore);
				} else {
					tempPosScore += tempnoLabelScore;
					//regEx.setSpecifity(negScore);
				}
				if (positive) {
					if (tempPosScore >= posScore && tempNegScore <= negScore) {
						regStr = regStrBld.toString();
						splitter = start + 24;
					} else {
						regEx.setRegEx(regStr);
						splitter = startIndex + 15;
					}
				} else {
					if (tempPosScore <= posScore && tempNegScore >= negScore) {
						regStr = regStrBld.toString();
						splitter = start + 24;
					} else {
						regEx.setRegEx(regStr);
						splitter = startIndex + 15;
					}
				}
			}
		}
		
		
		posScore = calculatePositiveScore(regEx, positive, false);
		negScore = calculateNegativeScore(regEx, positive, false);
		noLabelScore = calculateNoLabelScore(regEx);
		if (positive) {
			negScore += noLabelScore;
			//regEx.setSpecifity(posScore);
		} else {
			posScore += noLabelScore;
			//regEx.setSpecifity(negScore);
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
			int n=0;
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
			initalEnd = end;
			while (end <= regEx.getRegEx().length()) {
				boolean breakCond = true;
				if ((end+8) <= regEx.getRegEx().length() && regStr.substring(end, end+8).equals("\\s{1,10}")) {
					end = end + 8;
					sum += 8;
					breakCond = false;
					foundSpace = true;
					n++;
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
				int tempPosScore = calculatePositiveScore(regEx, positive, false);
				int tempNegScore = calculateNegativeScore(regEx, positive, false);
				int tempnoLabelScore = calculateNoLabelScore(regEx);
				if (positive) {
					tempNegScore += tempnoLabelScore;
					//regEx.setSpecifity(posScore);
				} else {
					tempPosScore += tempnoLabelScore;
					//regEx.setSpecifity(negScore);
				}
				if (positive) {
					if (tempPosScore >= posScore && tempNegScore <= negScore) {
						regStr = regStrBld.toString();
						splitter = start + 24;
					} else {
						regEx.setRegEx(regStr);
						splitter = startIndex + 14;
					}
				} else {
					if (tempPosScore <= posScore && tempNegScore >= negScore) {
						regStr = regStrBld.toString();
						splitter = start + 24;
					} else {
						regEx.setRegEx(regStr);
						splitter = startIndex + 14;
					}
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
				int tempPosScore = calculatePositiveScore(regEx, positive, false);
				int tempNegScore = calculateNegativeScore(regEx, positive, false);
				int tempnoLabelScore = calculateNoLabelScore(regEx);
				if (positive) {
					tempNegScore += tempnoLabelScore;
					//regEx.setSpecifity(posScore);
				} else {
					tempPosScore += tempnoLabelScore;
					//regEx.setSpecifity(negScore);
				}
				if (positive) {
					if (tempPosScore >= posScore && tempNegScore <= negScore) {
						regStr = regStrBld.toString();
						splitter = start + 23;
					} else {
						regEx.setRegEx(regStr);
						splitter = startIndex + 14;
					}
				} else {
					if (tempPosScore <= posScore && tempNegScore >= negScore) {
						regStr = regStrBld.toString();
						splitter = start + 23;
					} else {
						regEx.setRegEx(regStr);
						splitter = startIndex + 14;
					}
				}
			}
		}
	}
	
	private void collapse(RegEx regEx, boolean positive) {
		int posScore = calculatePositiveScore(regEx, positive, false);
		int negScore = calculateNegativeScore(regEx, positive, false);
		int noLabelScore = calculateNoLabelScore(regEx);
		if (positive) {
			negScore += noLabelScore;
			//regEx.setSpecifity(posScore);
		} else {
			posScore += noLabelScore;
			//regEx.setSpecifity(negScore);
		}
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
			int n = 1;
			while (start > 7) {
				boolean breakCond = true;
				if (regStr.substring(start-8, start).equals("\\s{1,10}")) {
					start = start - 8;
					breakCond = false;
					found = true;
					n++;
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
						n++;
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
					n++;
				}
				if (end <=  regEx.getRegEx().length()-14) {
					if (regStr.substring(end, end+8).equals("[a-zA-Z]")) {
						end = end + 14;
						tempStart = end-3;
						tempEnd = tempStart + 2;
						totalSum += Integer.parseInt(regStr.substring(tempStart, tempEnd));
						breakCond = false;
						found = true;
						n++;
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
				overallCollapser(regEx, positive);
				//System.out.println(regEx.getRegEx());
				int tempPosScore = calculatePositiveScore(regEx, positive, false);
				int tempNegScore = calculateNegativeScore(regEx, positive, false);
				int tempnoLabelScore = calculateNoLabelScore(regEx);
				if (positive) {
					tempNegScore += tempnoLabelScore;
					//regEx.setSpecifity(posScore);
				} else {
					tempPosScore += tempnoLabelScore;
					//regEx.setSpecifity(negScore);
				}
				if (positive) {
					if (tempPosScore >= posScore && tempNegScore <= negScore) {
						regStr = regStrBld.toString();
						/*if (regStr.contains("trochanteric")) {
							System.out.println(regStr);
						}*/
					} else {
						regStrBld = new StringBuilder(regStr);
						regStrBld.replace(initialStart, initalEnd-6, "[A-Za-z]");
						regEx.setRegEx(regStrBld.toString());
						regStr = regStrBld.toString();
					}
				} else {
					if (tempPosScore <= posScore && tempNegScore >= negScore) {
						regStr = regStrBld.toString();
					} else {
						regStrBld = new StringBuilder(regStr);
						regStrBld.replace(initialStart, initalEnd-6, "[A-Za-z]");
						regEx.setRegEx(regStrBld.toString());
						regStr = regStrBld.toString();
					}
				}
			} else {
				StringBuilder regStrBld = new StringBuilder(regStr);
				regStrBld.replace(start, end-6, "[A-Za-z]");
				regEx.setRegEx(regStrBld.toString());
				regStr = regStrBld.toString();
				/*if (regStr.contains("trochanteric")) {
					System.out.println(regStr);
				}*/
			}
		}
	}
	
	private List<RegEx> initialize(boolean positive) {
		List<RegEx> initialRegExs = null;
		if (positive) {
			initialRegExs = new ArrayList<RegEx>(snippetsYes.size());
			initializeInitialRegEx(snippetsYes, yesLabels, initialRegExs);
		} else {
			initialRegExs = new ArrayList<RegEx>(snippetsNo.size());
			initializeInitialRegEx(snippetsNo, noLabels, initialRegExs);
		}

		replacePunct(initialRegExs);
		
		replaceDigits(initialRegExs);
		
		replaceWhiteSpaces(initialRegExs);
		
		return initialRegExs;
	}
		
	private void removeLeastFrequentPos(){
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
		//if (leastfreqWordRemovalLevel > (size)) {
			leastfreqWordRemovalLevel = size;
		//}
		for (int i=0;i< leastfreqWordRemovalLevel;i++) {
			Entry<String, Integer> leastFreqEntry = sortedEntryList.get(i);
			//if (NOT_KEYWORD_TRAINING || (!leastFreqEntry.getKey().equalsIgnoreCase("diabetes") && !leastFreqEntry.getKey().equalsIgnoreCase("history") && !leastFreqEntry.getKey().equalsIgnoreCase("mellitus") && !leastFreqEntry.getKey().equalsIgnoreCase("family") && !leastFreqEntry.getKey().equalsIgnoreCase("past"))) {
			/*if (leastFreqEntry.getKey().length() == 1) {
				continue;
			}*/
				for (RegEx regEx : initialPositiveRegExs) {
					int posScore = calculatePositiveScore(regEx, true, false);
					int negScore = calculateNegativeScore(regEx, true, false);
					int noLabelScore = calculateNoLabelScore(regEx);
					negScore += noLabelScore;
					
					String regExStr = regEx.getRegEx();
					if (leastFreqEntry.getKey().equalsIgnoreCase("As") && regExStr.contains("her") && regExStr.contains("As")) {
						int a = 1;
						a++;
					} /*else {
						continue;
					}*/
					String replacedString = null;
					if (leastFreqEntry.getKey().length() < 10) {
						replacedString = regExStr.replaceAll("(?i)}"+leastFreqEntry.getKey()+"\\\\", "}[a-zA-Z]{1,0"+leastFreqEntry.getKey().length()+"}\\\\");
						replacedString = replacedString.replaceAll("(?i)}"+leastFreqEntry.getKey()+"\\[", "}[a-zA-Z]{1,0"+leastFreqEntry.getKey().length()+"}[");
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
					int tempPosScore = calculatePositiveScore(temp, true, false);
					int tempNegScore = calculateNegativeScore(temp, true, false);
					int tempnoLabelScore = calculateNoLabelScore(regEx);
					tempNegScore += tempnoLabelScore;
					if (tempPosScore >= posScore && tempNegScore <= negScore) {
						regEx.setRegEx(replacedString);
						/*if (replacedString.contains("trochanteric")) {
							System.out.println(replacedString);
						}*/
						collapse(regEx, true);
						collapserPunct(regEx,true);
						//overallCollapser(regEx, true);
						if (PERFORM_TRIMMING) {
							performTrimmingIndividual(regEx, true);
						}
						if (PERFORM_USELESS_REMOVAL) {
							uselessRegRemover(regEx, true);
						}
					}
				}
			//}
		}
	}
	
	private void removeLeastFrequentNeg(){
		Set<Entry<String, Integer>> entries = wordFreqMapNeg.entrySet();
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
		//if (leastfreqWordRemovalLevel > (size)) {
			leastfreqWordRemovalLevel = size;
		//}
		for (int i=0;i< leastfreqWordRemovalLevel;i++) {
			Entry<String, Integer> leastFreqEntry = sortedEntryList.get(i);
			/*if (leastFreqEntry.getKey().length() == 1) {
				continue;
			}*/
			//if (NOT_KEYWORD_TRAINING || (!leastFreqEntry.getKey().equalsIgnoreCase("diabetes") && !leastFreqEntry.getKey().equalsIgnoreCase("history") && !leastFreqEntry.getKey().equalsIgnoreCase("mellitus") && !leastFreqEntry.getKey().equalsIgnoreCase("family") && !leastFreqEntry.getKey().equalsIgnoreCase("past"))) {
				for (RegEx regEx : initialNegativeRegExs) {
					int posScore = calculatePositiveScore(regEx, false, false);
					int negScore = calculateNegativeScore(regEx, false, false);
					int noLabelScore = calculateNoLabelScore(regEx);
					posScore += noLabelScore;
					String regExStr = regEx.getRegEx();
					String replacedString = null;
					if (leastFreqEntry.getKey().length() < 10) {
						replacedString = regExStr.replaceAll("(?i)}"+leastFreqEntry.getKey()+"\\\\", "}[a-zA-Z]{1,0"+leastFreqEntry.getKey().length()+"}\\\\");
						replacedString = replacedString.replaceAll("(?i)}"+leastFreqEntry.getKey()+"\\[", "}[a-zA-Z]{1,0"+leastFreqEntry.getKey().length()+"}[");
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
					int tempPosScore = calculatePositiveScore(temp, false, false);
					int tempNegScore = calculateNegativeScore(temp, false, false);
					int tempnoLabelScore = calculateNoLabelScore(regEx);
					tempPosScore += tempnoLabelScore;
					if (tempPosScore <= posScore && tempNegScore >= negScore) {
						regEx.setRegEx(replacedString);
						collapse(regEx, false);
						collapserPunct(regEx,false);
						//overallCollapser(regEx, false);
						if (PERFORM_TRIMMING) {
							performTrimmingIndividual(regEx, false);
						}
						if (PERFORM_USELESS_REMOVAL) {
							uselessRegRemover(regEx, false);
						}
					}
				}
			//}
		}
	}
	
	private void createFrequencyMapPos(){
		for (RegEx regEx : initialPositiveRegExs) {
			String[] regExStrArray = regEx.getRegEx().split("\\\\s\\{1,10\\}|\\\\p\\{Punct\\}|\\\\d\\{1,50\\}");
			for (String word : regExStrArray) {
				String lowerCase = word.toLowerCase();
				if (!lowerCase.equals("")) {
					if (wordFreqMapPos.containsKey(lowerCase)) {
						int count = wordFreqMapPos.get(lowerCase);
						wordFreqMapPos.put(lowerCase, ++count);
					} else {
						wordFreqMapPos.put(lowerCase, 1);
					}
				}
			}
		}
	}
	
	private void createFrequencyMapNeg(){
		for (RegEx regEx : initialNegativeRegExs) {
			String[] regExStrArray = regEx.getRegEx().split("\\\\s\\{1,10\\}|\\\\p\\{Punct\\}|\\\\d\\{1,50\\}");
			for (String word : regExStrArray) {
				String lowerCase = word.toLowerCase();
				if (!lowerCase.equals("")) {
					if (wordFreqMapNeg.containsKey(lowerCase)) {
						int count = wordFreqMapNeg.get(lowerCase);
						wordFreqMapNeg.put(lowerCase, ++count);
					} else {
						wordFreqMapNeg.put(lowerCase, 1);
					}
				}
			}
		}
	}
	
	private List<RegEx> convertSnippetToRegEx(List<Snippet> snippets) {
		List<RegEx> regExList = new ArrayList<RegEx>();
		for (Snippet snippet : snippets) {
			regExList.add(new RegEx(snippet.getText()));
		}

		replacePunct(regExList);
		
		replaceDigits(regExList);
		
		replaceWhiteSpaces(regExList);
		
		return regExList;
	}
	
	
	/**
	 * remove this method
	 * @param snippets
	 */
	private Map<String, Integer> createFrequencyMapList(List<RegEx> regExList){
		Map<String, Integer> freqMap = new HashMap<String, Integer>();
		for (RegEx regEx : regExList) {
			String[] regExStrArray = regEx.getRegEx().split("\\\\s\\{1,10\\}|\\\\p\\{Punct\\}|\\\\d\\+");
			for (String word : regExStrArray) {
				String lowerCase = word.toLowerCase();
				if (!lowerCase.equals("")) {
					if (freqMap.containsKey(lowerCase)) {
						int count = freqMap.get(lowerCase);
						freqMap.put(lowerCase, ++count);
					} else {
						freqMap.put(lowerCase, 1);
					}
				}
			}
		}
		return freqMap;
	}
	
	private void performTrimming(boolean positiveTrim) {
		List<RegEx> regExToTrim = null;
		if (positiveTrim) {
			regExToTrim = initialPositiveRegExs;
		} else {
			regExToTrim = initialNegativeRegExs;
		}
		for (RegEx regEx : regExToTrim) {
			boolean frontTrim = true;
			boolean backTrim = true;
			while (true) {
				int posScore = calculatePositiveScore(regEx, positiveTrim, false);
				int negScore = calculateNegativeScore(regEx, positiveTrim, false);
				int noLabelScore = calculateNoLabelScore(regEx);
				if (positiveTrim) {
					negScore += noLabelScore;
					//regEx.setSpecifity(posScore);
				} else {
					posScore += noLabelScore;
					//regEx.setSpecifity(negScore);
				}
				if (frontTrim) {
					RegEx frontTrimRegEx = frontTrim(regEx);
					if (frontTrimRegEx == null) {
						frontTrim = false;
					} else {
						int trimPosScore = calculatePositiveScore(frontTrimRegEx, positiveTrim, false);
						int trimNegScore = calculateNegativeScore(frontTrimRegEx, positiveTrim, false);
						int trimNoLabelScore = calculateNoLabelScore(frontTrimRegEx);
						if (positiveTrim) {
							trimNegScore += trimNoLabelScore;
							if (trimPosScore < posScore || trimNegScore > negScore) {
								frontTrim = false;
							} else {
								regEx.setRegEx(frontTrimRegEx.getRegEx());
								/*String regStr = regEx.getRegEx(); 
								if (regStr.contains("trochanteric")) {
									System.out.println(regStr);
								}*/
								//regEx.setSpecifity(trimPosScore);
							}
						} else {
							trimPosScore += trimNoLabelScore;
							if (trimPosScore > posScore || trimNegScore < negScore) {
								frontTrim = false;
							} else {
								regEx.setRegEx(frontTrimRegEx.getRegEx());
								//regEx.setSpecifity(trimNegScore);
							}
						}
					}
				}
				if (backTrim) {
					RegEx backTrimRegEx = backTrim(regEx);
					if (backTrimRegEx == null) {
						backTrim = false;
					} else {
						int trimPosScore = calculatePositiveScore(backTrimRegEx, positiveTrim, false);
						int trimNegScore = calculateNegativeScore(backTrimRegEx, positiveTrim, false);
						int trimNoLabelScore = calculateNoLabelScore(backTrimRegEx);
						if (positiveTrim) {
							trimNegScore += trimNoLabelScore;
							if (trimPosScore < posScore || trimNegScore > negScore) {
								backTrim = false;
							} else {
								regEx.setRegEx(backTrimRegEx.getRegEx());
								/*String regStr = regEx.getRegEx(); 
								if (regStr.contains("trochanteric")) {
									System.out.println(regStr);
								}*/
								//regEx.setSpecifity(trimPosScore);
							}
						} else {
							trimPosScore += trimNoLabelScore;
							if (trimPosScore > posScore || trimNegScore < negScore) {
								backTrim = false;
							} else {
								regEx.setRegEx(backTrimRegEx.getRegEx());
								//regEx.setSpecifity(trimNegScore);
							}
						}
					}
				}
				if (!frontTrim && !backTrim) {
					break;
				}
			}
		}
	}
	
	private void performTrimmingIndividual(RegEx regEx, boolean positiveTrim) {
		boolean frontTrim = true;
		boolean backTrim = true;
		//while (true) {
			int posScore = calculatePositiveScore(regEx, positiveTrim, false);
			int negScore = calculateNegativeScore(regEx, positiveTrim, false);
			int noLabelScore = calculateNoLabelScore(regEx);
			if (positiveTrim) {
				negScore += noLabelScore;
				//regEx.setSpecifity(posScore);
			} else {
				posScore += noLabelScore;
				//regEx.setSpecifity(negScore);
			}
			if (frontTrim) {
				RegEx frontTrimRegEx = frontTrim(regEx);
				if (frontTrimRegEx == null) {
					frontTrim = false;
				} else {
					int trimPosScore = calculatePositiveScore(frontTrimRegEx, positiveTrim, false);
					int trimNegScore = calculateNegativeScore(frontTrimRegEx, positiveTrim, false);
					int trimNoLabelScore = calculateNoLabelScore(frontTrimRegEx);
					if (positiveTrim) {
						trimNegScore += trimNoLabelScore;
						if (trimPosScore < posScore || trimNegScore > negScore) {
							frontTrim = false;
						} else {
							regEx.setRegEx(frontTrimRegEx.getRegEx());
							//regEx.setSpecifity(trimPosScore);
						}
					} else {
						trimPosScore += trimNoLabelScore;
						if (trimPosScore > posScore || trimNegScore < negScore) {
							frontTrim = false;
						} else {
							regEx.setRegEx(frontTrimRegEx.getRegEx());
							//regEx.setSpecifity(trimNegScore);
						}
					}
				}
			}
			if (backTrim) {
				RegEx backTrimRegEx = backTrim(regEx);
				if (backTrimRegEx == null) {
					backTrim = false;
				} else {
					int trimPosScore = calculatePositiveScore(backTrimRegEx, positiveTrim, false);
					int trimNegScore = calculateNegativeScore(backTrimRegEx, positiveTrim, false);
					int trimNoLabelScore = calculateNoLabelScore(backTrimRegEx);
					if (positiveTrim) {
						trimNegScore += trimNoLabelScore;
						if (trimPosScore < posScore || trimNegScore > negScore) {
							backTrim = false;
						} else {
							regEx.setRegEx(backTrimRegEx.getRegEx());
							//regEx.setSpecifity(trimPosScore);
						}
					} else {
						trimPosScore += trimNoLabelScore;
						if (trimPosScore > posScore || trimNegScore < negScore) {
							backTrim = false;
						} else {
							regEx.setRegEx(backTrimRegEx.getRegEx());
							//regEx.setSpecifity(trimNegScore);
						}
					}
				}
			}
			/*if (!frontTrim && !backTrim) {
				break;
			}*/
		//}
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
			/*while(true){
				if (cutLocation == regExStr.length()) {
					return null;
				}
				char currentChar = regExStr.charAt(cutLocation++);
				if (currentChar == '\\') {
					cutLocation = cutLocation - 1;
					break;
				}
			}*/
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
			/*while(true){
				if (cutLocation < 0) {
					return null;
				}
				char currentChar = regExStr.charAt(cutLocation--);
				if (currentChar == '+' || currentChar == '}') {
					cutLocation = cutLocation+2;
					break;
				}
			}*/
			return null;
		}
		return new RegEx(regExStr.substring(0,cutLocation));
	}
	
	private int calculatePositiveScore(RegEx regEx, boolean positiveTrim, boolean mapEntry) {
		Pattern pattern = patternCache.get(regEx);
		int posScore = 0;
		if (pattern == null) {
			pattern = Pattern.compile("(?i)"+regEx.getRegEx());
			patternCache.put(regEx, pattern);
		}
		for (Snippet snippet : snippetsYes) {
			if (positiveTrim) {
				for (String label : yesLabels) {
					if (snippet.getLabeledSegment(label) != null && snippet.getLabeledSegment(label).getLabeledString() != null && !snippet.getLabeledSegment(label).getLabeledString().equals("")) {
						Matcher matcher = pattern.matcher(snippet.getLabeledSegment(label).getLabeledString());
						if (matcher.find()) {
							posScore++;
							if (mapEntry) {
								List<LabeledSegment> listLS = reg2Ls.get(regEx);
								if (listLS == null) {
									listLS = new ArrayList<LabeledSegment>();
									reg2Ls.put(regEx, listLS);
								}
								listLS.add(snippet.getLabeledSegment(label));
								List<RegEx> listRegEx = lS2Reg.get(snippet.getLabeledSegment(label));
								if (listRegEx == null) {
									listRegEx = new ArrayList<RegEx>();
									lS2Reg.put(snippet.getLabeledSegment(label), listRegEx);
								}
								listRegEx.add(regEx);
							}
						}
					}
				}
			} else {
				Matcher matcher = pattern.matcher(snippet.getText());
				if (matcher.find()) {
					posScore++;
				}
			}
		}
		return posScore;
	}
	
	private int calculateNegativeScore(RegEx regEx, boolean positiveTrim, boolean mapEntry) {
		Pattern pattern = patternCache.get(regEx);
		int negScore = 0;
		if (pattern == null) {
			pattern = Pattern.compile("(?i)"+regEx.getRegEx());
			patternCache.put(regEx, pattern);
		}
		for (Snippet snippet : snippetsNo) {
			if (positiveTrim) {
				Matcher matcher = pattern.matcher(snippet.getText());
				if (matcher.find()) {
					negScore++;
				}
			} else {
				for (String label : noLabels) {
					if (snippet.getLabeledSegment(label) != null && snippet.getLabeledSegment(label).getLabeledString() != null && !snippet.getLabeledSegment(label).getLabeledString().equals("")) {
						Matcher matcher = pattern.matcher(snippet.getLabeledSegment(label).getLabeledString());
						if (matcher.find()) {
							negScore++;
							if (mapEntry) {
								List<LabeledSegment> listLS = reg2LsNeg.get(regEx);
								if (listLS == null) {
									listLS = new ArrayList<LabeledSegment>();
									reg2LsNeg.put(regEx, listLS);
								}
								listLS.add(snippet.getLabeledSegment(label));
								List<RegEx> listRegEx = lS2RegNeg.get(snippet.getLabeledSegment(label));
								if (listRegEx == null) {
									listRegEx = new ArrayList<RegEx>();
									lS2RegNeg.put(snippet.getLabeledSegment(label), listRegEx);
								}
								listRegEx.add(regEx);
							}
						}
					}
				}
			}
		}
		return negScore;
	}
	
	private int calculateNoLabelScore(RegEx regEx) {
		Pattern pattern = patternCache.get(regEx);
		int noLabelScore = 0;
		if (pattern == null) {
			pattern = Pattern.compile("(?i)"+regEx.getRegEx());
			patternCache.put(regEx, pattern);
		}
		for (Snippet snippet : snippetsNoLabel) {
			Matcher matcher = pattern.matcher(snippet.getText());
			if (matcher.find()) {
				noLabelScore++;
			}
		}
		return noLabelScore;
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
			/*else if(predicted && !actual && !actual)
				score.setFp(score.getFp() + 1);
			else if(!predicted && !actual && !actual)
				score.setFn(score.getFn() + 1);
			else if(!predicted && !predicted && actual)
				score.setFn(score.getFn() + 1);
			else if(!predicted && !predicted && actual)
				score.setTn(score.getTn() + 1);*/
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
