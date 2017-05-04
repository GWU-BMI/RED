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

import gov.nih.nlm.nls.vtt.model.Markup;
import gov.nih.nlm.nls.vtt.model.Tags;
import gov.nih.nlm.nls.vtt.model.VttDocument;

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
import java.util.function.Function;

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
		boolean valid = vttDoc.readFromFile(null, vttFile);
		if (!valid) {
			throw new IOException("Not a valid VTT file: " + vttFile);
		}
		return vttDoc;
	}

	/**
	 * Reads labeled segment triplets from a VTT file
	 * @param vttFile The VTT file to extract triplets from.
	 * @param label The label of the segments to extract.
	 * @param snippetParser Function to use to parse snippets from <code>vttFile</code>.
	 * @return Labeled segment triplets (before labeled segment, labeled segment, after labeled segment)
	 * @throws IOException when a problem occurs reading <code>vttFile</code>.
	 */
	public List<LSTriplet> readLSTriplets(final File vttFile, final String label, Function<VttDocument, TreeMap<SnippetPosition, Snippet>> snippetParser) throws IOException {
		Collection<Snippet> snippets = readSnippets(vttFile, label, snippetParser);
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
	 * Reads snippets from a vtt file.
	 * @param vttFile The VTT file to extract triplets from.
	 * @param includeLabel The label of the segments to extract.
	 * @param snippetParser Function to use to parse snippets from <code>vttFile</code>.
	 * @return Snippets containing labeled segments for the specified label.
	 * @throws IOException when a problem occurs reading <code>vttFile</code>.
	 */
	public Collection<Snippet> readSnippets(final File vttFile, final String includeLabel, Function<VttDocument, TreeMap<SnippetPosition, Snippet>> snippetParser) throws IOException {
		Collection<String> includeLabels = new ArrayList<>(1);
		includeLabels.add(includeLabel);
		return readSnippets(vttFile, includeLabels, snippetParser);
	}

	/**
	 * Reads snippets from a vtt file.
	 * @param vttFile The VTT file to extract triplets from.
	 * @param includeLabels A collection of the labels of the segments to extract.
 	 * @param snippetParser Function to use to parse snippets from <code>vttFile</code>.
	 * @return Snippets containing labeled segments for the specified label.
	 * @throws IOException when a problem occurs reading <code>vttFile</code>.
	 */
	public Collection<Snippet> readSnippets(final File vttFile, final Collection<String> includeLabels,
			final Function<VttDocument, TreeMap<SnippetPosition, Snippet>> snippetParser)
			throws IOException {
		VttDocument vttDoc = read(vttFile);
		String docText = vttDoc.getText();
		TreeMap<SnippetPosition, Snippet> pos2snips = snippetParser.apply(vttDoc);// findSnippetPositions(vttDoc);
		Set<Snippet> snippets = new HashSet<>();

		for (Markup markup : vttDoc.getMarkups().getMarkups()) {
			// Check if the markup has the requested label
			if (CVUtils.containsCI(includeLabels, markup.getTagName())) {

				// Get the labeled text boundaries
				int labeledOffset = markup.getOffset();
				int labeledLength = markup.getLength();
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
					LabeledSegment ls = new LabeledSegment(markup.getTagName().toLowerCase(), labStr, labeledOffset - p2s.getKey().start, labeledLength);
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
	 * Reads snippets from a vtt file
	 * @param vttFile The VTT file to extract triplets from.
	 * @param snippetParser Function to use to parse snippets from <code>vttFile</code>.
	 * @return All snippets in the vtt file.
	 * @throws IOException when a problem occurs reading <code>vttFile</code>.
	 */
	public Collection<Snippet> readSnippets(final File vttFile, Function<VttDocument, TreeMap<SnippetPosition, Snippet>> snippetParser)
			throws IOException {
		VttDocument vttDoc = read(vttFile);
		TreeMap<SnippetPosition, Snippet> pos2snips = snippetParser.apply(vttDoc);

		for (Markup markup : vttDoc.getMarkups().getMarkups()) {
			// Check if the markup is not a SnippetColumn
			if (!"SnippetColumn".equalsIgnoreCase(markup.getTagName())) {

				// Get the labeled text boundaries
				int labeledOffset = markup.getOffset();
				int labeledLength = markup.getLength();
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
	 * Reads snippets from a vtt file
	 * @param vttFile The VTT file to extract triplets from.
	 * @param snippetParser Function to use to parse snippets from <code>vttFile</code>.
	 * @return All snippets in the vtt file.
	 * @throws IOException when a problem occurs reading <code>vttFile</code>.
	 */
	public Collection<Snippet> readSnippetsAll(final File vttFile, Function<VttDocument, TreeMap<SnippetPosition, Snippet>> snippetParser)
			throws IOException {
		VttDocument vttDoc = read(vttFile);
		String docText = vttDoc.getText();
		TreeMap<SnippetPosition, Snippet> pos2snips = snippetParser.apply(vttDoc);
		
		Tags tags = vttDoc.getTags();
		for (Markup markup : vttDoc.getMarkups().getMarkups()) {
			// Check if the markup is not a SnippetColumn
			if (!"SnippetColumn".equalsIgnoreCase(markup.getTagName()) && tags.getTagNames().contains(markup.getTagName())) {

				// Get the labeled text boundaries
				int labeledOffset = markup.getOffset();
				int labeledLength = markup.getLength();
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
					LabeledSegment ls = new LabeledSegment(markup.getTagName(), labStr, labeledOffset - p2s.getKey().start, labeledLength);
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
	 * @param snippetParser Function to use to parse snippets from <code>vttFile</code>.
	 * @return All snippets in the vtt file, including labeled segments matching the collection of labels.
	 * @throws IOException when a problem occurs reading <code>vttFile</code>.
	 */
	public Collection<Snippet> findSnippets(final File vttFile, final Collection<String> labels, Function<VttDocument, TreeMap<SnippetPosition, Snippet>> snippetParser)
			throws IOException {
		VttDocument vttDoc = read(vttFile);

		TreeMap<SnippetPosition, Snippet> pos2snips = snippetParser.apply(vttDoc);
		
		String docText = vttDoc.getText();
		for (Markup markup : vttDoc.getMarkups().getMarkups()) {
			// Check if the markup is not a SnippetColumn
			if (!"SnippetColumn".equalsIgnoreCase(markup.getTagName())) {
				// Get the labeled text boundaries
				int labeledOffset = markup.getOffset();
				int labeledLength = markup.getLength();
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
					if (labStr.trim().isEmpty()) {
						LOG.warn("Empty labeled string: " + markup.toString());
						continue;
					}
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
					LabeledSegment ls = new LabeledSegment(markup.getTagName(), labStr, labeledOffset - p2s.getKey().start, labeledLength);
					Snippet snippet = p2s.getValue();
					List<LabeledSegment> labeledSegments = null;
					if (CVUtils.containsCI(labels, markup.getTagName())) {
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
	
}
