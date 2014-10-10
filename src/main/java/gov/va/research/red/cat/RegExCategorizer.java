package gov.va.research.red.cat;

import gov.va.research.red.CVScore;
import gov.va.research.red.LabeledSegment;
import gov.va.research.red.RegEx;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
	private int mostfreqWordRemovalLevel = 10;
	private int leastfreqWordRemovalLevel = 10;
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
		writeSnippetsToFile();
		extractRegexClassifications();
	}
	
	private void writeSnippetsToFile() throws IOException {
		File file = new File("snippets-text-positive.txt");
		file.createNewFile();
		BufferedWriter writer = new BufferedWriter(new FileWriter(file));
		writer.write("Positive Snippets");
		writer.newLine();
		for (Snippet yes : snippetsYes) {
			writer.newLine();
			writer.write("Snippet");
			writer.newLine();
			writer.newLine();
			writer.write(yes.getText());
			writer.newLine();
			writer.newLine();
			writer.write("Labeled Segment");
			writer.newLine();
			writer.newLine();
			writer.write(yes.getLabeledSegment("yes").getLabeledString());
			writer.newLine();
		}
		writer.close();
		file = new File("snippets-text-negative.txt");
		file.createNewFile();
		writer = new BufferedWriter(new FileWriter(file));
		writer.write("Negative Snippets");
		writer.newLine();
		for (Snippet no : snippetsNo) {
			writer.newLine();
			writer.write("Snippet");
			writer.newLine();
			writer.newLine();
			writer.write(no.getText());
			writer.newLine();
		}
		writer.close();
	}

	public void extractRegexClassifications() throws IOException {
		if(snippetsYes == null || snippetsYes.isEmpty() || snippetsNo == null || snippetsNo.isEmpty())
			return;
		List<Snippet> snippetsAll = new ArrayList<Snippet>();
		snippetsAll.addAll(snippetsYes);
		snippetsAll.addAll(snippetsNo);
		CVScore cvScore = null;
		initialPositiveRegExs = initialize();
		
		/*performTrimming();
		
		for (RegEx regEx : initialPositiveRegExs) {
			System.out.println(regEx.getRegEx()+"\n");
		}
		
		cvScore = testClassifier(snippetsAll, initialPositiveRegExs, null, yesLabels);
		try {
			System.out.println(cvScore.getEvaluation());
		} catch (IOException e) {
			e.printStackTrace();
		}*/
		
		//initialPositiveRegExs = initialize();
		
		createFrequencyMap();
		
		/*removeMostFrequent();
		
		cvScore = testClassifier(snippetsAll, initialPositiveRegExs, null, yesLabels);
		try {
			System.out.println(cvScore.getEvaluation());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		initialPositiveRegExs = initialize();*/
		
		//createFrequencyMap();
		
		/*removeLeastFrequent();
		
		cvScore = testClassifier(snippetsAll, initialPositiveRegExs, null, yesLabels);
		try {
			System.out.println(cvScore.getEvaluation());
		} catch (IOException e) {
			e.printStackTrace();
		}*/
		
		Map<String, Integer> snippetPosFreq = createFrequencyMapList(convertSnippetToRegEx(snippetsYes));
		Map<String, Integer> snippetNegFreq = createFrequencyMapList(convertSnippetToRegEx(snippetsNo));
		storeSortedWordsTofiles(snippetPosFreq, snippetNegFreq);
	}
	
	private void storeSortedWordsTofiles(Map<String, Integer> snippetPosFreq, Map<String, Integer> snippetNegFreq) throws IOException{
		List<Entry<String, Integer>> sortedPosList = new ArrayList<Map.Entry<String,Integer>>();
		sortedPosList.addAll(snippetPosFreq.entrySet());
		List<Entry<String, Integer>> sortednegList = new ArrayList<Map.Entry<String,Integer>>();
		sortednegList.addAll(snippetNegFreq.entrySet());
		List<Entry<String, Integer>> sortedLabeledSegment = new ArrayList<Map.Entry<String,Integer>>();
		sortedLabeledSegment.addAll(wordFreqMap.entrySet());
		Comparator<Entry<String, Integer>> comp = new Comparator<Map.Entry<String,Integer>>() {

			@Override
			public int compare(Entry<String, Integer> o1,
					Entry<String, Integer> o2) {
				if (o1.getValue() < o2.getValue()) {
					return 1;
				} else if (o1.getValue() > o2.getValue()) {
					return -1;
				}
				return 0;
			}
		};
		Collections.sort(sortedPosList, comp);
		Collections.sort(sortednegList, comp);
		Collections.sort(sortedLabeledSegment, comp);
		
		File file = new File("frequency-caseinsensitive-word-snippets-positive.txt");
		file.createNewFile();
		BufferedWriter writer = new BufferedWriter(new FileWriter(file));
		writer.newLine();
		for (Entry<String, Integer> entry : sortedPosList) {
			writer.newLine();
			writer.write(entry.getKey());
			writer.newLine();
		}
		writer.close();
		
		file = new File("frequency-caseinsensitive-word-snippets-negative.txt");
		file.createNewFile();
		writer = new BufferedWriter(new FileWriter(file));
		writer.newLine();
		for (Entry<String, Integer> entry : sortednegList) {
			writer.newLine();
			writer.write(entry.getKey());
			writer.newLine();
		}
		writer.close();
		
		file = new File("frequency-caseinsensitive-word-labeledsegment-positive.txt");
		file.createNewFile();
		writer = new BufferedWriter(new FileWriter(file));
		writer.newLine();
		for (Entry<String, Integer> entry : sortedLabeledSegment) {
			writer.newLine();
			writer.write(entry.getKey());
			writer.newLine();
		}
		writer.close();
	}
	
	private List<RegEx> initialize() {
		List<RegEx> initialPositiveRegExs = new ArrayList<RegEx>(snippetsYes.size());
		initializeInitialRegEx(snippetsYes, yesLabels, initialPositiveRegExs);

		replacePunct(initialPositiveRegExs);
		
		replaceDigits(initialPositiveRegExs);
		
		replaceWhiteSpaces(initialPositiveRegExs);
		
		return initialPositiveRegExs;
	}
		
	private void removeMostFrequent(){
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
		if (mostfreqWordRemovalLevel > (size)) {
			mostfreqWordRemovalLevel = size;
		}
		for (int i=1;i<=mostfreqWordRemovalLevel;i++) {
			Entry<String, Integer> mostFreqEntry = sortedEntryList.get(size-i);
			for (RegEx regEx : initialPositiveRegExs) {
				int posScore = calculatePositiveScore(regEx);
				int negScore = calculateNegativeScore(regEx);
				String regExStr = regEx.getRegEx();
				String replacedString = regExStr.replaceAll("(?i)"+mostFreqEntry.getKey(), "\\\\S+");
				RegEx temp = new RegEx(replacedString);
				int tempPosScore = calculatePositiveScore(temp);
				int tempNegScore = calculateNegativeScore(temp);
				//if (tempPosScore >= posScore && tempNegScore <= negScore) {
					regEx.setRegEx(replacedString);
				//}
			}
		}
	}
	
	private void removeLeastFrequent(){
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
		if (leastfreqWordRemovalLevel > (size)) {
			leastfreqWordRemovalLevel = size;
		}
		for (int i=0;i< leastfreqWordRemovalLevel;i++) {
			Entry<String, Integer> leastFreqEntry = sortedEntryList.get(i);
			for (RegEx regEx : initialPositiveRegExs) {
				int posScore = calculatePositiveScore(regEx);
				int negScore = calculateNegativeScore(regEx);
				String regExStr = regEx.getRegEx();
				String replacedString = regExStr.replaceAll("(?i)"+leastFreqEntry.getKey(), "\\\\S+");
				RegEx temp = new RegEx(replacedString);
				int tempPosScore = calculatePositiveScore(temp);
				int tempNegScore = calculateNegativeScore(temp);
				//if (tempPosScore >= posScore && tempNegScore <= negScore) {
					regEx.setRegEx(replacedString);
				//}
			}
		}
	}
	
	private void createFrequencyMap(){
		for (RegEx regEx : initialPositiveRegExs) {
			String[] regExStrArray = regEx.getRegEx().split("\\\\s\\{1,50\\}|\\\\p\\{Punct\\}|\\\\d\\+");
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
			String[] regExStrArray = regEx.getRegEx().split("\\\\s\\{1,50\\}|\\\\p\\{Punct\\}|\\\\d\\+");
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
	
	private void performTrimming() {
		for (RegEx regEx : initialPositiveRegExs) {
			boolean frontTrim = true;
			boolean backTrim = true;
			while (true) {
				int posScore = calculatePositiveScore(regEx);
				int negScore = calculateNegativeScore(regEx);
				if (frontTrim) {
					RegEx frontTrimRegEx = frontTrim(regEx);
					if (frontTrimRegEx == null) {
						frontTrim = false;
					} else {
						int trimPosScore = calculatePositiveScore(frontTrimRegEx);
						int trimNegScore = calculateNegativeScore(frontTrimRegEx);
						if (trimPosScore < posScore || trimNegScore > negScore) {
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
						int trimPosScore = calculatePositiveScore(backTrimRegEx);
						int trimNegScore = calculateNegativeScore(backTrimRegEx);
						if (trimPosScore < posScore || trimNegScore > negScore) {
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
	
	private RegEx frontTrim(RegEx regEx) {
		String regExStr = regEx.getRegEx();
		char start = regExStr.charAt(0);
		int cutLocation=1;
		if (start == '\\') {
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
			while(true){
				if (cutLocation == regExStr.length()) {
					return null;
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
					return null;
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
					return null;
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
	
	public CVScore testClassifier(List<Snippet> testing, Collection<RegEx> regularExpressions, CategorizerTester tester, List<String> labels) {
		CVScore score = new CVScore();
		if(tester == null)
			tester = new CategorizerTester();
		for(Snippet testSnippet : testing){
			boolean predicted = tester.test(regularExpressions, testSnippet);
			boolean actual = false;
			for(String label : labels){
				LabeledSegment actualSegment = testSnippet.getLabeledSegment(label);
				if(actualSegment != null) {
					actual = true;
					break;
				}
			}
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
