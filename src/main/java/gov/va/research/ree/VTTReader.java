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
	public List<LSTriplet> extractRegexExpressions(final File vttFile, final String label) throws IOException{
		List<Snippet> snippets = extractSnippets(vttFile, label);
		return extractRegexExpressions(snippets, label);
	}
	
	public List<LSTriplet> extractRegexExpressions(final List<Snippet> snippets, final String label) {
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
			//LSExtractor lsExtractor = new LSExtractor(null);
			//CrossValidate cv = new CrossValidate();
			//lsExtractor.setRegExpressions(ls3list);
			//CVScore initScore = cv.testExtractor(snippets, lsExtractor);
			//System.out.println("trimming bls and als");
			//System.out.println("No of regex "+ls3list.size());
			trimRegEx(snippets, ls3list);
			//System.out.println("trimming als");
			//trimRegEx(snippets, ls3list, lsExtractor, cv, false);
			leExt.setRegExpressions(ls3list);
			CVScore scoreAfterTrimming = cv.testExtractor(snippets, leExt);
			/*try {
				System.out.println("Score after trimming\n"+scoreAfterTrimming.getEvaluation());
			} catch (IOException e) {
				e.printStackTrace();
			}*/
			/*if(scoreAfterTrimming.getFn() >= initScore.getFn())
				System.out.println("----------------------------------------------------------FATAL ERROR TRIMMING INCREASING FN------------------------------------------");
			if(scoreAfterTrimming.getFp() >= initScore.getFp())
				System.out.println("----------------------------------------------------------FATAL ERROR TRIMMING INCREASING FP------------------------------------------");*/
			/*else
				System.out.println("-------------------Trim Success---------------------------------");*/
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
			if(ls3list.size() != testList.size())
				System.out.println("Reduntant regex code did something");
			else
				System.out.println("Reduntant regex code did nothing");
			ls3list = testList;
			/*if(!ls3list.isEmpty()) {
				lsExtractor.setRegExpressions(ls3list);
				tempScore = cv.testExtractor(snippets, lsExtractor);
				try {
					System.out.println("Score after removing reduntant regex\n"+tempScore.getEvaluation());
				} catch (IOException e) {
					e.printStackTrace();
				}
				for(LSTriplet triplet : ls3list){
					System.out.println("\n\n"+triplet.toStringRegEx()+"\n\n");
				}
			}*/
			//return ls3list;
			Map<String,List<LSTriplet>> snippetGroups = groupSnippets(ls3list);
			processSnippetGroupsTrimVersion(snippetGroups,snippets);
			List<LSTriplet> allTriplets = new ArrayList<>();
			for(List<LSTriplet> tripletList : snippetGroups.values()){
				allTriplets.addAll(tripletList);
			}
			leExt.setRegExpressions(allTriplets);
			tempScore = cv.testExtractor(snippets, leExt);
			try {
				System.out.println("Score after MFT operation\n"+tempScore.getEvaluation());
			} catch (IOException e) {
				e.printStackTrace();
			}
			/*for(LSTriplet triplet : ls3list){
				System.out.println("\n\n"+triplet.toStringRegEx()+"\n\n");
			}*/
			return allTriplets;
		}
		return null;
	}

	/**
	 * Finds out all the groups that have sizes greater than 1. Calls processGroup on those groups.
	 * @param snippetGroups A hashmap containing the groups. Key is LS and the value is a list of LSTriplet's.
	 */
	private void processSnippetGroupsTrimVersion(Map<String,List<LSTriplet>> snippetGroups, final List<Snippet> snippets){
		Iterator<List<LSTriplet>> iteratorSnippetGroups = snippetGroups.values().iterator();
		Map<String, List<LSTriplet>> tempGroupMap = new HashMap<String, List<LSTriplet>>();
		while(iteratorSnippetGroups.hasNext()){
			List<LSTriplet> group = iteratorSnippetGroups.next();
			//if(group.size() > 1){
			processGroupTrimVersion(group, tempGroupMap, true,snippets);
			//}
		}
		
		//repeat the above steps for ALS.
		iteratorSnippetGroups = snippetGroups.values().iterator();
		tempGroupMap = new HashMap<String, List<LSTriplet>>();
		while(iteratorSnippetGroups.hasNext()){
			List<LSTriplet> group = iteratorSnippetGroups.next();
			//if(group.size() > 1){
			processGroupTrimVersion(group, tempGroupMap, false,snippets);
			//}
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
						//cvScore = cv.testExtractor(snippets, lsExtractor);
						//int falsePositives = cvScore.getFp();
						if(cv.checkForFalsePositives(snippets, leExt)){
							triplet.setBLS(bls);
							//System.out.println("trim fail bls "+count++);
							prevTrimOpResultBLS.put(triplet, false);
						}else{
							//System.out.println("trim success");
							prevTrimOpResultBLS.put(triplet, true);
						}
					}
				}
				if(prevTrimOpResultALS.get(triplet) && ((triplet.getBLS().length() <= triplet.getALS().length()) ||  !prevTrimOpResultBLS.get(triplet))){
					processedBLSorALS = false;
					als = triplet.getALS();
					if(als.equals("") || als == null){
						prevTrimOpResultALS.put(triplet, false);
						//System.out.println("trim fail special als "+count++);
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
						//cvScore = cv.testExtractor(snippets, lsExtractor);
						//int falsePositives = cvScore.getFp();
						if(cv.checkForFalsePositives(snippets, leExt)){
							triplet.setALS(als);
							//System.out.println("trim fail als "+count++);
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
		System.out.println("done");
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
			if (label.equals(markup.GetTagName())) {

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
