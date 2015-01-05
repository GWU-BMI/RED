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
	
	private static final String P_PUNCT = "\\p{Punct}";
	private static final Map<RegEx, Pattern> patternCache = new HashMap<RegEx, Pattern>();
	private static final boolean PERFORM_TRIMMING = true;
	private static final boolean PERFORM_TRIMMING_OVERALL = true;
	private static final boolean PERFORM_USELESS_REMOVAL_OVERALL = true;
	private static final boolean PERFORM_USELESS_REMOVAL = true;
	private static final boolean FILE_TRANSFER = true;
	
	/**
	 * Reads a vtt file and generates collections of positive and negative regular expressions for classification.
	 * @param vttFile The VTT file containing positive and negative snippets.
	 * @param yesLabels The snippet labels to consider positive.
	 * @param noLabels The snippet labels to consider negative.
	 * @return A two entry map containing collections of regular expressions, one collection matching positive snippets and one collection matching negative snippets.
	 * @throws IOException
	 */
	public Map<String, Collection<RegEx>> findRegexesAndSaveInFile (
			final File vttFile, List<String> yesLabels, List<String> noLabels) throws IOException {
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
		Map<String, Collection<RegEx>> rtMap =  generateRegexClassifications(snippetsYes, snippetsNo, snippetsNoLabel, yesLabels, noLabels);
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
			Collapse.overallCollapser(regEx);
		}
		for (RegEx regEx : initialNegativeRegExs) {
			Collapse.overallCollapser(regEx);
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
			regEx.setSensitivity((double)specifity/initialPositiveRegExs.size());
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
			regEx.setSensitivity((double)specifity/initialNegativeRegExs.size());
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
		positiveAndNegativeRegEx.put(Boolean.TRUE.toString(), initialPositiveRegExs);
		positiveAndNegativeRegEx.put(Boolean.FALSE.toString(), initialNegativeRegExs);
		
		return positiveAndNegativeRegEx;
	}

	private void printRegExs(Collection<RegEx> initialPositiveRegExs) {
		for (RegEx re : initialPositiveRegExs) {
			System.out.println("" + re.getRegEx());
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

	private void uselessRegRemover(RegEx regEx, Collection<Snippet> negSnippets, Collection<String> posLabels){		
//		String regExStr = regEx.getRegEx();
//		StringBuilder regExStrBld = new StringBuilder(regExStr);
//		String backup = null;
//		int start = 0,end =0;
//		while (true) {
//			backup = regExStrBld.toString();
//			while (start < backup.length() && backup.charAt(start) != '\\' && backup.charAt(start) != '[') {
//				start++;
//				end++;
//			}
//			if (start == backup.length()) {
//				break;
//			}
//			while (end < backup.length() && backup.charAt(end) != '}' && backup.charAt(end) != '+') {
//				end++;
//			}
//			end++;
//			if (end == backup.length()) {
//				break;
//			}
//			regExStrBld.replace(start, end, "");
//			regEx.setRegEx(regExStrBld.toString());
//			if (!anyMatches(regEx, negSnippets, posLabels)) {
//				end = start;
//			} else {
//				start = end;
//				regEx.setRegEx(backup);
//				regExStrBld = new StringBuilder(backup);
//			}
//		}
	}
	
	private List<RegEx> initialize(Collection<Snippet> snippets, Collection<String> labels) {
		List<RegEx> initialRegExs = null;
		initialRegExs = new ArrayList<RegEx>(snippets.size());
		initializeInitialRegEx(snippets, labels, initialRegExs);

		replacePunct(initialRegExs);
		
		replaceDigits(initialRegExs);
		
		replaceWhiteSpaces(initialRegExs);
		
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
					boolean fps = anyMatches(temp, negSnippets, posLabels);
					if (!fps) {
						regEx.setRegEx(replacedString);
						Collapse.collapseWhitespace(regEx);
						Collapse.collapsePunct(regEx);
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
	}
	
	/**
	 * @param regex The regular expression to check.
	 * @param snippets The snippets to match the regular expression against.
	 * @param excludeLabels Do not check against snippet labeled segments with these labels.
	 * @return <code>true</code> if there are any matches, <code>false</code> otherwise.
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
			} else {
			Matcher m = pattern.matcher(snippet.getText());
				if (m.find()) {
					return true;
				}
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
	
	private void performTrimming(Collection<RegEx> initialPositiveRegExs, Collection<Snippet> negSnippets, Collection<String> posLabels) {
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
	
	private void performTrimmingIndividual(RegEx regEx, Collection<Snippet> negSnippets, Collection<String> posLabels) {
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
	
	private void initializeInitialRegEx(final Collection<Snippet> snippets,
			Collection<String> labels, Collection<RegEx> initialRegExs) {
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
	
	public CVScore testClassifier(List<Snippet> testing, Collection<RegEx> regularExpressions, Collection<RegEx> negativeRegularExpressions, CategorizerTester tester, Collection<String> labels) throws IOException {
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
			x.setRegEx(x.getRegEx().replaceAll(P_PUNCT,"\\\\p{Punct}"));
		}
		return regexColl;
	}
}
