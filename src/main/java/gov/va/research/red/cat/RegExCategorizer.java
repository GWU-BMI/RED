package gov.va.research.red.cat;

import gov.va.research.red.CVScore;
import gov.va.research.red.LabeledSegment;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;
import gov.va.research.red.ex.REDExCrossValidator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
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
	
	private REDExCrossValidator cv = new REDExCrossValidator();
	private Map<String, Pattern> patternCache = new HashMap<>();
	
	public Map<String, List<CategorizerRegEx>> extracteRegexClassifications(
			final File vttFile, List<String> yesLabels, List<String> noLabels,
			final String classifierOutputFileName) throws IOException {
		VTTReader vttr = new VTTReader();
		List<Snippet> snippetsYes = new ArrayList<>();
		for (String yesLabel : yesLabels) {
			snippetsYes.addAll(vttr.extractSnippets(vttFile, yesLabel));
		}
		List<CategorizerRegEx> regExYes = extracteRegexClassifications(
				snippetsYes, yesLabels);
		List<Snippet> snippetsNo = new ArrayList<>();
		for (String noLabel : noLabels) {
			snippetsNo.addAll(vttr.extractSnippets(vttFile, noLabel));
		}
		List<CategorizerRegEx> regExNo = extracteRegexClassifications(
				snippetsNo, noLabels);
		if (classifierOutputFileName != null
				&& !classifierOutputFileName.equals("")) {
			File outputFile = new File(classifierOutputFileName);
			if (!outputFile.exists())
				outputFile.createNewFile();
			FileWriter fWriter = new FileWriter(outputFile.getAbsoluteFile(),
					false);
			PrintWriter pWriter = new PrintWriter(fWriter);
			pWriter.println("yes regex");
			for (CategorizerRegEx regEx : regExYes) {
				pWriter.println(regEx.getRegEx());
			}
			pWriter.println("\nno regex");
			for (CategorizerRegEx regEx : regExNo) {
				pWriter.println(regEx.getRegEx());
			}
			pWriter.close();
			fWriter.close();
		}
		REDExCrossValidator cv = new REDExCrossValidator();
		CVScore score = testClassifier(snippetsYes, regExYes, null,
				yesLabels);
		System.out.println(score.getEvaluation());
		score = testClassifier(snippetsNo, regExNo, null, noLabels);
		System.out.println(score.getEvaluation());
		Map<String, List<CategorizerRegEx>> returnMap = new HashMap<>();
		returnMap.put("yes", regExYes);
		returnMap.put("no", regExNo);
		return returnMap;
	}
	

	public List<CategorizerRegEx> extracteRegexClassifications(final List<Snippet> snippets, final List<String> labels) {
		if(snippets == null || snippets.isEmpty())
			return null;
		List<LabeledSegment> listOfLabeledSegments = new ArrayList<>(snippets.size());
		for(Snippet snippet : snippets){
			for(String label : labels){
				LabeledSegment labeledSegment = snippet.getLabeledSegment(label);
				if(labeledSegment != null)
					listOfLabeledSegments.add(labeledSegment);
			}
		}
		//replace all the punctuations with their regular expressions in the labeled
		//segments
		replacePunctClassification(listOfLabeledSegments);
		//replace all the digits with their regular expressions in the labeled
		//segments
		replaceDigitsClassification(listOfLabeledSegments);
		//replace all the whitespaces with their regular expressions in the labeled
		//segments
		replaceWhiteSpacesClassification(listOfLabeledSegments);
		List<PotentialMatchClassification> potentialList = new ArrayList<>();
		List<CategorizerRegEx> listClassifierRegEx = new ArrayList<>();
		for(LabeledSegment lsSeg : listOfLabeledSegments){
			listClassifierRegEx.add(new CategorizerRegEx(lsSeg.getLabeledString()));
		}
		treeReplacementLogicClassification(listClassifierRegEx, potentialList);
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
		replacePotentialMatchesClassification(potentialList, listClassifierRegEx, snippets, labels);
		return listClassifierRegEx;
	}

	public CVScore testClassifier(List<Snippet> testing, List<CategorizerRegEx> regularExpressions, CategorizerTester tester, List<String> labels) {
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
	private void replacePotentialMatchesClassification(List<PotentialMatchClassification> potentialMatches, List<CategorizerRegEx> ls3List, final List<Snippet> snippets, List<String> labels){
		for(PotentialMatchClassification match : potentialMatches){
			if(match.count == 1 && match.termSize > 1){
				for(Match potentialMatch : match.matches){
					//String key = match.toString();
					String bls = potentialMatch.match.getRegEx();
					if(bls.contains(potentialMatch.matchedString)){
						//if(!key.equals("S")){
						List<CategorizerRegEx> regEx = new ArrayList<CategorizerRegEx>();
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
	
	private void updateMFTMapClassification(CategorizerRegEx triplet, Map<String, List<CategorizerRegEx>> freqMap){
		String phrase = "";
		phrase = triplet.getRegEx();
		String[] termArray = phrase.split("\\\\s\\{1,50\\}|\\\\p\\{Punct\\}|\\\\d\\+");
		List<CategorizerRegEx> termContainingTriplets = null;
		for(String term : termArray){
			if(!term.equals(" ") && !term.equals("")){
				if(freqMap.containsKey(term))
					termContainingTriplets = freqMap.get(term);
				else
					termContainingTriplets = new ArrayList<CategorizerRegEx>();
				termContainingTriplets.add(triplet);
				freqMap.put(term, termContainingTriplets);
			}
		}
	}
	
	private void treeReplacementLogicClassification(List<CategorizerRegEx> ls3List, List<PotentialMatchClassification> potentialList){
		Map<String, List<CategorizerRegEx>> freqMap = new HashMap<String, List<CategorizerRegEx>>();
		for(CategorizerRegEx triplet : ls3List)
			updateMFTMapClassification(triplet, freqMap);
		List<String> termList = new ArrayList<>();
		termList.addAll(freqMap.keySet());
		performPermuationClassification(null, termList, ls3List, potentialList);
	}
	
	private void performPermuationClassification(List<String> prefixList, List<String> termList, List<CategorizerRegEx> ls3List, List<PotentialMatchClassification> potentialList){
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
				PotentialMatchClassification match = findMatchClassification(tempPrefixList, ls3List);
				if(match.count > 0){
					potentialList.add(match);
					performPermuationClassification(tempPrefixList, tempTermList, ls3List, potentialList);
				}
			}
		}
	}
	
	private PotentialMatchClassification findMatchClassification(List<String> termList, List<CategorizerRegEx> ls3List){
		StringBuilder concatString = new StringBuilder("");
		int count = 0;
		List<Match> triplets = new ArrayList<>();
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
		}else{
			pattern = Pattern.compile(regex);
		}
		for(CategorizerRegEx triplet : ls3List){
			String matchAgainst = null;
			matchAgainst = triplet.getRegEx();
			Matcher matcher = pattern.matcher(matchAgainst);
			if(matcher.find()){
				count++;
				String matchedString = matchAgainst.substring(matcher.start(), matcher.end());
				triplets.add(new Match(triplet, matchedString, matcher.start(), matcher.end()));
			}
		}
		PotentialMatchClassification match = new PotentialMatchClassification(termList, count, triplets, size);
		return match;
	}
	
	public List<LabeledSegment> replaceDigitsClassification(List<LabeledSegment> ls3list)
	{
		String s;
		for(LabeledSegment x : ls3list)
		{
			s=x.getLabeledString();
			s=s.replaceAll("\\d+","\\\\d+");
			x.setLabeledString(s);
		}
		return ls3list;
	}
	
	public List<LabeledSegment> replaceWhiteSpacesClassification(List<LabeledSegment> ls3list)
	{
		String s;
		for(LabeledSegment x : ls3list)
		{
			s=x.getLabeledString();
			s=s.replaceAll("\\s+","\\\\s{1,50}");
			x.setLabeledString(s);
		}
		return ls3list;
	}
	
	public List<LabeledSegment> replacePunctClassification(List<LabeledSegment> ls3list)
	{
		String s;
		for(LabeledSegment x : ls3list)
		{
			s=x.getLabeledString();
			s=s.replaceAll("\\p{Punct}","\\\\p{Punct}");
			x.setLabeledString(s);
		}
		return ls3list;
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
	CategorizerRegEx match;
	String matchedString;
	int startPos;
	int endPos;
	
	public Match(CategorizerRegEx match, String matchedString, int startPos, int endPos) {
		this.match = match;
		this.matchedString = matchedString;
		this.startPos = startPos;
		this.endPos = endPos;
	}
}
