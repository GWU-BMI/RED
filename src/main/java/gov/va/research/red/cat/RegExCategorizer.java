package gov.va.research.red.cat;

import gov.va.research.red.LabeledSegment;
import gov.va.research.red.RegEx;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * starting off with un-labeled segments
 */
public class RegExCategorizer {
	
	private List<Snippet> snippetsYes;
	private List<Snippet> snippetsNo;
	private List<RegEx> initialPositiveRegExs;
	private Map<RegEx, Pattern> patternCache = new HashMap<RegEx, Pattern>();
	private Map<String, Integer> wordFreqMap = new HashMap<String, Integer>();
	private int freqWordRemovalLevel = 1;
	private List<String> yesLabels;
	private List<String> noLabels;
	
	public void findRegexesAndSaveInFile (
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
		snippetsNo.addAll(vttr.extractSnippets(vttFile));
		extractRegexClassifications();
	}

	public void extractRegexClassifications() {
		if(snippetsYes == null || snippetsYes.isEmpty() || snippetsNo == null || snippetsNo.isEmpty())
			return;
		initialPositiveRegExs = new ArrayList<RegEx>(snippetsYes.size());
		initializeInitialRegEx(snippetsYes, yesLabels, initialPositiveRegExs);
		
		replaceDigits(initialPositiveRegExs);

		replacePunct(initialPositiveRegExs);
		
		replaceWhiteSpaces(initialPositiveRegExs);
		
		performTrimming();
		
		createFrequencyMap();
		
		removeWordsOnFrequency();
		
		for (RegEx regEx : initialPositiveRegExs) {
			System.out.println(regEx.getRegEx()+"\n");
		}
	}
	
	private void removeWordsOnFrequency(){
		Set<Entry<String, Integer>> entries = wordFreqMap.entrySet();
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
		if (freqWordRemovalLevel > (size/2)) {
			freqWordRemovalLevel = size/2;
		}
		for (int i=1;i<=freqWordRemovalLevel;i++) {
			Entry<String, Integer> leastFreqEntry = sortedEntryList.get(i);
			Entry<String, Integer> mostFreqEntry = sortedEntryList.get(size-i);
			for (RegEx regEx : initialPositiveRegExs) {
				int posScore = calculatePositiveScore(regEx);
				int negScore = calculateNegativeScore(regEx);
				String regExStr = regEx.getRegEx();
				regExStr.replace(mostFreqEntry.getKey(), "\\\\S+");
				RegEx temp = new RegEx(regExStr);
				int tempPosScore = calculatePositiveScore(temp);
				int tempNegScore = calculateNegativeScore(temp);
				if (tempPosScore >= posScore && tempNegScore <= negScore) {
					regEx.setRegEx(regExStr);
				}
				regExStr = regEx.getRegEx();
				regExStr.replace(leastFreqEntry.getKey(), "\\\\S+");
				temp = new RegEx(regExStr);
				tempPosScore = calculatePositiveScore(temp);
				tempNegScore = calculateNegativeScore(temp);
				if (tempPosScore >= posScore && tempNegScore <= negScore) {
					regEx.setRegEx(regExStr);
				}
			}
		}
	}
	
	private void createFrequencyMap(){
		for (RegEx regEx : initialPositiveRegExs) {
			String[] regExStrArray = regEx.getRegEx().split("\\\\s\\{1,50\\}|\\\\p\\{Punct\\}|\\\\d\\+");
			for (String word : regExStrArray) {
				if (wordFreqMap.containsKey(word)) {
					int count = wordFreqMap.get(word);
					wordFreqMap.put(word, ++count);
				} else {
					wordFreqMap.put(word, 1);
				}
			}
		}
	}
	
	private void performTrimming() {
		for (RegEx regEx : initialPositiveRegExs) {
			boolean frontTrim = true;
			boolean backTrim = true;
			while (true) {
				int posScore = calculatePositiveScore(regEx);
				int negScore = calculateNegativeScore(regEx);
				if (frontTrim) {
					RegEx frontTrimRegEx = frontTrim(regEx);
					int trimPosScore = calculatePositiveScore(frontTrimRegEx);
					int trimNegScore = calculateNegativeScore(frontTrimRegEx);
					if (trimPosScore < posScore || trimNegScore > negScore) {
						frontTrim = false;
					} else if (trimPosScore == posScore && trimNegScore == negScore) {
						frontTrim = false;
					} else {
						regEx.setRegEx(frontTrimRegEx.getRegEx());
					}
				}
				if (backTrim) {
					RegEx backTrimRegEx = backTrim(regEx);
					int trimPosScore = calculatePositiveScore(backTrimRegEx);
					int trimNegScore = calculateNegativeScore(backTrimRegEx);
					if (trimPosScore < posScore || trimNegScore > negScore) {
						backTrim = false;
					} else if (trimPosScore == posScore && trimNegScore == negScore) {
						backTrim = false;
					} else {
						regEx.setRegEx(backTrimRegEx.getRegEx());
					}
				}
				if (!frontTrim && !backTrim) {
					break;
				}
			}
		}
	}
	
	private RegEx frontTrim(RegEx regEx) {
		String regExStr = regEx.getRegEx();
		char start = regExStr.charAt(0);
		int cutLocation=1;
		if (start == '\\') {
			while(true){
				if (cutLocation == regExStr.length()) {
					cutLocation = 0;
					break;
				}
				char currentChar = regExStr.charAt(cutLocation++);
				if (currentChar == '+' || currentChar == '}') {
					break;
				}
			}
		} else {
			while(true){
				if (cutLocation == regExStr.length()) {
					cutLocation = 0;
					break;
				}
				char currentChar = regExStr.charAt(cutLocation++);
				if (currentChar == '\\') {
					cutLocation = cutLocation - 1;
					break;
				}
			}
		}
		return new RegEx(regExStr.substring(cutLocation));
	}
	
	private RegEx backTrim(RegEx regEx) {
		String regExStr = regEx.getRegEx();
		char end = regExStr.charAt(regExStr.length()-1);
		int cutLocation=regExStr.length()-2;
		if (end == '+' || end == '}') {
			while(true){
				if (cutLocation < 0) {
					cutLocation = regExStr.length();
					break;
				}
				char currentChar = regExStr.charAt(cutLocation--);
				if (currentChar == '\\') {
					cutLocation = cutLocation+1;
					break;
				}
			}
		} else {
			while(true){
				if (cutLocation < 0) {
					cutLocation = regExStr.length();
					break;
				}
				char currentChar = regExStr.charAt(cutLocation--);
				if (currentChar == '+' || currentChar == '}') {
					cutLocation = cutLocation+2;
					break;
				}
			}
		}
		return new RegEx(regExStr.substring(0,cutLocation));
	}
	
	private int calculatePositiveScore(RegEx regEx) {
		Pattern pattern = patternCache.get(regEx);
		int posScore = 0;
		if (pattern == null) {
			pattern = Pattern.compile(regEx.getRegEx());
			patternCache.put(regEx, pattern);
		}
		for (Snippet snippet : snippetsYes) {
			for (String label : yesLabels) {
				if (snippet.getLabeledSegment(label) != null && snippet.getLabeledSegment(label).getLabeledString() != null && !snippet.getLabeledSegment(label).getLabeledString().equals("")) {
					Matcher matcher = pattern.matcher(snippet.getLabeledSegment(label).getLabeledString());
					if (matcher.find()) {
						posScore++;
					}
				}
			}
		}
		return posScore;
	}
	
	private int calculateNegativeScore(RegEx regEx) {
		Pattern pattern = patternCache.get(regEx);
		int negScore = 0;
		if (pattern == null) {
			pattern = Pattern.compile(regEx.getRegEx());
			patternCache.put(regEx, pattern);
		}
		for (Snippet snippet : snippetsNo) {
			Matcher matcher = pattern.matcher(snippet.getText());
			if (matcher.find()) {
				negScore++;
			}
		}
		return negScore;
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
	
	public Collection<RegEx> replaceDigits(Collection<RegEx> regexColl)
	{
		for(RegEx x : regexColl)
		{
			x.setRegEx(x.getRegEx().replaceAll("\\d+","\\\\d+"));
		}
		return regexColl;
	}
	
	public Collection<RegEx> replaceWhiteSpaces(Collection<RegEx> regexColl)
	{
		for(RegEx x : regexColl)
		{
			x.setRegEx(x.getRegEx().replaceAll("\\s+","\\\\s{1,50}"));
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
