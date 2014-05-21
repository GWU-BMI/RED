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
package gov.va.research.red;

import gov.nih.nlm.nls.vtt.Model.Markup;
import gov.nih.nlm.nls.vtt.Model.VttDocument;
import gov.va.research.rec.ClassifierRegEx;
import gov.va.research.rec.ClassifierRegExExtractor;
import gov.va.research.ree.CrossValidate;
import gov.va.research.ree.LSExtractor;
import gov.va.research.ree.LSTriplet;
import gov.va.research.ree.LabeledSegment;
import gov.va.research.ree.REGExExtractor;
import gov.va.research.ree.Snippet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads VTT files
 */
public class VTTReader {

	private static final String SNIPPET_TEXT_BEGIN_REGEX = "Snippet\\s?Text:";
	private static final Pattern SNIPPET_TEXT_BEGIN_PATTERN = Pattern.compile(SNIPPET_TEXT_BEGIN_REGEX);
	private static final String SNIPPET_TEXT_END = "----------------------------------------------------------------------------------";
	private static final Logger LOG = LoggerFactory.getLogger(VTTReader.class);
	private LSExtractor leExt = new LSExtractor(null);
	private CrossValidate cv = new CrossValidate();
	private Map<String, Pattern> patternCache = new HashMap<>();

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
		List<Snippet> snippets = extractSnippets(vttFile, label);
		List<LSTriplet> ls3list = new ArrayList<>(snippets.size());
		for (Snippet snippet : snippets) {
			for (LabeledSegment ls : snippet.getLabeledSegments()) {
				if (label.equals(ls.getLabel())) {
					ls3list.add(LSTriplet.valueOf(snippet.getText(), ls));
				}
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
	public List<LSTriplet> extractRegexExpressions(final File vttFile, final String label, final String outputFileName) throws IOException{
		List<Snippet> snippets = extractSnippets(vttFile, label);
		return extractRegexExpressions(snippets, label, outputFileName);
	}
	
	public List<LSTriplet> extractRegexExpressions(List<Snippet> snippets,
			String label, String outputFileName) throws IOException {
		REGExExtractor regExt = new REGExExtractor();
		return regExt.extractRegexExpressions(snippets, label, outputFileName);
	}

	public Map<String,List<ClassifierRegEx>> extracteRegexClassifications(final File vttFile, final String label, final String classifierOutputFileName) throws IOException{
		List<Snippet> snippetsYes = extractSnippets(vttFile, "yes");
		List<ClassifierRegEx> regExYes = extracteRegexClassifications(snippetsYes, "yes");
		List<Snippet> snippetsNo = extractSnippets(vttFile, "no");
		List<ClassifierRegEx> regExNo = extracteRegexClassifications(snippetsNo, "no");
		if(classifierOutputFileName != null && !classifierOutputFileName.equals("")){
			File outputFile = new File(classifierOutputFileName);
			if(!outputFile.exists())
				outputFile.createNewFile();
			FileWriter fWriter = new FileWriter(outputFile.getAbsoluteFile(), false);
			PrintWriter pWriter = new PrintWriter(fWriter);
			pWriter.println("yes regex");
			for(ClassifierRegEx regEx : regExYes){
				pWriter.println(regEx.getRegEx());
			}
			pWriter.println("\nno regex");
			for(ClassifierRegEx regEx : regExNo){
				pWriter.println(regEx.getRegEx());
			}
			pWriter.close();
			fWriter.close();
		}
		CrossValidate cv = new CrossValidate();
		CVScore score = cv.testClassifier(snippetsYes, regExYes, null, "yes");
		System.out.println(score.getEvaluation());
		score = cv.testClassifier(snippetsNo, regExNo, null, "no");
		System.out.println(score.getEvaluation());
		Map<String, List<ClassifierRegEx>> returnMap = new HashMap<>();
		returnMap.put("yes", regExYes);
		returnMap.put("no", regExNo);
		return returnMap;
	}
	
	public List<ClassifierRegEx> extracteRegexClassifications(
			List<Snippet> snippets, String label) {
		ClassifierRegExExtractor clRegExExt = new ClassifierRegExExtractor();
		return clRegExExt.extracteRegexClassifications(snippets, label);
	}

	/*public List<LSTriplet> extractRegexExpressions(final List<Snippet> snippets, final String label) throws IOException{
		List<LSTriplet> ls3list = new ArrayList<>(snippets.size());
		for (Snippet snippet : snippets) {
			if (label.equals(snippet.getLabel())) {
				ls3list.add(LSTriplet.valueOf(snippet));
			}
		}
		
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
	}*/

	/**
	 * Extracts regular expressions from the snippet triplets.
	 * @param vttFile The VTT file to extract triplets from.
	 * @param label The label of the segments to extract.
	 * @return Regular expressions extracted from the snippets.
	 * @throws IOException
	 */
	public List<Snippet> extractSnippets(final File vttFile, final String label)
			throws IOException {
		VttDocument vttDoc = read(vttFile);
		String docText = vttDoc.GetText();
		Pattern snippetPattern = Pattern.compile("(?s)" + SNIPPET_TEXT_BEGIN_REGEX + "(.*?)" + SNIPPET_TEXT_END);
		Matcher snippetMatcher = snippetPattern.matcher(docText);
		TreeMap<SnippetPosition, Snippet> pos2snips = new TreeMap<>();
		while (snippetMatcher.find()) {
			SnippetPosition snipPos = new SnippetPosition(snippetMatcher.start(1), snippetMatcher.end(1));
			Snippet snip = new Snippet(snippetMatcher.group(1), null);
			pos2snips.put(snipPos, snip);
		}
		// The last snippet in the file does not have a snippet end delimiter, so we must add it separately.
		Matcher snippetBeginMatcher = SNIPPET_TEXT_BEGIN_PATTERN.matcher(docText);
		snippetBeginMatcher.find(pos2snips.lastKey().end);

		SnippetPosition snipPos = new SnippetPosition(snippetBeginMatcher.end(), docText.length());
		Snippet snip = new Snippet(docText.substring(snippetBeginMatcher.end()), null);
		pos2snips.put(snipPos, snip);

		for (Markup markup : vttDoc.GetMarkups().GetMarkups()) {
			// Check if the markup has the requested label
			if (label.equalsIgnoreCase(markup.GetTagName())) {

				// Get the labeled text boundaries
				int labeledOffset = markup.GetOffset();
				int labeledLength = markup.GetLength();
				int labeledEnd = labeledOffset + labeledLength;

				// Find the snippet in which the labeled segment occurs
				SnippetPosition labelPos = new SnippetPosition(labeledOffset, labeledEnd);
				Entry<SnippetPosition, Snippet> p2s = pos2snips.floorEntry(labelPos);
				if (p2s == null) {
					LOG.error("No enclosing snippet found for label position: " + labelPos);
				} else if (!(p2s.getKey().start <= labeledOffset && p2s.getKey().end >= labeledEnd)) {
					LOG.error("Label is not within snippet. Label position:" + labelPos + ", snippet position:" + p2s.getKey());
				} else {
					String labStr = docText.substring(labeledOffset, labeledEnd);
					// Adjust the labeled string boundaries so that it does not have any whitespace prefix or suffix
					while (Character.isWhitespace(labStr.charAt(0))) {
						labeledOffset++;
						labStr = labStr.substring(1);
						labeledLength--;
					}
					while (Character.isWhitespace(labStr.charAt(labStr.length() - 1))) {
						labeledEnd--;
						labStr = labStr.substring(0, labStr.length() - 1);
						labeledLength--;
					}
					LabeledSegment ls = new LabeledSegment(label, labStr, labeledOffset - p2s.getKey().start, labeledLength);
					Snippet snippet = p2s.getValue();
					Collection<LabeledSegment> labeledSegments = snippet.getLabeledSegments();
					if (labeledSegments == null) {
						labeledSegments = new ArrayList<LabeledSegment>();
						snippet.setLabeledSegments(labeledSegments);
					}
					labeledSegments.add(ls);
				}
			}
		}
		return new ArrayList<Snippet>(pos2snips.values());
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
	private Map<String,List<LSTriplet>> groupTriplets(List<LSTriplet> ls3list){
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
	
	public List<LSTriplet> removeDuplicates(List<LSTriplet> ls3list)
	{
				
		Set<LSTriplet> listToSet = new HashSet<LSTriplet>(ls3list);
		List<LSTriplet> ls3listWithoutDuplicates = new ArrayList<LSTriplet>(listToSet);
		return ls3listWithoutDuplicates;
	}
	
	private class SnippetPosition implements Comparable<SnippetPosition> {
		public final int start;
		public final int end;
		public SnippetPosition(final int start, final int end) {
			this.start = start;
			this.end = end;
		}
		@Override
		public int hashCode() {
			int result = 17;
			result = 31 * result + start;
			result = 31 * result + end;
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof SnippetPosition)) {
				return false;
			}
			SnippetPosition sp = (SnippetPosition)obj;
			return sp.start == start && sp.end == end;
		}
		@Override
		public String toString() {
			return "" + start + "-" + end;
		}
		@Override
		public int compareTo(SnippetPosition o) {
			if (start < o.start) {
				return -1;
			}
			if (start > o.start){
				return 1;
			}
			if (end < o.end) {
				return -1;
			}
			if (end > o.end) {
				return 1;
			}
			return 0;
		}
		
	}
}
