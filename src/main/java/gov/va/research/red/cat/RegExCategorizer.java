package gov.va.research.red.cat;

import gov.va.research.red.CVScore;
import gov.va.research.red.LabeledSegment;
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
	
	public Map<String, Collection<CategorizerRegEx>> findRegexesAndSaveInFile (
			final File vttFile, List<String> yesLabels, List<String> noLabels,
			final String classifierOutputFileName, boolean printScore) throws IOException {
		VTTReader vttr = new VTTReader();
		List<Snippet> snippetsYes = new ArrayList<>();
		for (String yesLabel : yesLabels) {
			snippetsYes.addAll(vttr.extractSnippets(vttFile, yesLabel));
		}
		Map<LabeledSegment, CategorizerRegEx> ls2regExYes = extractRegexClassifications(
				snippetsYes, yesLabels);
		List<Snippet> snippetsNo = new ArrayList<>();
		for (String noLabel : noLabels) {
			snippetsNo.addAll(vttr.extractSnippets(vttFile, noLabel));
		}
		Map<LabeledSegment, CategorizerRegEx> ls2regExNo = extractRegexClassifications(
				snippetsNo, noLabels);
		saveOutputInFile(classifierOutputFileName, ls2regExNo, ls2regExYes);
		if (printScore) {
			CVScore score = testClassifier(snippetsYes, ls2regExYes.values(), null,
					yesLabels);
			System.out.println(score.getEvaluation());
			score = testClassifier(snippetsNo, ls2regExNo.values(), null, noLabels);
			System.out.println(score.getEvaluation());
		}
		Map<String, Collection<CategorizerRegEx>> returnMap = new HashMap<>();
		returnMap.put("yes", ls2regExYes.values());
		returnMap.put("no", ls2regExNo.values());
		return returnMap;
	}
	
	private void saveOutputInFile(final String classifierOutputFileName, Map<LabeledSegment, CategorizerRegEx> ls2regExNo, Map<LabeledSegment, CategorizerRegEx> ls2regExYes) throws IOException {
		if (classifierOutputFileName != null
				&& !classifierOutputFileName.equals("")) {
			File outputFile = new File(classifierOutputFileName);
			if (!outputFile.exists())
				outputFile.createNewFile();
			FileWriter fWriter = new FileWriter(outputFile.getAbsoluteFile(),
					false);
			PrintWriter pWriter = new PrintWriter(fWriter);
			pWriter.println("yes regex");
			for (CategorizerRegEx regEx : ls2regExYes.values()) {
				pWriter.println(regEx.getRegEx());
			}
			pWriter.println("\nno regex");
			for (CategorizerRegEx regEx : ls2regExNo.values()) {
				pWriter.println(regEx.getRegEx());
			}
			pWriter.close();
			fWriter.close();
		}
	}
	

	public Map<LabeledSegment, CategorizerRegEx> extractRegexClassifications(final List<Snippet> snippets, final List<String> labels) {
		if(snippets == null || snippets.isEmpty())
			return null;
		Map<LabeledSegment, CategorizerRegEx> ls2regex = new HashMap<>(snippets.size());
		for(Snippet snippet : snippets) {
			for(String label : labels) {
				LabeledSegment labeledSegment = snippet.getLabeledSegment(label);
				if(labeledSegment != null) {
					ls2regex.put(labeledSegment, new CategorizerRegEx(labeledSegment.getLabeledString()));
				}
			}
		}
		//replace all the punctuations with their regular expressions in the labeled
		//segments
		replacePunctClassification(ls2regex.values());
		//replace all the digits with their regular expressions in the labeled
		//segments
		replaceDigitsClassification(ls2regex.values());
		//replace all the whitespaces with their regular expressions in the labeled
		//segments
		replaceWhiteSpacesClassification(ls2regex.values());
		List<PotentialMatchClassification> potentialList = new ArrayList<>();
		treeReplacementLogicClassification(ls2regex.values(), potentialList);
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
		replacePotentialMatchesClassification(potentialList, ls2regex.values(), snippets, labels);
		return ls2regex;
	}

	public CVScore testClassifier(List<Snippet> testing, Collection<CategorizerRegEx> regularExpressions, CategorizerTester tester, List<String> labels) {
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
	private void replacePotentialMatchesClassification(List<PotentialMatchClassification> potentialMatches, Collection<CategorizerRegEx> regexList, final List<Snippet> snippets, List<String> labels){
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
	
	private void updateMFTMapClassification(CategorizerRegEx regex, Map<String, List<CategorizerRegEx>> freqMap){
		String phrase = "";
		phrase = regex.getRegEx();
		String[] termArray = phrase.split("\\\\s\\{1,50\\}|\\\\p\\{Punct\\}|\\\\d\\+");
		List<CategorizerRegEx> termContainingRegexes = null;
		for(String term : termArray){
			if(!term.equals(" ") && !term.equals("")){
				if(freqMap.containsKey(term))
					termContainingRegexes = freqMap.get(term);
				else
					termContainingRegexes = new ArrayList<CategorizerRegEx>();
				termContainingRegexes.add(regex);
				freqMap.put(term, termContainingRegexes);
			}
		}
	}
	
	private void treeReplacementLogicClassification(Collection<CategorizerRegEx> regexList, Collection<PotentialMatchClassification> potentialList){
		Map<String, List<CategorizerRegEx>> freqMap = new HashMap<String, List<CategorizerRegEx>>();
		for(CategorizerRegEx regex : regexList)
			updateMFTMapClassification(regex, freqMap);
		List<String> termList = new ArrayList<>();
		termList.addAll(freqMap.keySet());
		performPermuationClassification(null, termList, regexList, potentialList);
	}
	
	private void performPermuationClassification(Collection<String> prefixList, List<String> termList, Collection<CategorizerRegEx> regexList, Collection<PotentialMatchClassification> potentialList){
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
	
	private PotentialMatchClassification findMatchClassification(List<String> termList, Collection<CategorizerRegEx> regexList){
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
		for(CategorizerRegEx catRegex : regexList){
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
	
	public Collection<CategorizerRegEx> replaceDigitsClassification(Collection<CategorizerRegEx> regexColl)
	{
		for(CategorizerRegEx x : regexColl)
		{
			x.setRegEx(x.getRegEx().replaceAll("\\d+","\\\\d+"));
		}
		return regexColl;
	}
	
	public Collection<CategorizerRegEx> replaceWhiteSpacesClassification(Collection<CategorizerRegEx> regexColl)
	{
		for(CategorizerRegEx x : regexColl)
		{
			x.setRegEx(x.getRegEx().replaceAll("\\s+","\\\\s{1,50}"));
		}
		return regexColl;
	}
	
	public Collection<CategorizerRegEx> replacePunctClassification(Collection<CategorizerRegEx> regexColl)
	{
		for(CategorizerRegEx x : regexColl)
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
