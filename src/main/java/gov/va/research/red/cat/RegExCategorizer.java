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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 * case like T1 T2 T1 T2 can cause errors with the current algorithm
 * basically a repeated sequence
 */
public class RegExCategorizer {
	
	private Map<String, Pattern> patternCache = new HashMap<>();
	private static final int SNIPPET_MATCH_COUNT = 2;
	private static final int SNIPPET_CHUNK_SIZE = 5;
	private static final String YES = "yes";
	private static final String NO = "no";
	
	public Map<String, List<RegEx>> findRegexesAndSaveInFile (
			final File vttFile, List<String> yesLabels, List<String> noLabels,
			final String classifierOutputFileName, boolean printScore) throws IOException {
		if (yesLabels == null || yesLabels.isEmpty() || noLabels == null || noLabels.isEmpty()) {
			return null;
		}
		VTTReader vttr = new VTTReader();
		List<Snippet> snippetsYes = new ArrayList<>();
		for (String yesLabel : yesLabels) {
			snippetsYes.addAll(vttr.extractSnippets(vttFile, yesLabel));
		}	
		List<Snippet> snippetsNo = new ArrayList<>();
		for (String noLabel : noLabels) {
			snippetsNo.addAll(vttr.extractSnippets(vttFile, noLabel));
		}
		Map<String, List<RegEx>> regExClassificationMap = extractRegExInChunks(snippetsYes, yesLabels, snippetsNo, noLabels);
		saveOutputInFile(classifierOutputFileName, regExClassificationMap);
		if (printScore) {
			CVScore score = testClassifier(snippetsYes, regExClassificationMap.get(YES), null,
					yesLabels);
			System.out.println(score.getEvaluation());
			score = testClassifier(snippetsNo, regExClassificationMap.get(NO), null, noLabels);
			System.out.println(score.getEvaluation());
		}
		return regExClassificationMap;
	}
	
	private Map<String, List<RegEx>> extractRegExInChunks(List<Snippet> snippetsYes, List<String> yesLabels, List<Snippet> snippetsNo, List<String> noLabels) {
		List<RegEx> ls2regExYes = new ArrayList<RegEx>();
		List<RegEx> ls2regExNo = new ArrayList<RegEx>();
		ls2regExYes = extractRegexClassifications(
				snippetsYes, yesLabels);
		ls2regExNo = extractRegexClassifications(
				snippetsNo, noLabels);
		Map<String, List<RegEx>> returnMap = new HashMap<>();
		returnMap.put(YES, ls2regExYes);
		returnMap.put(NO, ls2regExNo);
		return returnMap;
	}
	
	private void saveOutputInFile(final String classifierOutputFileName, Map<String, List<RegEx>> regExClassificationMap) throws IOException {
		List<RegEx> ls2regExYes = regExClassificationMap.get(YES);
		List<RegEx> ls2regExNo = regExClassificationMap.get(NO);
		if (classifierOutputFileName != null
				&& !classifierOutputFileName.equals("")) {
			File outputFile = new File(classifierOutputFileName);
			if (!outputFile.exists())
				outputFile.createNewFile();
			FileWriter fWriter = new FileWriter(outputFile.getAbsoluteFile(),
					false);
			PrintWriter pWriter = new PrintWriter(fWriter);
			if (ls2regExYes != null) {
				pWriter.println("yes regex");
				for (RegEx regEx : ls2regExYes) {
					pWriter.println(regEx.getRegEx());
				}
			}
			if (ls2regExNo != null) {
				pWriter.println("\nno regex");
				for (RegEx regEx : ls2regExNo) {
					pWriter.println(regEx.getRegEx());
				}
			}	
			pWriter.close();
			fWriter.close();
		}
	}
	

	public List<RegEx> extractRegexClassifications(final List<Snippet> snippets, final List<String> labels) {
		if(snippets == null || snippets.isEmpty())
			return null;
		List<RegEx> ls2regex = new ArrayList<RegEx>(snippets.size());
		for(Snippet snippet : snippets) {
			for(String label : labels) {
				LabeledSegment labeledSegment = snippet.getLabeledSegment(label);
				if(labeledSegment != null) {
					ls2regex.add(new RegEx(labeledSegment.getLabeledString()));
				}
			}
		}
		//replace all the punctuations with their regular expressions in the labeled
		//segments
		replacePunctClassification(ls2regex);
		//replace all the digits with their regular expressions in the labeled
		//segments
		replaceDigitsClassification(ls2regex);
		//replace all the whitespaces with their regular expressions in the labeled
		//segments
		replaceWhiteSpacesClassification(ls2regex);
		List<PotentialMatchClassification> potentialList = new ArrayList<>();
		treeReplacementLogicClassification(ls2regex, potentialList);
		Comparator<PotentialMatchClassification> comp = new Comparator<PotentialMatchClassification>() {

			@Override
			public int compare(PotentialMatchClassification o1, PotentialMatchClassification o2) {
				int size1 = 0;
				int size2 = 0;
				for(String term : o1.terms){
					size1 = size1 + term.length();
				}
				for(String term : o2.terms){
					size2 = size2 + term.length();
				}
				if(size1 < size2)
					return 1;
				if(size1 > size2)
					return -1;
				return 0;
			}
			
		};
		Collections.sort(potentialList, comp);
		replacePotentialMatchesClassification(potentialList, ls2regex, snippets, labels);
		return ls2regex;
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
	private void replacePotentialMatchesClassification(List<PotentialMatchClassification> potentialMatches, Collection<RegEx> regexList, final List<Snippet> snippets, List<String> labels){
		for(PotentialMatchClassification match : potentialMatches){
			if(match.count == 1 && match.termSize > 1){
				for(Match potentialMatch : match.matches){
					//String key = match.toString();
					String bls = potentialMatch.match.getRegEx();
					if(bls.contains(potentialMatch.matchedString)){
						//if(!key.equals("S")){
						List<RegEx> regEx = new ArrayList<RegEx>();
						regEx.add(potentialMatch.match);
						//CVScore cvScore = cv.testClassifier(snippets, regEx, null, labels);
						//int start = bls.indexOf(potentialMatch.matchedString);
						//int end = potentialMatch.matchedString.length();
						potentialMatch.match.setRegEx(bls.replace(potentialMatch.matchedString, "[\\s\\S]+"));//triplet.getBLS().replaceAll("?:"+key, "(?:"+key+")");
						//potentialMatch.match.setRegEx(bls.substring(0, start)+"[\\s\\S\\d\\p{Punct}]+"+bls.substring(end));//triplet.getBLS().replaceAll("?:"+key, "(?:"+key+")");
						/*regEx = new ArrayList<ClassifierRegEx>();
						regEx.add(triplet.match);*/
						CVScore cvScore2 = testClassifier(snippets, regEx, null, labels);
						if(cvScore2.getFp() != 0)
							potentialMatch.match.setRegEx(bls);
					}
				}
			}
		}
	}
	
	private void updateMFTMapClassification(RegEx regex, Map<String, List<RegEx>> freqMap){
		String phrase = "";
		phrase = regex.getRegEx();
		String[] termArray = phrase.split("\\\\s\\{1,50\\}|\\\\p\\{Punct\\}|\\\\d\\+");
		List<RegEx> termContainingRegexes = null;
		for(String term : termArray){
			if(!term.equals(" ") && !term.equals("")){
				if(freqMap.containsKey(term))
					termContainingRegexes = freqMap.get(term);
				else
					termContainingRegexes = new ArrayList<RegEx>();
				termContainingRegexes.add(regex);
				freqMap.put(term, termContainingRegexes);
			}
		}
	}
	
	private void treeReplacementLogicClassification(Collection<RegEx> regexList, Collection<PotentialMatchClassification> potentialList){
		Map<String, List<RegEx>> freqMap = new HashMap<String, List<RegEx>>();
		for(RegEx regex : regexList)
			updateMFTMapClassification(regex, freqMap);
		List<String> termList = new ArrayList<>();
		termList.addAll(freqMap.keySet());
		performPermuationClassification(null, termList, regexList, potentialList);
	}
	
	private void performPermuationClassification(Collection<String> prefixList, List<String> termList, Collection<RegEx> regexList, Collection<PotentialMatchClassification> potentialList){
		if(!termList.isEmpty()){
			for(int i=0;i<termList.size();i++){
				List<String> tempPrefixList = new ArrayList<>();
				if(prefixList != null)
					tempPrefixList.addAll(prefixList);
				String tempElement = termList.get(i);
				tempPrefixList.add(tempElement);
				List<String> tempTermList = new ArrayList<>();
				tempTermList.addAll(termList);
				tempTermList.remove(tempElement);
				PotentialMatchClassification match = findMatchClassification(tempPrefixList, regexList);
				if(match.count > 0){
					potentialList.add(match);
					performPermuationClassification(tempPrefixList, tempTermList, regexList, potentialList);
				}
			}
		}
	}
	
	private PotentialMatchClassification findMatchClassification(List<String> termList, Collection<RegEx> regexList){
		StringBuilder concatString = new StringBuilder("");
		int count = 0;
		List<Match> matches = new ArrayList<>();
		int size = termList.size();
		for(int i=0;i<termList.size();i++){
			if(i==termList.size()-1)
				concatString.append(termList.get(i));
			else
				concatString.append(termList.get(i)+"\\\\s\\{1,50\\}");
		}
		Pattern pattern = null;
		String regex = concatString.toString();
		if(patternCache.containsKey(regex)){
			pattern = patternCache.get(regex);
		} else {
			pattern = Pattern.compile(regex);
		}
		for(RegEx catRegex : regexList){
			String matchAgainst = null;
			matchAgainst = catRegex.getRegEx();
			Matcher matcher = pattern.matcher(matchAgainst);
			if(matcher.find()){
				count++;
				String matchedString = matchAgainst.substring(matcher.start(), matcher.end());
				matches.add(new Match(catRegex, matchedString, matcher.start(), matcher.end()));
			}
		}
		PotentialMatchClassification match = new PotentialMatchClassification(termList, count, matches, size);
		return match;
	}
	
	public Collection<RegEx> replaceDigitsClassification(Collection<RegEx> regexColl)
	{
		for(RegEx x : regexColl)
		{
			x.setRegEx(x.getRegEx().replaceAll("\\d+","\\\\d+"));
		}
		return regexColl;
	}
	
	public Collection<RegEx> replaceWhiteSpacesClassification(Collection<RegEx> regexColl)
	{
		for(RegEx x : regexColl)
		{
			x.setRegEx(x.getRegEx().replaceAll("\\s+","\\\\s{1,50}"));
		}
		return regexColl;
	}
	
	public Collection<RegEx> replacePunctClassification(Collection<RegEx> regexColl)
	{
		for(RegEx x : regexColl)
		{
			x.setRegEx(x.getRegEx().replaceAll("\\p{Punct}","\\\\p{Punct}"));
		}
		return regexColl;
	}
}

class PotentialMatchClassification {
	List<String> terms;
	int count;
	List<Match> matches;
	int termSize;
	
	public PotentialMatchClassification(List<String> terms, int count, List<Match> matches, int termSize) {
		this.terms = terms;
		this.count = count;
		this.matches = matches;
		this.termSize = termSize;
	}
	
	/*@Override
	public String toString() {
		if(terms == null || terms.isEmpty())
			return " The match is empty";
		StringBuilder temp = new StringBuilder();
		for(int i=0;i<terms.size();i++){
			if(i==terms.size()-1)
				temp.append(terms.get(i));
			else
				temp.append(terms.get(i)+"\\s{1,50}*|\\p{punct}*|\\d*");
		}
		return temp.toString();
	}*/
}
class Match {
	RegEx match;
	String matchedString;
	int startPos;
	int endPos;
	
	public Match(RegEx match, String matchedString, int startPos, int endPos) {
		this.match = match;
		this.matchedString = matchedString;
		this.startPos = startPos;
		this.endPos = endPos;
	}
}
