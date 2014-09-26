package gov.va.research.red.cat;

import gov.va.research.red.CVScore;
import gov.va.research.red.LabeledSegment;
import gov.va.research.red.RegEx;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 
 * case like T1 T2 T1 T2 can cause errors with the current algorithm
 * basically a repeated sequence
 */
public class RegExCategorizer {
	
	private Map<String, Integer> termIDMap = new HashMap<String, Integer>();
	private Map<Integer, String> IDtermMap = new HashMap<Integer, String>();
	private Map<List<Integer>, Integer> posRegExCounterMap = new HashMap<List<Integer>, Integer>();
	private Set<List<Integer>> candidateListSet = new HashSet<List<Integer>>();
	private List<List<Integer>> positiveTermIds;
	private List<List<Integer>> negativeTermIds;
	private static final String PUNCTUATIONS = "punctuations";
	private static final String DIGITS = "digits";
	private static final String SPACES = "spaces";
	private static int TERM_MAP_COUNT = 4;
	private static final int PUNCTUATIONS_INT = 1;
	private static final int DIGITS_INT = 2;
	private static final int SPACES_INT = 3;
	private static final int SAMPLE_SIZE = 2;
	private static final int REGEX_COUNTER_SIZE = 2;
	
	public RegExCategorizer() {
		termIDMap.put(PUNCTUATIONS, PUNCTUATIONS_INT);
		termIDMap.put(DIGITS, DIGITS_INT);
		termIDMap.put(SPACES, SPACES_INT);
		IDtermMap.put(PUNCTUATIONS_INT, PUNCTUATIONS);
		IDtermMap.put(DIGITS_INT, DIGITS);
		IDtermMap.put(SPACES_INT, SPACES);
	}
	
	public void findRegexesAndSaveInFile (
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
		extractRegexClassifications(snippetsYes, snippetsNo, yesLabels, noLabels);
	}

	public void extractRegexClassifications(final List<Snippet> snippetsYes, final List<Snippet> snippetsNo, List<String> yesLabels, List<String> noLabels) {
		if(snippetsYes == null || snippetsYes.isEmpty() || snippetsNo == null || snippetsNo.isEmpty())
			return;
		List<RegEx> initialPositiveRegExs = new ArrayList<RegEx>(snippetsYes.size());
		List<RegEx> initialNegativeRegExs = new ArrayList<RegEx>(snippetsNo.size());
		initializeInitialRegEx(snippetsYes, yesLabels, initialPositiveRegExs);
		initializeInitialRegEx(snippetsNo, noLabels, initialNegativeRegExs);
		
		replaceDigits(initialPositiveRegExs);
		replaceDigits(initialNegativeRegExs);

		replacePunct(initialPositiveRegExs);
		replacePunct(initialNegativeRegExs);
		
		replaceWhiteSpaces(initialPositiveRegExs);
		replaceWhiteSpaces(initialNegativeRegExs);
		
		positiveTermIds = new ArrayList<List<Integer>>(snippetsYes.size());
		negativeTermIds = new ArrayList<List<Integer>>(snippetsNo.size());
		
		initializeTermIDMapAndReplaceRegExWithNumbers(initialPositiveRegExs, positiveTermIds);
		initializeTermIDMapAndReplaceRegExWithNumbers(initialNegativeRegExs, negativeTermIds);
		
		initializePosCounterMap();
		
		extractRegExes();
	}
	
	private int[] generateRandomArray(int arraySize, int limit) {
		int[] randomArray = new int[arraySize];
		for (int i=0; i<arraySize; i++) {
			randomArray[i] = -1;
		}
		Random rand = new Random();
		for (int i=0; i<arraySize; i++) {
			boolean loop = true;
			int randomNumber = -1;
			while (loop) {
				loop = false;
				randomNumber = rand.nextInt(limit);
				for (int j=0;j<i;j++) {
					if (randomNumber == randomArray[j]) {
						loop = true;
						break;
					}
				}
			}
			randomArray[i] = randomNumber;
		}
		return  randomArray;
	}
	
	private void extractRegExes() {
		List<List<Integer>> positiveTermIdsCopy = new LinkedList<List<Integer>>(positiveTermIds);
		while (positiveTermIdsCopy.size() > 0) {
			int randomSetSize = -1;
			if (SAMPLE_SIZE > positiveTermIdsCopy.size()) {
				randomSetSize = positiveTermIdsCopy.size();
			} else {
				randomSetSize = SAMPLE_SIZE;
			}
			int[] randomSet = generateRandomArray(randomSetSize, positiveTermIdsCopy.size());
			List<Integer> workingTermIDs = new LinkedList<Integer>();
			for (int randomIndex : randomSet) {
				workingTermIDs.addAll(positiveTermIdsCopy.get(randomIndex));
			}
			boolean breakRecursion = false;
			permuteList(new LinkedList<Integer>(), workingTermIDs, breakRecursion);
			Iterator<List<Integer>> it = positiveTermIdsCopy.iterator();
			while (it.hasNext()) {
				List<Integer> tempList = it.next();
				if (posRegExCounterMap.get(tempList) >=  REGEX_COUNTER_SIZE) {
					it.remove();
				}
			}
		}
		for (List<Integer> candidateList : candidateListSet) {
			for (Integer term : candidateList) {
				System.out.print(IDtermMap.get(term));
			}
			System.out.println();
		}
	}
	
	private void permuteList(List<Integer> prefix, List<Integer> remaining, boolean breakRecursion) {
		if (!breakRecursion && !remaining.isEmpty()) {
			for (Integer elem : remaining) {
				if (breakRecursion) {
					break;
				}
				List<Integer> remainingCopy = new LinkedList<Integer>(remaining);
				List<Integer> candidateList = new LinkedList<Integer>(prefix);
				candidateList.add(elem);
				remainingCopy.remove(elem);
				if (!candidateListSet.contains(candidateList)) {
					if (!checkIfThereAreFalsePositives(candidateList)) {
						if (checkIfMatchesPositives(candidateList)) {
							candidateListSet.add(candidateList);
							breakRecursion = true;
						}
					}
				}
				if (!breakRecursion) {
					permuteList(candidateList, remainingCopy, breakRecursion);
				}
			}
		}
	}
	
	private boolean checkIfMatchesPositives(List<Integer> candidateList) {
		boolean matchedPositive = false;
		for (List<Integer> posList : positiveTermIds) {
			if (checkIfListContainsOther(posList, candidateList)) {
				matchedPositive = true;
				posRegExCounterMap.put(posList, posRegExCounterMap.get(posList)+1);
			}
		}
		return matchedPositive;
	}
	
	private boolean checkIfListContainsOther(List<Integer> container, List<Integer> contained) {
		for (int i=0;i<container.size();i++) {
			if (container.get(i).equals(contained.get(0))) {
				boolean matched = true;
				for (int j=1;j<contained.size();j++) {
					if ((i+j) < container.size()-1) {
						if (!(container.get(i+j).equals(contained.get(j)))) {
							matched = false;
							break;
						}
					} else {
						matched = false;
						break;
					}
				}
				if (matched) {
					return true;
				}
			}
		}
		return false;
	}
	
	private boolean checkIfThereAreFalsePositives(List<Integer> candidateList) {
		for (List<Integer> negList : negativeTermIds) {
			if (checkIfListContainsOther(negList, candidateList)) {
				return true;
			}
		}
		return false;
	}
	
	private void initializePosCounterMap() {
		for (List<Integer> termIdList : positiveTermIds) {
			posRegExCounterMap.put(termIdList, 0);
		}
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
	
	private void initializeTermIDMapAndReplaceRegExWithNumbers(List<RegEx> regExes, List<List<Integer>> termIdList) {
		for (RegEx regEx : regExes) {
			String regExStr = regEx.getRegEx();
			List<Integer> regExTermIdList = new ArrayList<Integer>();
			int prevIntIndex = -1;
			int currentIndIndex = 0;
			for (int i=0;i<regExStr.length();i++) {
				int currentCharacter = Character.getNumericValue(regExStr.charAt(i));
				if (currentCharacter == PUNCTUATIONS_INT || currentCharacter == DIGITS_INT || currentCharacter == SPACES_INT) {
					currentIndIndex = i;
					if ((currentIndIndex - prevIntIndex) > 1) {
						String term = regExStr.substring(prevIntIndex+1, currentIndIndex);
						if (!termIDMap.containsKey(term)) {
							termIDMap.put(term, TERM_MAP_COUNT);
							IDtermMap.put(TERM_MAP_COUNT, term);
							TERM_MAP_COUNT = TERM_MAP_COUNT + 1;
						}
						regExTermIdList.add(termIDMap.get(term));
					}
					regExTermIdList.add(currentIndIndex);
					prevIntIndex = currentIndIndex;
				}
			}
			termIdList.add(regExTermIdList);
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
			x.setRegEx(x.getRegEx().replaceAll("\\d+",Integer.toString(DIGITS_INT)));
		}
		return regexColl;
	}
	
	public Collection<RegEx> replaceWhiteSpaces(Collection<RegEx> regexColl)
	{
		for(RegEx x : regexColl)
		{
			x.setRegEx(x.getRegEx().replaceAll("\\s+",Integer.toString(SPACES_INT)));
		}
		return regexColl;
	}
	
	public Collection<RegEx> replacePunct(Collection<RegEx> regexColl)
	{
		for(RegEx x : regexColl)
		{
			x.setRegEx(x.getRegEx().replaceAll("\\p{Punct}",Integer.toString(PUNCTUATIONS_INT)));
		}
		return regexColl;
	}
}
