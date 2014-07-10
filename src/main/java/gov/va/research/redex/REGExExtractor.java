package gov.va.research.redex;

import gov.va.research.red.CVScore;
import gov.va.research.red.VTTReader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class REGExExtractor {
	
	private static final Logger LOG = LoggerFactory.getLogger(VTTReader.class);
	private LSExtractor leExt = new LSExtractor(null);
	private CrossValidate cv = new CrossValidate();
	
	public List<LSTriplet> extractRegexExpressions(final List<Snippet> snippets, final String label, final String outputFileName) throws IOException {
		List<LSTriplet> ls3list = new ArrayList<>(snippets.size());
		for (Snippet snippet : snippets) {
			for (LabeledSegment ls : snippet.getLabeledSegments()) {
				if (label.equals(ls.getLabel())) {
					ls3list.add(LSTriplet.valueOf(snippet.getText(), ls));
				}
			}
		}
		if(ls3list != null && !ls3list.isEmpty()){
			//replace all the punctuation marks with their regular expressions.
			replacePunct(ls3list);
			//replace all the digits in the LS with their regular expressions.
			replaceDigitsLS(ls3list);
			//replace the digits in BLS and ALS with their regular expressions.
			replaceDigitsBLSALS(ls3list);
			//replace the white spaces with regular expressions.
			replaceWhiteSpaces(ls3list);
			
			Map<LSTriplet, CrossValidate.TripletMatches> tripsWithFP = CrossValidate.findTripletsWithFalsePositives(ls3list, snippets, label);
			if (tripsWithFP != null && tripsWithFP.size() > 0) {
				LOG.warn("False positive regexes found before trimming");
				for (Map.Entry<LSTriplet, CrossValidate.TripletMatches> twfp : tripsWithFP.entrySet()) {
					LOG.warn("RegEx: " + twfp.getKey().toStringRegEx());
					LOG.warn("Correct matches:");
					for (Snippet correct : twfp.getValue().getCorrect()) {
						LOG.warn("<correct value='" + correct.getLabeledStrings() + "'>\n" + correct.getText() + "\n</correct>");
					}
					LOG.warn("False positive matches:");
					for (Snippet fp : twfp.getValue().getFalsePositive()) {
						Pattern p = Pattern.compile(twfp.getKey().toStringRegEx());
						Matcher m = p.matcher(fp.getText());
						m.find();
						LOG.warn("<fp actual='" + fp.getLabeledStrings() + "' predicted='" + m.group(1) + "'>\n" + fp.getText() + "\n</fp>");
					}
				}
			}
			
			//check if we can remove the first regex from bls. Keep on repeating
			//the process till we can't remove any regex's from the bls's.
			trimRegEx(snippets, ls3list);
			leExt.setRegExpressions(ls3list);
			CVScore scoreAfterTrimming = cv.testExtractor(snippets, leExt);
			
			
			// remove reduntant expressions
			List<LSTriplet> testList = new ArrayList<LSTriplet>(ls3list);
			int fnToTestAgainst = scoreAfterTrimming.getFn();
			CVScore tempScore = null;
			for(LSTriplet triplet : ls3list){
				testList.remove(triplet);
				leExt.setRegExpressions(testList);
				tempScore = cv.testExtractor(snippets, leExt);
				if(tempScore.getFn() < fnToTestAgainst)
					fnToTestAgainst = tempScore.getFn();
				else
					testList.add(triplet);
			}
			ls3list = testList;
			
			// the tree replacement algorithm
			List<PotentialMatch> potentialListBLS = new ArrayList<>();
			List<PotentialMatch> potentialListALS = new ArrayList<>();
			treeReplacementLogic(ls3list, potentialListBLS, potentialListALS);
			Comparator<PotentialMatch> comp = new Comparator<PotentialMatch>() {

				@Override
				public int compare(PotentialMatch o1, PotentialMatch o2) {
					if(o1.terms.size() < o2.terms.size())
						return 1;
					if(o1.terms.size() > o2.terms.size())
						return -1;
					return 0;
				}
				
			};
			Collections.sort(potentialListBLS, comp);
			Collections.sort(potentialListALS, comp);
			replacePotentialMatches(potentialListBLS, ls3list, true, snippets);
			replacePotentialMatches(potentialListALS, ls3list, false, snippets);
			if(outputFileName != null && !outputFileName.equals("")){
				File file = new File(outputFileName);
				if(!file.exists())
					file.createNewFile();
				FileWriter fWriter = new FileWriter(file,false);
				PrintWriter pWriter = new PrintWriter(fWriter);
				for(LSTriplet triplet : ls3list){
					pWriter.println(triplet.toString());
				}
				pWriter.close();
				fWriter.close();
			}
			return ls3list;
		}
		return null;
	}
	
	private void replacePotentialMatches(List<PotentialMatch> potentialMatches, List<LSTriplet> ls3List, boolean processBLS, final List<Snippet> snippets){
		for(PotentialMatch match : potentialMatches){
			if(match.count == 1){
				for(LSTriplet triplet : match.matches){
					String key = match.toString();
					if(processBLS){
						String bls = triplet.getBLS();
						//if(!key.equals("S")){
							triplet.setBLS(triplet.getBLS().replaceAll("\\b(?<!\\\\)"+key+"\\b", "\\\\S{1,"+key.length()+"}"));//triplet.getBLS().replaceAll("?:"+key, "(?:"+key+")");
							List<LSTriplet> regEx = new ArrayList<LSTriplet>();
							regEx.add(triplet);
							leExt.setRegExpressions(regEx);
							//CVScore cvScore = cv.testExtractor(snippets, leExt);
							if(cv.checkForFalsePositives(snippets, leExt))
								triplet.setBLS(bls);
					}else{
						String als = triplet.getALS();
						//if(!key.equals("S")){
							triplet.setALS(triplet.getALS().replaceAll("\\b(?<!\\\\)"+key+"\\b", "\\\\S{1,"+key.length()+"}"));//triplet.getALS().replaceAll("?:"+key, "(?:"+key+")");
							List<LSTriplet> regEx = new ArrayList<LSTriplet>();
							regEx.add(triplet);
							leExt.setRegExpressions(regEx);
							//CVScore cvScore = cv.testExtractor(snippets, leExt);
							if(cv.checkForFalsePositives(snippets, leExt))
								triplet.setALS(als);
					}
				}
			}
		}
	}
	
	public List<LSTriplet> replacePunct(List<LSTriplet> ls3list)
	{
		String s;
		for(LSTriplet x : ls3list)
		{
			s=x.getLS();
			s=s.replaceAll("\\p{Punct}","\\\\p{Punct}");
			x.setLS(s);
			s=x.getBLS();
			s=s.replaceAll("\\p{Punct}","\\\\p{Punct}");
			x.setBLS(s);
			s=x.getALS();
			s=s.replaceAll("\\p{Punct}","\\\\p{Punct}");
			x.setALS(s);
		}
		return ls3list;
	}
	
	//replace digits with '\d+'
	public List<LSTriplet> replaceDigitsLS(List<LSTriplet> ls3list)
	{
		String s;
		for(LSTriplet x : ls3list)
		{
			s=x.getLS();
			s=s.replaceAll("\\d+","\\\\d+");
			x.setLS(s);
		}
		return ls3list;
	}
		
	//replace digits with '\d+'
	public List<LSTriplet> replaceDigitsBLSALS(List<LSTriplet> ls3list)
	{
		String s;
		for(LSTriplet x : ls3list)
		{
			s=x.getBLS();
			s=s.replaceAll("\\d+","\\\\d+");//&^((?!\\.\\{1,20\\}).)*$
			x.setBLS(s);
			s=x.getALS();
			s=s.replaceAll("\\d+","\\\\d+");//&^((?!\\.\\{1,20\\}).)*$
			x.setALS(s);
		}
		return ls3list;
	}
	
	//replace white spaces with 's{1,10}'
	public List<LSTriplet> replaceWhiteSpaces(List<LSTriplet> ls3list)
	{
		String s;
		for(LSTriplet x : ls3list)
		{
			s=x.getBLS();
			s=s.replaceAll("\\s+","\\\\s{1,50}");
			x.setBLS(s);
			s=x.getLS();
			s=s.replaceAll("\\s+","\\\\s{1,50}");
			x.setLS(s);
			s=x.getALS();
			s=s.replaceAll("\\s+","\\\\s{1,50}");
			x.setALS(s);
		}
		return ls3list;
	}
	
	private void trimRegEx(final List<Snippet> snippets,
			List<LSTriplet> ls3list) {
		Map<LSTriplet,Boolean> prevTrimOpResultBLS = new HashMap<LSTriplet,Boolean>();
		for(LSTriplet triplet : ls3list){
			prevTrimOpResultBLS.put(triplet, true);
		}
		Map<LSTriplet,Boolean> prevTrimOpResultALS = new HashMap<LSTriplet,Boolean>();
		for(LSTriplet triplet : ls3list){
			prevTrimOpResultALS.put(triplet, true);
		}
		String bls=null,als=null;
		List<LSTriplet> regExList = new ArrayList<LSTriplet>();
		regExList.addAll(ls3list);
		leExt.setRegExpressions(regExList);
		//int count = 1;
		while(true){
			boolean processedBLSorALS = true;
			for(LSTriplet triplet : ls3list){
				if(prevTrimOpResultBLS.get(triplet) && ((triplet.getBLS().length() >= triplet.getALS().length()) || !prevTrimOpResultALS.get(triplet))){
					processedBLSorALS = false;
					bls = triplet.getBLS();
					if(bls.equals("") || bls == null){
						prevTrimOpResultBLS.put(triplet, false);
						//System.out.println("trim fail special bls "+count++);
					}else{
						char firstChar = bls.charAt(0);
						String blsWithoutFirstRegex = null;
						if(firstChar != '\\'){
							if(bls.indexOf("\\") != -1)
								blsWithoutFirstRegex = bls.substring(bls.indexOf("\\"),bls.length());
							else
								blsWithoutFirstRegex = "";
						}else{
							int index = 1;
							while(index < bls.length()){
								char temp = bls.charAt(index++);
								if(temp == '+' || temp == '}')
									break;
							}
							if(index == bls.length())
								blsWithoutFirstRegex = "";
							else
								blsWithoutFirstRegex = bls.substring(index, bls.length());
						}
						
						
						triplet.setBLS(blsWithoutFirstRegex);
						regExList = new ArrayList<LSTriplet>();
						regExList.add(triplet);
						leExt.setRegExpressions(regExList);
						if(cv.checkForFalsePositives(snippets, leExt)){
							triplet.setBLS(bls);
							prevTrimOpResultBLS.put(triplet, false);
						}else{
							prevTrimOpResultBLS.put(triplet, true);
						}
					}
				}
				if(prevTrimOpResultALS.get(triplet) && ((triplet.getBLS().length() <= triplet.getALS().length()) ||  !prevTrimOpResultBLS.get(triplet))){
					processedBLSorALS = false;
					als = triplet.getALS();
					if(als.equals("") || als == null){
						prevTrimOpResultALS.put(triplet, false);
					}else{
						String alsWithoutLastRegex = null;
						if(als.lastIndexOf("\\") == -1)
							alsWithoutLastRegex = "";
						else{
							int lastIndex = als.lastIndexOf("\\");
							int index = lastIndex;
							index++;
							while(index < als.length()){
								char temp = als.charAt(index++);
								if(temp == '+' || temp == '}')
									break;
							}
							if(index == als.length()){
								if(lastIndex == 0)
									alsWithoutLastRegex = "";
								else
									alsWithoutLastRegex = als.substring(0,lastIndex);
							}else
								alsWithoutLastRegex = als.substring(0, index);
						}
						
						
						triplet.setALS(alsWithoutLastRegex);
						regExList = new ArrayList<LSTriplet>();
						regExList.add(triplet);
						leExt.setRegExpressions(regExList);
						if(cv.checkForFalsePositives(snippets, leExt)){
							triplet.setALS(als);
							prevTrimOpResultALS.put(triplet, false);
						}else{
							prevTrimOpResultALS.put(triplet, true);
						}
					}
				}
			}
			if(processedBLSorALS)
				break;
		}
	}
	
	/**
	 * For every group it determines the frequent terms. It then replaces the frequent terms by the regular expression \bfrequent term\b.
	 * It replaces the terms that are not frequent by .*{1,frequent term's length}
	 * @param group The group of LSTriplets on which we are performing the operation.
	 * @param snippetGroups The group of all snippets.
	 */
	private void processGroupTrimVersion(List<LSTriplet> group, Map<String,List<LSTriplet>> tempGroupMap, boolean processBLS, final List<Snippet> snippets){
		Map<String, List<LSTriplet>> freqMap = new HashMap<String, List<LSTriplet>>();
		for(LSTriplet triplet : group)
			updateMFTMapTrimVersion(triplet, processBLS, freqMap);
		List<Entry<String, List<LSTriplet>>> entryList = new ArrayList<Entry<String, List<LSTriplet>>>();
		for(Entry<String, List<LSTriplet>> entry : freqMap.entrySet()){
			if(entryList.isEmpty())
				entryList.add(entry);
			else{
				int i=0;
				for(i=0;i<entryList.size();i++){
					Entry<String, List<LSTriplet>> entryListElem = entryList.get(i);
					if(entry.getValue().size() < entryListElem.getValue().size())
						break;
				}
				entryList.add(i, entry);
			}
		}
		for(Entry<String, List<LSTriplet>> entry : entryList){
			List<LSTriplet> value = entry.getValue();
			String key = entry.getKey();
			String bls=null,als=null;
			for(LSTriplet triplet : value){
				if(processBLS){
					bls = triplet.getBLS();
					//if(!key.equals("S")){
						triplet.setBLS(triplet.getBLS().replaceAll("\\b(?<!\\\\)"+key+"\\b", "\\\\S{1,"+key.length()+"}"));//triplet.getBLS().replaceAll("?:"+key, "(?:"+key+")");
						List<LSTriplet> regEx = new ArrayList<LSTriplet>();
						regEx.add(triplet);
						leExt.setRegExpressions(regEx);
						//CVScore cvScore = cv.testExtractor(snippets, leExt);
						if(cv.checkForFalsePositives(snippets, leExt))
							triplet.setBLS(bls);
				}else{
					als = triplet.getALS();
					//if(!key.equals("S")){
						triplet.setALS(triplet.getALS().replaceAll("\\b(?<!\\\\)"+key+"\\b", "\\\\S{1,"+key.length()+"}"));//triplet.getALS().replaceAll("?:"+key, "(?:"+key+")");
						List<LSTriplet> regEx = new ArrayList<LSTriplet>();
						regEx.add(triplet);
						leExt.setRegExpressions(regEx);
						//CVScore cvScore = cv.testExtractor(snippets, leExt);
						if(cv.checkForFalsePositives(snippets, leExt))
							triplet.setALS(als);
				}
			}
		}
	}
	
	/**
	 * Finds out all the groups that have sizes greater than 1. Calls processGroup on those groups.
	 * @param tripletGroups A hashmap containing the groups. Key is LS and the value is a list of LSTriplet's.
	 */
	private void processSnippetGroupsTrimVersion(Map<String,List<LSTriplet>> tripletGroups, final List<Snippet> snippets){
		Iterator<List<LSTriplet>> iteratorSnippetGroups = tripletGroups.values().iterator();
		Map<String, List<LSTriplet>> tempGroupMap = new HashMap<String, List<LSTriplet>>();
		while(iteratorSnippetGroups.hasNext()){
			List<LSTriplet> group = iteratorSnippetGroups.next();
			//if(group.size() > 1){
			processGroupTrimVersion(group, tempGroupMap, true,snippets);
			//}
		}
		
		//repeat the above steps for ALS.
		iteratorSnippetGroups = tripletGroups.values().iterator();
		tempGroupMap = new HashMap<String, List<LSTriplet>>();
		while(iteratorSnippetGroups.hasNext()){
			List<LSTriplet> group = iteratorSnippetGroups.next();
			//if(group.size() > 1){
			processGroupTrimVersion(group, tempGroupMap, false,snippets);
			//}
		}
	}
	
	/**
	 * Creates a frequency map of terms contained inside the BLS/ALS.
	 * @param triplet The triplet on which the processing is being performed.
	 * @param processingBLS Specifies whether the processing is to be performed on BLS/ALS
	 * @param freqMap a map containing a term as the key and a list of triplets containing that term as the value.
	 */
	private void updateMFTMapTrimVersion(LSTriplet triplet, boolean processingBLS, Map<String, List<LSTriplet>> freqMap){
		String phrase = "";
		if(processingBLS)
			phrase = triplet.getBLS();
		else
			phrase = triplet.getALS();
		String[] termArray = phrase.split("\\\\s\\{1,50\\}|\\\\p\\{Punct\\}|\\\\d\\+");
		List<LSTriplet> termContainingTriplets = null;
		for(String term : termArray){
			if(!term.equals(" ") && !term.equals("")){
				if(freqMap.containsKey(term))
					termContainingTriplets = freqMap.get(term);
				else
					termContainingTriplets = new ArrayList<LSTriplet>();
				termContainingTriplets.add(triplet);
				freqMap.put(term, termContainingTriplets);
			}
		}
	}
	
	/**
	 * checks if any permutation of the terms matches against any bls or als
	 * if it does it records it,
	 * @param prefixList
	 * @param termList
	 * @param ls3List
	 * @param processBLS
	 * @param potentialList
	 */
	private void performPermuation(List<String> prefixList, List<String> termList, List<LSTriplet> ls3List, boolean processBLS, List<PotentialMatch> potentialList){
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
				PotentialMatch match = findMatch(tempPrefixList, ls3List, processBLS);
				if(match.count > 0){
					potentialList.add(match);
					performPermuation(tempPrefixList, tempTermList, ls3List, processBLS, potentialList);
				}
			}
		}
	}
	
	/**
	 * checks if the permutation calculated earlier matches against
	 * any of the bls or als.
	 * @param termList
	 * @param ls3List
	 * @param processBLS
	 * @return
	 */
	private PotentialMatch findMatch(List<String> termList, List<LSTriplet> ls3List, boolean processBLS){
		StringBuilder concatString = new StringBuilder("");
		int count = 0;
		List<LSTriplet> triplets = new ArrayList<>();
		for(int i=0;i<termList.size();i++){
			if(i==termList.size()-1)
				concatString.append(termList.get(i));
			else
				concatString.append(termList.get(i)+"\\s{1,50}");
		}
		for(LSTriplet triplet : ls3List){
			String matchAgainst = null;
			if(processBLS)
				matchAgainst = triplet.getBLS();
			else
				matchAgainst = triplet.getALS();
			if(matchAgainst.contains(concatString)){
				count++;
				triplets.add(triplet);
			}
		}
		PotentialMatch match = new PotentialMatch(termList, count, triplets);
		return match;
	}
	
	/**
	 * finds out all the terms in all the bls and als. It then checks to see
	 * if any permutation of the terms matches against any bls or als. If matches
	 * it records its in a list of potential matches.
	 * @param ls3List the original list of triplets
	 * @param potentialListBLS records the list of potential matches for bls
	 * @param potentialListALS records the list of potential matches for als
	 */
	private void treeReplacementLogic(List<LSTriplet> ls3List, List<PotentialMatch> potentialListBLS, List<PotentialMatch> potentialListALS){
		Map<String, List<LSTriplet>> freqMap = new HashMap<String, List<LSTriplet>>();
		for(LSTriplet triplet : ls3List)
			updateMFTMapTrimVersion(triplet, true, freqMap);
		List<String> termList = new ArrayList<>();
		termList.addAll(freqMap.keySet());
		performPermuation(null, termList, ls3List, true, potentialListBLS);
		
		freqMap = new HashMap<String, List<LSTriplet>>();
		for(LSTriplet triplet : ls3List)
			updateMFTMapTrimVersion(triplet, false, freqMap);
		termList = new ArrayList<>();
		termList.addAll(freqMap.keySet());
		performPermuation(null, termList, ls3List, false, potentialListALS);
	}
	
	
}

class PotentialMatch {
	List<String> terms;
	int count;
	List<LSTriplet> matches;
	
	public PotentialMatch(List<String> terms, int count, List<LSTriplet> matches) {
		this.terms = terms;
		this.count = count;
		this.matches = matches;
	}
	
	@Override
	public String toString() {
		if(terms == null || terms.isEmpty())
			return " The match is empty";
		StringBuilder temp = new StringBuilder();
		for(int i=0;i<terms.size();i++){
			if(i==terms.size()-1)
				temp.append(terms.get(i));
			else
				temp.append(terms.get(i)+"\\s{1,50}");
		}
		return temp.toString();
	}
}
