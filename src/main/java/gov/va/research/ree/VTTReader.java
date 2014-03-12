/*
 *  Copyright 2014 United States Department of Veterans Affairs,
 *		Health Services Research & Development Service
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 */
package gov.va.research.ree;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import gov.nih.nlm.nls.vtt.Model.Markup;
import gov.nih.nlm.nls.vtt.Model.VttDocument;

/**
 * Reads VTT files
 */
public class VTTReader {

	private static final String SNIPPET_TEXT_BEGIN = "SnippetText:";
	private static final String SNIPPET_TEXT_END = "----------------------------------------------------------------------------------";

	/**
	 * Reads a VTT file.
	 * @param vttFile The VTT format file.
	 * @return A VTT document representation of the VTT file.
	 * @throws IOException
	 */
	public VttDocument read(final File vttFile) throws IOException {
		VttDocument vttDoc = new VttDocument();
		boolean valid = vttDoc.ReadFromFile(vttFile);
		if (!valid) {
			throw new IOException("Not a valid VTT file: " + vttFile);
		}
		return vttDoc;
	}

	/**
	 * Extracts labeled segment triplets from a VTT file
	 * @param vttFile The VTT file to extract triplets from.
	 * @param label The label of the segments to extract.
	 * @return Labeled segment triplets (before labeled segment, labeled segment, after labeled segment)
	 * @throws IOException
	 */
	public List<LSTriplet> extractLSTriplets(final File vttFile, final String label) throws IOException {
		VttDocument vttDoc = read(vttFile);
		String docText = vttDoc.GetText();
		List<LSTriplet> ls3list = new ArrayList<>(vttDoc.GetMarkups().GetSize());
		for (Markup markup : vttDoc.GetMarkups().GetMarkups()) {
			
			// Check if the markup has the requested label
			if (label.equals(markup.GetTagName())) {
				
				// Get the labeled text boundaries
				int labeledOffset = markup.GetOffset();
				int labeledLength = markup.GetLength();
				
				// Find the boundaries for the snippet
				int snippetTextBegin = docText.substring(0, labeledOffset).lastIndexOf(SNIPPET_TEXT_BEGIN) + SNIPPET_TEXT_BEGIN.length();
				int snippetTextEnd = docText.indexOf(SNIPPET_TEXT_END, snippetTextBegin);
				
				// Split the snippet into before, labeled segment, and after
				String bls = docText.substring(snippetTextBegin, labeledOffset);
				String ls = docText.substring(labeledOffset, labeledOffset + labeledLength);
				String als = docText.substring(labeledOffset + ls.length(), snippetTextEnd);
				
				LSTriplet ls3 = new LSTriplet((bls == null ? null : bls.trim()), ls, (als == null ? null : als.trim()));
				ls3list.add(ls3);
			}
		}
		return ls3list;
	}
	
	/**
	 * Extracts regular expressions from the snippet triplets.
	 * @param vttFile The VTT file to extract triplets from.
	 * @param label The label of the segments to extract.
	 * @return Regular expressions extracted from the snippets.
	 * @throws IOException
	 */
	public List<LSTriplet> extractRegexExpressions(final File vttFile, final String label) throws IOException{
		List<String> regExpressions = new ArrayList<String>();
		List<LSTriplet> ls3list = extractLSTriplets(vttFile, label);
		//List<LSTriplet> ls3list = new ArrayList<LSTriplet>();
		//ls3list.add(new LSTriplet("brought her the attention of the music industry, winning her the music", "selling artist and was titled 2012", "now debuted three additional studio recorded albums, a best of the albums"));
		//ls3list.add(new LSTriplet("American singer-songwriter, record producer, actor and choreographer and music. His music is", "selling artist and was titled 2012", "He has sold 10 million albums and 58 million singles worldwide as. His best album is"));
		if(ls3list != null && !ls3list.isEmpty()){
			replacePunct(ls3list);
			replaceDigitsLS(ls3list);
			Map<String,List<LSTriplet>> snippetGroups = groupSnippets(ls3list);
			processSnippetGroups(snippetGroups);
			List<LSTriplet> allTriplets = new ArrayList<>();
			for(List<LSTriplet> tripletList : snippetGroups.values()){
				replaceDigitsBLSALS(tripletList);
				replaceWhiteSpaces(tripletList);
				//replacePunctEnd(ls3list);
				//return tripletList;
				allTriplets.addAll(tripletList);
				/*for(LSTriplet triplet : tripletList)
					regExpressions.add(triplet.toString());*/
			}
			return allTriplets;
		}
		return null;
		/*for(String reEx: regExpressions){
			System.out.println(replaceSingleFreqWords(reEx));
		}*/
		//System.out.println(replaceSingleWords2(regExpressions));
		//return regExpressions;
	}
	
	/**
	 * Replaces all the words which have a frequency of one by .*.
	 * @param regExpressions The string on which the method operates.
	 */
	private String replaceSingleFreqWords(String regExpression){
		return regExpression;
	}
	
	/*private String replaceSingleWords2(List<String> regExpressions){
		StringBuilder tempStr = new StringBuilder("");
		for(String regEx: regExpressions){
			String[] words = regEx.split("\\\\s\\{1,10\\}|\\\\p\\{Punct\\}");
			for(String word : words){
				tempStr.append(word);
			}
		}
		return tempStr.toString();
	}*/
	
	/**
	 * Finds out all the groups that have sizes greater than 1. Calls processGroup on those groups.
	 * Keeps on doing so till there are no groups having size greater than one.
	 * @param snippetGroups A hashmap containing the groups. Key is LS and the value is a list of LSTriplet's.
	 * @param blsProcessing If true the method does processing on BLS otherwise on ALS.
	 */
	private void processSnippetGroups(Map<String,List<LSTriplet>> snippetGroups){
		//boolean repeatBLS = true, repeatALS = true;
		//while(repeatALS || repeatBLS){
		//	repeatALS = false;
		//	repeatBLS = false;
			Iterator<List<LSTriplet>> iteratorSnippetGroups = snippetGroups.values().iterator();
			Map<String, List<LSTriplet>> tempGroupMap = new HashMap<String, List<LSTriplet>>();
			while(iteratorSnippetGroups.hasNext()){
				List<LSTriplet> group = iteratorSnippetGroups.next();
				if(group.size() > 1){
					processGroup(group, tempGroupMap, true);
		//			repeatBLS = true;
				}
			}
		//	copyMaps(snippetGroups,tempGroupMap);
			iteratorSnippetGroups = snippetGroups.values().iterator();
			tempGroupMap = new HashMap<String, List<LSTriplet>>();
			while(iteratorSnippetGroups.hasNext()){
				List<LSTriplet> group = iteratorSnippetGroups.next();
				if(group.size() > 1){
					processGroup(group, tempGroupMap, false);
		//			repeatALS = true;
				}
			}
		//	copyMaps(snippetGroups,tempGroupMap);
		//}
	}
	
	/**
	 * takes all the values from one map and puts into the another map.
	 * @param snippetGroups the map to which all the values are inserted.
	 * @param tempGroupMap that map from which all the values are taken and inserted into the other map.
	 */
	private void copyMaps(Map<String, List<LSTriplet>> snippetGroups, Map<String, List<LSTriplet>> tempGroupMap){
		for(Entry<String, List<LSTriplet>> entry : tempGroupMap.entrySet()){
			String key = entry.getKey();
			List<LSTriplet> value = entry.getValue();
			if(snippetGroups.containsKey(key)){
				List<LSTriplet> temp = snippetGroups.get(key);
				temp.addAll(value);
				snippetGroups.put(key, temp);
			}else
				snippetGroups.put(key, value);
		}
	}
	
	/**
	 * For every group it determines the MFT. It then replaces the MFT by the regular expression. Creates a new
	 * group for all the snippets containing the MFT.
	 * @param group The group of LSTriplets on which we are performing the operation.
	 * @param snippetGroups The group of all snippets.
	 */
	private void processGroup(List<LSTriplet> group, Map<String,List<LSTriplet>> tempGroupMap, boolean processBLS){
		Map<String, List<LSTriplet>> freqMap = new HashMap<String, List<LSTriplet>>();
		for(LSTriplet triplet : group)
			updateMFTMap(triplet, processBLS, freqMap);
		/*int maxSize = 1;
		List<LSTriplet> tripletListContainingMFT = null;
		String MFT = "";
		for(Entry<String, List<LSTriplet>> entry : freqMap.entrySet()){
			List<LSTriplet> temptripletListContainingMFT = entry.getValue();
			String tempMFT = entry.getKey();
			if(temptripletListContainingMFT.size() > maxSize){
				maxSize = temptripletListContainingMFT.size();
				MFT = entry.getKey();
				tripletListContainingMFT = temptripletListContainingMFT;
			}
		}
		if(tripletListContainingMFT != null){
			for(LSTriplet triplet : tripletListContainingMFT){
				if(processBLS)
					triplet.getBLS().replaceAll(MFT, "?:"+MFT);
				else
					triplet.getALS().replaceAll(MFT, "?:"+MFT);
				group.remove(triplet);
			}
			tempGroupMap.put(MFT, tripletListContainingMFT);
		}*/
		for(Entry<String, List<LSTriplet>> entry : freqMap.entrySet()){
			List<LSTriplet> value = entry.getValue();
			String key = entry.getKey();
			if(value.size() > 1){
				for(LSTriplet triplet : value){
					if(processBLS)
						triplet.setBLS(triplet.getBLS().replaceAll("\\b"+key+"\\b", "\\\\b"+key+"\\\\b"));//triplet.getBLS().replaceAll("?:"+key, "(?:"+key+")");
					else
						triplet.setALS(triplet.getALS().replaceAll("\\b"+key+"\\b", "\\\\b"+key+"\\\\b"));//triplet.getALS().replaceAll("?:"+key, "(?:"+key+")");
				}
			}else{
				for(LSTriplet triplet : value){
					if(processBLS)
						triplet.setBLS(triplet.getBLS().replaceAll("\\b"+key+"\\b"+"&^((?!\\.\\{1,20\\}).)*$", ".{1,"+key.length()+"}"));//triplet.getBLS().replaceAll("?:"+key, "(?:"+key+")");
					else
						triplet.setALS(triplet.getALS().replaceAll("\\b"+key+"\\b"+"&^((?!\\.\\{1,20\\}).)*$", ".{1,"+key.length()+"}"));//triplet.getALS().replaceAll("?:"+key, "(?:"+key+")");
				}
			}
		}
	}
	
	/**
	 * Creates a map of triplets according to the terms contained inside their BLS/ALS.
	 * @param triplet The triplet on which the processing is being performed.
	 * @param processingBLS Specifies whether the processing is to be performed on BLS/ALS
	 * @param freqMap a map containing a term as the key and a list of triplets containing that term as the value.
	 */
	private void updateMFTMap(LSTriplet triplet, boolean processingBLS, Map<String, List<LSTriplet>> freqMap){
		String phrase = "";
		if(processingBLS)
			phrase = triplet.getBLS();
		else
			phrase = triplet.getALS();
		//String[] termArray = phrase.replaceAll("[^a-zA-Z ]", "").split("\\s+|\\n");
		String[] termArray = phrase.split("\\s+|\\n|\\\\p\\{Punct\\}");
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
	 * Groups snippets by LS exact match
	 * @param ls3list The list of snippets that the method iterates on and groups them.
	 * @return A hashmap containing the groups. Key is LS and the value is a list of LSTriplet's.
	 */
	private Map<String,List<LSTriplet>> groupSnippets(List<LSTriplet> ls3list){
		Map<String,List<LSTriplet>> snippetGroups = new HashMap<String, List<LSTriplet>>();
		for(LSTriplet triplet : ls3list){
			List<LSTriplet> group = snippetGroups.get(triplet.getLS());
			if(group == null)
				group = new ArrayList<LSTriplet>();
			group.add(triplet);
			snippetGroups.put(triplet.getLS(), group);
		}
		return snippetGroups;
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
				s=s.replaceAll("\\d+&^((?!\\.\\{1,20\\}).)*$","\\\\d+");
				x.setBLS(s);
				s=x.getALS();
				s=s.replaceAll("\\d+&^((?!\\.\\{1,20\\}).)*$","\\\\d+");
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
	
	/*public List<LSTriplet> replacePunct(List<LSTriplet> ls3list)
	{
		String s;
		for(LSTriplet x : ls3list)
		{
			s=x.getLS();
			s=s.replaceAll("\\p{Punct}&&[^\\\\]&&[^\\\\s{1,10}]","\\\\p{Punct}");
			x.setLS(s);
			s=x.getBLS();
			s=s.replaceAll("[\\p{Punct}&&[^\\\\]&&[^\bs\\{1,10\\}\b]]","\\\\p{Punct}");
			x.setBLS(s);
			s=x.getALS();
			s=s.replaceAll("\\p{Punct}&&[^\\\\]&&[^\\\\s{1,10}]","\\\\p{Punct}");
			x.setALS(s);
		}
		return ls3list;
	}*/
	
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
	
	/*public List<LSTriplet> replacePunctEnd(List<LSTriplet> ls3list)
	{
		String s;
		for(LSTriplet x : ls3list)
		{
			s=x.getLS();
			s=s.replace("\\s{1,10}\\p{Punct}","\\p{Punct}");
			x.setLS(s);
			s=x.getBLS();
			s=s.replace("\\s{1,10}\\p{Punct}","\\p{Punct}");
			x.setBLS(s);
			s=x.getALS();
			s=s.replace("\\s{1,10}\\p{Punct}","\\p{Punct}");
			x.setALS(s);
		}
		return ls3list;
	}*/
	
	public List<LSTriplet> removeDuplicates(List<LSTriplet> ls3list)
	{
				
		Set<LSTriplet> listToSet = new HashSet<LSTriplet>(ls3list);
		List<LSTriplet> ls3listWithoutDuplicates = new ArrayList<LSTriplet>(listToSet);
		return ls3listWithoutDuplicates;
	}
	
}
