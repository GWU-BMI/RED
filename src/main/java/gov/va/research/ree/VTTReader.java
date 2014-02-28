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
	public List<String> extractRegexExpressions(final File vttFile, final String label) throws IOException{
		List<String> regExpressions = new ArrayList<String>();
		List<LSTriplet> ls3list = extractLSTriplets(vttFile, label);
		replaceDigits(ls3list);
		replaceWhiteSpaces(ls3list);
		Map<String,List<LSTriplet>> snippetGroups = groupSnippets(ls3list);
		processSnippetGroups(snippetGroups);
		return regExpressions;
	}
	
	/**
	 * Finds out all the groups that have sizes greater than 1. Calls processGroup on those groups.
	 * @param snippetGroups A hashmap containing the groups. Key is LS and the value is a list of LSTriplet's.
	 * @param blsProcessing If true the method does processing on BLS otherwise on ALS.
	 */
	private void processSnippetGroups(Map<String,List<LSTriplet>> snippetGroups){
		Iterator<List<LSTriplet>> iteratorSnippetGroups = snippetGroups.values().iterator();
		while(iteratorSnippetGroups.hasNext()){
			List<LSTriplet> group = iteratorSnippetGroups.next();
			if(group.size() > 1){
				processGroup(group);
			}
		}
	}
	
	private void processGroup(List<LSTriplet> group){
		
	}
	
	/**
	 * Finds the most frequent term in a phrase. Before doing so it removes all the punctuation marks in a string.
	 * @param phrase The string in which the MFT has to be found out.
	 * @return A the most frequent term if its freq is > 1 or else null.
	 */
	private String getMFT(String phrase){
		String[] termArray = phrase.replaceAll("[^a-zA-Z ]", "").split("\\s+");
		Map<String, Integer> freqMap = new HashMap<String,Integer>();
		for(String term : termArray){
			if(freqMap.containsKey(term)){
				int count = freqMap.get(term);
				freqMap.put(term, ++count);
			}else
				freqMap.put(term, 1);
		}
		String mftStr = null;
		int mftCount = -1;
		for(String term : freqMap.keySet()){
			int freq = freqMap.get(term);
			if(freq > mftCount){
				mftCount = freq;
				mftStr = term;
			}
		}
		if(mftCount > 1)
			return mftStr;
		return null;
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
	public List<LSTriplet> replaceDigits(List<LSTriplet> ls3list)
	{
		String s;
		for(LSTriplet x : ls3list)
		{
			s=x.getLS();
			s=s.replaceAll("\\d+.*","\\d+");
			x.setLS(s);
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
			s=s.replaceAll("\\s+","\\s{1,10}");
			x.setBLS(s);
			s=x.getLS();
			s=s.replaceAll("\\s+","");
			x.setLS(s);
			s=x.getALS();
			s=s.replaceAll("\\s+"," ");
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
			s=s.replaceAll("\\p{Punct}","\\p{Punct}");
			x.setLS(s);
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
