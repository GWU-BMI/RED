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

import gov.nih.nlm.nls.vtt.Model.Markup;
import gov.nih.nlm.nls.vtt.Model.VttDocument;

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
		List<LSTriplet> ls3list = extractLSTriplets(vttFile, label);
		
		//check if there are any snippets for that label
		if(ls3list != null && !ls3list.isEmpty()){
			//replace all the punctuation marks with their regular expressions.
			replacePunct(ls3list);
			//replace all the digits in the LS with their regular expressions.
			replaceDigitsLS(ls3list);
			//group the snippets according to LS exact match.
			Map<String,List<LSTriplet>> snippetGroups = groupSnippets(ls3list);
			//for each group find the frequent terms and replace the frequent and
			//less frequent terms with appropriate regular expressions.
			processSnippetGroups(snippetGroups);
			List<LSTriplet> allTriplets = new ArrayList<>();
			for(List<LSTriplet> tripletList : snippetGroups.values()){
				//replace the digits in BLS and ALS with their regular expressions.
				replaceDigitsBLSALS(tripletList);
				//replace the white spaces with regular expressions.
				replaceWhiteSpaces(tripletList);
				//add all the triplets containing the regular expressions
				//to a global list.
				allTriplets.addAll(tripletList);
			}
			return allTriplets;
		}
		return null;
	}
	
	/**
	 * Finds out all the groups that have sizes greater than 1. Calls processGroup on those groups.
	 * @param snippetGroups A hashmap containing the groups. Key is LS and the value is a list of LSTriplet's.
	 */
	private void processSnippetGroups(Map<String,List<LSTriplet>> snippetGroups){
		Iterator<List<LSTriplet>> iteratorSnippetGroups = snippetGroups.values().iterator();
		Map<String, List<LSTriplet>> tempGroupMap = new HashMap<String, List<LSTriplet>>();
		while(iteratorSnippetGroups.hasNext()){
			List<LSTriplet> group = iteratorSnippetGroups.next();
			if(group.size() > 1){
				processGroup(group, tempGroupMap, true);
			}
		}
		
		//repeat the above steps for ALS.
		iteratorSnippetGroups = snippetGroups.values().iterator();
		tempGroupMap = new HashMap<String, List<LSTriplet>>();
		while(iteratorSnippetGroups.hasNext()){
			List<LSTriplet> group = iteratorSnippetGroups.next();
			if(group.size() > 1){
				processGroup(group, tempGroupMap, false);
			}
		}
	}
	
	/**
	 * For every group it determines the frequent terms. It then replaces the frequent terms by the regular expression \bfrequent term\b.
	 * It replaces the terms that are not frequent by .*{1,frequent term's length}
	 * @param group The group of LSTriplets on which we are performing the operation.
	 * @param snippetGroups The group of all snippets.
	 */
	private void processGroup(List<LSTriplet> group, Map<String,List<LSTriplet>> tempGroupMap, boolean processBLS){
		Map<String, List<LSTriplet>> freqMap = new HashMap<String, List<LSTriplet>>();
		for(LSTriplet triplet : group)
			updateMFTMap(triplet, processBLS, freqMap);
		for(Entry<String, List<LSTriplet>> entry : freqMap.entrySet()){
			List<LSTriplet> value = entry.getValue();
			String key = entry.getKey();
			if(value.size() > 1){
				for(LSTriplet triplet : value){
					if(processBLS)
						triplet.setBLS(triplet.getBLS().replaceAll("\\b"+key+"\\b"+"&^((?!\\.\\{1,20\\}).)*$", "\\\\b"+key+"\\\\b"));//triplet.getBLS().replaceAll("?:"+key, "(?:"+key+")");
					else
						triplet.setALS(triplet.getALS().replaceAll("\\b"+key+"\\b"+"&^((?!\\.\\{1,20\\}).)*$", "\\\\b"+key+"\\\\b"));//triplet.getALS().replaceAll("?:"+key, "(?:"+key+")");
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
	 * Creates a frequency map of terms contained inside the BLS/ALS.
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
	
	public List<LSTriplet> removeDuplicates(List<LSTriplet> ls3list)
	{
				
		Set<LSTriplet> listToSet = new HashSet<LSTriplet>(ls3list);
		List<LSTriplet> ls3listWithoutDuplicates = new ArrayList<LSTriplet>(listToSet);
		return ls3listWithoutDuplicates;
	}
	
}
