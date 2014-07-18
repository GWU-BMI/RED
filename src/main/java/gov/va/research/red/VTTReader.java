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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
