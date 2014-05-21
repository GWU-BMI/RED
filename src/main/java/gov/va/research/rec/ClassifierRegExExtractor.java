package gov.va.research.rec;

import gov.va.research.red.CVScore;
import gov.va.research.ree.CrossValidate;
import gov.va.research.ree.LabeledSegment;
import gov.va.research.ree.Snippet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassifierRegExExtractor {
	
	private CrossValidate cv = new CrossValidate();
	private Map<String, Pattern> patternCache = new HashMap<>();
	
	public List<ClassifierRegEx> extracteRegexClassifications(final List<Snippet> snippets, final String label) {
		if(snippets == null || snippets.isEmpty())
			return null;
		List<LabeledSegment> listOfLabeledSegments = new ArrayList<>(snippets.size());
		for(Snippet snippet : snippets){
			LabeledSegment labeledSegment = snippet.getLabeledSegment(label);
			if(labeledSegment != null)
				listOfLabeledSegments.add(labeledSegment);
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
		List<ClassifierRegEx> listClassifierRegEx = new ArrayList<>();
		for(LabeledSegment lsSeg : listOfLabeledSegments){
			listClassifierRegEx.add(new ClassifierRegEx(lsSeg.getLabeledString()));
		}
		treeReplacementLogicClassification(listClassifierRegEx, potentialList);
		Comparator<PotentialMatchClassification> comp = new Comparator<PotentialMatchClassification>() {

			@Override
			public int compare(PotentialMatchClassification o1, PotentialMatchClassification o2) {
				if(o1.terms.size() < o2.terms.size())
					return 1;
				if(o1.terms.size() > o2.terms.size())
					return -1;
				return 0;
			}
			
		};
		Collections.sort(potentialList, comp);
		replacePotentialMatchesClassification(potentialList, listClassifierRegEx, snippets, label);
		return listClassifierRegEx;
	}
	
	private void replacePotentialMatchesClassification(List<PotentialMatchClassification> potentialMatches, List<ClassifierRegEx> ls3List, final List<Snippet> snippets, String label){
		for(PotentialMatchClassification match : potentialMatches){
			if(match.count == 1){
				for(Match triplet : match.matches){
					//String key = match.toString();
					String bls = triplet.match.getRegEx();
					if(bls.contains(triplet.matchedString)){
						//if(!key.equals("S")){
						triplet.match.setRegEx(bls.replace(triplet.matchedString, "[\\s\\S\\d\\p{Punct}]+"));//triplet.getBLS().replaceAll("?:"+key, "(?:"+key+")");
						List<ClassifierRegEx> regEx = new ArrayList<ClassifierRegEx>();
						regEx.add(triplet.match);
						CVScore cvScore = cv.testClassifier(snippets, regEx, null, label);
						if(cvScore.getFp() != 0)
							triplet.match.setRegEx(bls);
					}
				}
			}
		}
	}
	
	private void updateMFTMapClassification(ClassifierRegEx triplet, Map<String, List<ClassifierRegEx>> freqMap){
		String phrase = "";
		phrase = triplet.getRegEx();
		String[] termArray = phrase.split("\\\\s\\{1,50\\}|\\\\p\\{Punct\\}|\\\\d\\+");
		List<ClassifierRegEx> termContainingTriplets = null;
		for(String term : termArray){
			if(!term.equals(" ") && !term.equals("")){
				if(freqMap.containsKey(term))
					termContainingTriplets = freqMap.get(term);
				else
					termContainingTriplets = new ArrayList<ClassifierRegEx>();
				termContainingTriplets.add(triplet);
				freqMap.put(term, termContainingTriplets);
			}
		}
	}
	
	private void treeReplacementLogicClassification(List<ClassifierRegEx> ls3List, List<PotentialMatchClassification> potentialList){
		Map<String, List<ClassifierRegEx>> freqMap = new HashMap<String, List<ClassifierRegEx>>();
		for(ClassifierRegEx triplet : ls3List)
			updateMFTMapClassification(triplet, freqMap);
		List<String> termList = new ArrayList<>();
		termList.addAll(freqMap.keySet());
		performPermuationClassification(null, termList, ls3List, potentialList);
	}
	
	private void performPermuationClassification(List<String> prefixList, List<String> termList, List<ClassifierRegEx> ls3List, List<PotentialMatchClassification> potentialList){
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
	
	private PotentialMatchClassification findMatchClassification(List<String> termList, List<ClassifierRegEx> ls3List){
		StringBuilder concatString = new StringBuilder("");
		int count = 0;
		List<Match> triplets = new ArrayList<>();
		for(int i=0;i<termList.size();i++){
			if(i==termList.size()-1)
				concatString.append(termList.get(i));
			else
				concatString.append(termList.get(i)+"(\\\\s\\{1,50\\}|\\\\p\\{Punct\\}|\\\\d)+");
		}
		Pattern pattern = null;
		String regex = concatString.toString();
		if(patternCache.containsKey(regex)){
			pattern = patternCache.get(regex);
		}else{
			pattern = Pattern.compile(regex);
		}
		for(ClassifierRegEx triplet : ls3List){
			String matchAgainst = null;
			matchAgainst = triplet.getRegEx();
			Matcher matcher = pattern.matcher(matchAgainst);
			if(matcher.find()){
				count++;
				String matchedString = matchAgainst.substring(matcher.start(), matcher.end());
				triplets.add(new Match(triplet, matchedString));
			}
		}
		PotentialMatchClassification match = new PotentialMatchClassification(termList, count, triplets);
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
	
	public PotentialMatchClassification(List<String> terms, int count, List<Match> matches) {
		this.terms = terms;
		this.count = count;
		this.matches = matches;
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
	ClassifierRegEx match;
	String matchedString;
	
	public Match(ClassifierRegEx match, String matchedString) {
		this.match = match;
		this.matchedString = matchedString;
	}
}
