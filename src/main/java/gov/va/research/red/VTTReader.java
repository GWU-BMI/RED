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
import gov.nih.nlm.nls.vtt.Model.Tags;
import gov.nih.nlm.nls.vtt.Model.VttDocument;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
//import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads VTT files
 */
public class VTTReader {

//	private static final String SNIPPET_TEXT_BEGIN_REGEX = "Snippet\\s?Text:";
//	private static final Pattern SNIPPET_TEXT_BEGIN_PATTERN = Pattern.compile(SNIPPET_TEXT_BEGIN_REGEX, Pattern.CASE_INSENSITIVE);
//	private static final String SNIPPET_TEXT_END = "----------------------------------------------------------------------------------";
	private static final Logger LOG = LoggerFactory.getLogger(VTTReader.class);

	/**
	 * Reads a VTT file.
	 * @param vttFile The VTT format file.
	 * @return A VTT document representation of the VTT file.
	 * @throws IOException when <code>vttFile</code> is not valid.
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
 	 * @param convertToLowercase If <code>true</code> then all text is converted to lowercase (in order, for example, to make case-insensitive comparisons easier)
	 * @return Labeled segment triplets (before labeled segment, labeled segment, after labeled segment)
	 * @throws IOException when a problem occurs reading <code>vttFile</code>.
	 */
	public List<LSTriplet> extractLSTriplets(final File vttFile, final String label, final boolean convertToLowercase) throws IOException {
		Collection<Snippet> snippets = extractSnippets(vttFile, label, convertToLowercase);
		List<LSTriplet> ls3list = new ArrayList<>(snippets.size());
		for (Snippet snippet : snippets) {
			for (LabeledSegment ls : snippet.getPosLabeledSegments()) {
				if (label.equals(ls.getLabel())) {
					ls3list.add(LSTriplet.valueOf(snippet.getText(), ls));
				}
			}
		}
		return ls3list;
	}

	/**
	 * Extracts snippets from a vtt file.
	 * @param vttFile The VTT file to extract triplets from.
	 * @param includeLabel The label of the segments to extract.
 	 * @param convertToLowercase If <code>true</code> then all text is converted to lowercase (in order, for example, to make case-insensitive comparisons easier)
	 * @return Snippets containing labeled segments for the specified label.
	 * @throws IOException when a problem occurs reading <code>vttFile</code>.
	 */
	public Collection<Snippet> extractSnippets(final File vttFile, final String includeLabel, final boolean convertToLowercase) throws IOException {
		Collection<String> includeLabels = new ArrayList<>(1);
		includeLabels.add(includeLabel);
		return extractSnippets(vttFile, includeLabels, convertToLowercase);
	}

	/**
	 * Extracts snippets from a vtt file.
	 * @param vttFile The VTT file to extract triplets from.
	 * @param includeLabels A collection of the labels of the segments to extract.
 	 * @param convertToLowercase If <code>true</code> then all text is converted to lowercase (in order, for example, to make case-insensitive comparisons easier)
	 * @return Snippets containing labeled segments for the specified label.
	 * @throws IOException when a problem occurs reading <code>vttFile</code>.
	 */
	public Collection<Snippet> extractSnippets(final File vttFile, final Collection<String> includeLabels, final boolean convertToLowercase)
			throws IOException {
		VttDocument vttDoc = read(vttFile);
		String docText = vttDoc.GetText();
		TreeMap<SnippetPosition, Snippet> pos2snips = findSnippetPositions(vttDoc, convertToLowercase);
		Set<Snippet> snippets = new HashSet<>();

		for (Markup markup : vttDoc.GetMarkups().GetMarkups()) {
			// Check if the markup has the requested label
			if (CVUtils.containsCI(includeLabels, markup.GetTagName())) {

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
					String labStr = docText.substring(labeledOffset, labeledEnd).toLowerCase();
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
					LabeledSegment ls = new LabeledSegment(markup.GetTagName().toLowerCase(), labStr, labeledOffset - p2s.getKey().start, labeledLength);
					Snippet snippet = p2s.getValue();
					List<LabeledSegment> labeledSegments = snippet.getPosLabeledSegments();
					if (labeledSegments == null) {
						labeledSegments = new ArrayList<LabeledSegment>();
						snippet.setPosLabeledSegments(labeledSegments);
					}
					labeledSegments.add(ls);
					if (!snippets.contains(snippet)) {
						snippets.add(snippet);
					}
				}
			}
		}
		return snippets;
	}

	/**
	 * @param vttDoc
	 * @param docText
	 * @param convertToLowercase If <code>true</code> then all text is converted to lowercase (in order, for example, to make case-insensitive comparisons easier)
	 * @return
	 */
	private TreeMap<SnippetPosition, Snippet> findSnippetPositions(final VttDocument vttDoc, final boolean convertToLowercase) {
		TreeMap<SnippetPosition, Snippet> pos2snips = new TreeMap<>();
		String docText = vttDoc.GetText();
		for (Markup markup : vttDoc.GetMarkups().GetMarkups()) {
			if ("SnippetColumn".equals(markup.GetTagName())) {
				String annotation = markup.GetAnnotation();
				if (annotation != null && annotation.contains("<::>columnNumber=\"4\"<::>")) {
					int snippetOffset = markup.GetOffset();
					int snippetLength = markup.GetLength();
					int snippetEnd = snippetOffset + snippetLength;
					String snippet = docText.substring(snippetOffset, snippetEnd).toLowerCase();
					SnippetPosition snipPos = new SnippetPosition(snippetOffset, snippetEnd);
					pos2snips.put(snipPos, new Snippet(snippet, null, null));
				}
			}
		}
		return pos2snips;
	}

	/**
	 * Extracts snippets from a vtt file
	 * @param vttFile The VTT file to extract triplets from.
	 * @param convertToLowercase If <code>true</code> then all text is converted to lowercase (in order, for example, to make case-insensitive comparisons easier)
	 * @return All snippets in the vtt file.
	 * @throws IOException when a problem occurs reading <code>vttFile</code>.
	 */
	public Collection<Snippet> extractSnippets(final File vttFile, final boolean convertToLowercase)
			throws IOException {
		VttDocument vttDoc = read(vttFile);
		TreeMap<SnippetPosition, Snippet> pos2snips = findSnippetPositions(vttDoc, convertToLowercase);

		Tags tags = vttDoc.GetTags();
		for (Markup markup : vttDoc.GetMarkups().GetMarkups()) {
			// Check if the markup is not a SnippetColumn
			if (!"SnippetColumn".equalsIgnoreCase(markup.GetTagName()) && tags.GetTagNames().contains(markup.GetTagName())) {

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
					pos2snips.remove(p2s.getKey());
				}
			}
		}
		return pos2snips.values();
	}
	
	
	/**
	 * Extracts snippets from a vtt file
	 * @param vttFile The VTT file to extract triplets from.
	 * @param convertToLowercase If <code>true</code> then all text is converted to lowercase (in order, for example, to make case-insensitive comparisons easier)
	 * @return All snippets in the vtt file.
	 * @throws IOException when a problem occurs reading <code>vttFile</code>.
	 */
	public Collection<Snippet> extractSnippetsAll(final File vttFile, final boolean convertToLowercase)
			throws IOException {
		VttDocument vttDoc = read(vttFile);
		String docText = vttDoc.GetText();
		TreeMap<SnippetPosition, Snippet> pos2snips = findSnippetPositions(vttDoc, convertToLowercase);
		
		Tags tags = vttDoc.GetTags();
		for (Markup markup : vttDoc.GetMarkups().GetMarkups()) {
			// Check if the markup is not a SnippetColumn
			if (!"SnippetColumn".equalsIgnoreCase(markup.GetTagName()) && tags.GetTagNames().contains(markup.GetTagName())) {

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
					LabeledSegment ls = new LabeledSegment(markup.GetTagName(), labStr, labeledOffset - p2s.getKey().start, labeledLength);
					Snippet snippet = p2s.getValue();
					List<LabeledSegment> labeledSegments = snippet.getPosLabeledSegments();
					if (labeledSegments == null) {
						labeledSegments = new ArrayList<LabeledSegment>();
						snippet.setPosLabeledSegments(labeledSegments);
					}
					labeledSegments.add(ls);
				}
			}
		}
		return pos2snips.values();
	}
	
	/**
	 * Finds all snippets in a vtt file, adding positive labeled segments for tags those with matching labels, and negative labeled segments for tags without matching labels.
	 * @param vttFile The VTT file to extract triplets from.
	 * @param labels Labeled segments with any of these labels will be included with the snippets.
 	 * @param convertToLowercase If <code>true</code> then all text is converted to lowercase (in order, for example, to make case-insensitive comparisons easier)
	 * @return All snippets in the vtt file, including labeled segments matching the collection of labels.
	 * @throws IOException when a problem occurs reading <code>vttFile</code>.
	 */
	public Collection<Snippet> findSnippets(final File vttFile, final Collection<String> labels, final boolean convertToLowercase)
			throws IOException {
		VttDocument vttDoc = read(vttFile);

		TreeMap<SnippetPosition, Snippet> pos2snips = findSnippetPositions(vttDoc, convertToLowercase);
		
		String docText = vttDoc.GetText();
		for (Markup markup : vttDoc.GetMarkups().GetMarkups()) {
			// Check if the markup is not a SnippetColumn
			if (!"SnippetColumn".equalsIgnoreCase(markup.GetTagName())) {
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
					LabeledSegment ls = new LabeledSegment(markup.GetTagName(), labStr, labeledOffset - p2s.getKey().start, labeledLength);
					Snippet snippet = p2s.getValue();
					List<LabeledSegment> labeledSegments = null;
					if (CVUtils.containsCI(labels, markup.GetTagName())) {
						labeledSegments = snippet.getPosLabeledSegments();
						if (labeledSegments == null) {
							labeledSegments = new ArrayList<LabeledSegment>();
							snippet.setPosLabeledSegments(labeledSegments);
						}
					} else {
						labeledSegments = snippet.getNegLabeledSegments();
						if (labeledSegments == null) {
							labeledSegments = new ArrayList<LabeledSegment>();
							snippet.setNegLabeledSegments(labeledSegments);
						}
					}					
					labeledSegments.add(ls);
				}
			}
		}
		return pos2snips.values();
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
//			if (end < o.end) {
//				return -1;
//			}
//			if (end > o.end) {
//				return 1;
//			}
			return 0;
		}
		
	}
}
