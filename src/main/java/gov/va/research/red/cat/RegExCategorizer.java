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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
	private Map<String, Integer> wordFreqMap = new HashMap<String, Integer>();
	private int mostfreqWordRemovalLevel = 10;
	private int leastfreqWordRemovalLevel = 10;
	private List<String> yesLabels;
	private List<String> noLabels;
	private Map<RegEx, List<LabeledSegment>> reg2Ls = new HashMap<RegEx, List<LabeledSegment>>();
	private Map<LabeledSegment, List<RegEx>> lS2Reg = new HashMap<LabeledSegment, List<RegEx>>();
	private Map<RegEx, List<LabeledSegment>> reg2LsNeg = new HashMap<RegEx, List<LabeledSegment>>();
	private Map<LabeledSegment, List<RegEx>> lS2RegNeg = new HashMap<LabeledSegment, List<RegEx>>();
	
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
		snippetsNoLabel = new ArrayList<Snippet>();
		snippetsNoLabel.addAll(vttr.extractSnippets(vttFile));
		//writeSnippetsToFile();
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
		
		initialPositiveRegExs = initialize(true);
		performTrimming(true);
		
		initialNegativeRegExs = initialize(false);
		performTrimming(false);
		
		for (RegEx regEx : initialPositiveRegExs) {
			int posScore = calculatePositiveScore(regEx, true, true);
			regEx.setSpecifity(posScore);
		}
		
		for (RegEx regEx : initialNegativeRegExs) {
			int negScore = calculateNegativeScore(regEx, false, true);
			regEx.setSpecifity(negScore);
		}
		
		System.out.println("Pos regex");
		for (RegEx regEx : initialPositiveRegExs) {
			System.out.println(regEx.getRegEx()+" "+regEx.getSpecifity()+"\n");
		}
		System.out.println("\nNeg regex");
		for (RegEx regEx : initialNegativeRegExs) {
			System.out.println(regEx.getRegEx()+" "+regEx.getSpecifity()+"\n");
		}
		
		int orgPos = initialPositiveRegExs.size();
		int orgNeg = initialPositiveRegExs.size();
		
		Iterator<Entry<RegEx, List<LabeledSegment>>> entrySetIt = reg2Ls.entrySet().iterator();
		while (entrySetIt.hasNext()) {
			Entry<RegEx, List<LabeledSegment>> entry = entrySetIt.next();
			List<LabeledSegment> lsList = entry.getValue();
			boolean remove = false;
			for (LabeledSegment ls : lsList) {
				List<RegEx> regExLs = lS2Reg.get(ls);
				if (regExLs != null && regExLs.size() > 5){
					remove = true;
				}else {
					remove = false;
				}
			}
			if (remove) {
				entrySetIt.remove();
				initialPositiveRegExs.remove(entry.getKey());
			}
		}
		
		entrySetIt = reg2LsNeg.entrySet().iterator();
		while (entrySetIt.hasNext()) {
			Entry<RegEx, List<LabeledSegment>> entry = entrySetIt.next();
			List<LabeledSegment> lsList = entry.getValue();
			boolean remove = false;
			for (LabeledSegment ls : lsList) {
				List<RegEx> regExLs = lS2RegNeg.get(ls);
				if (regExLs != null && regExLs.size() > 5){
					remove = true;
				}else {
					remove = false;
				}
			}
			if (remove) {
				entrySetIt.remove();
				initialNegativeRegExs.remove(entry.getKey());
			}
		}
		
		System.out.println("\nPos regex");
		for (RegEx regEx : initialPositiveRegExs) {
			System.out.println(regEx.getRegEx()+" "+regEx.getSpecifity()+"\n");
		}
		System.out.println("\nNeg regex");
		for (RegEx regEx : initialNegativeRegExs) {
			System.out.println(regEx.getRegEx()+" "+regEx.getSpecifity()+"\n");
		}
		
		if (orgPos != initialPositiveRegExs.size()) {
			System.out.println("pos difference made " +(orgPos - initialPositiveRegExs.size()));
		}
		
		if (orgNeg != initialNegativeRegExs.size()) {
			System.out.println("neg difference made " +(orgNeg - initialNegativeRegExs.size()));
		}
		
		cvScore = testClassifier(snippetsAll, initialPositiveRegExs, initialNegativeRegExs, null, yesLabels);
		try {
			System.out.println(cvScore.getEvaluation());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//initialPositiveRegExs = initialize();
		
		//createFrequencyMap();
		
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
		
		/*Map<String, Integer> snippetPosFreq = createFrequencyMapList(convertSnippetToRegEx(snippetsYes));
		Map<String, Integer> snippetNegFreq = createFrequencyMapList(convertSnippetToRegEx(snippetsNo));
		storeSortedWordsTofiles(snippetPosFreq, snippetNegFreq);*/
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
		
	/*private void removeMostFrequent(){
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
	}*/
	
	/*private void removeLeastFrequent(){
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
	}*/
	
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
	
	private int calculatePositiveScore(RegEx regEx, boolean positiveTrim, boolean mapEntry) {
		Pattern pattern = patternCache.get(regEx);
		int posScore = 0;
		if (pattern == null) {
			pattern = Pattern.compile(regEx.getRegEx());
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
			pattern = Pattern.compile(regEx.getRegEx());
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
			pattern = Pattern.compile(regEx.getRegEx());
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
	
	public CVScore testClassifier(List<Snippet> testing, Collection<RegEx> regularExpressions, Collection<RegEx> negativeRegularExpressions, CategorizerTester tester, List<String> labels) {
		CVScore score = new CVScore();
		if(tester == null)
			tester = new CategorizerTester();
		for(Snippet testSnippet : testing){
			boolean predicted = tester.test(regularExpressions, negativeRegularExpressions, testSnippet);
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
