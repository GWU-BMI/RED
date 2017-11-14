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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nih.nlm.nls.vtt.model.Markup;
import gov.nih.nlm.nls.vtt.model.Tags;
import gov.nih.nlm.nls.vtt.model.VttDocumentDelegate;
import gov.va.vinci.nlp.framework.utils.snippet.SnippetUtil;

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
	public VttDocumentDelegate read(final File vttFile) throws IOException {
		VttDocumentDelegate vttDoc = new VttDocumentDelegate();
		boolean valid = vttDoc.readFromFile(null, vttFile);
		if (!valid) {
			throw new IOException("Not a valid VTT file: " + vttFile);
		}
		return vttDoc;
	}

	/**
	 * Reads snippets from a vtt file.
	 * @param vttFile The VTT file to extract triplets from.
	 * @param includeLabel The label of the segments to extract.
	 * @param snippetParser Function to use to parse snippets from <code>vttFile</code>.
	 * @return Snippets containing labeled segments for the specified label.
	 * @throws IOException when a problem occurs reading <code>vttFile</code>.
	 */
	public Collection<Snippet> readSnippets(final File vttFile, final String includeLabel, Function<VttDocumentDelegate, TreeMap<Position, Snippet>> snippetParser) throws IOException {
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
			final Function<VttDocumentDelegate, TreeMap<Position, Snippet>> snippetParser)
			throws IOException {
		VttDocumentDelegate vttDoc = read(vttFile);
		String docText = vttDoc.getText();
		TreeMap<Position, Snippet> pos2snips = snippetParser.apply(vttDoc);// findSnippetPositions(vttDoc);
		Set<Snippet> snippets = new HashSet<>();

		for (Markup markup : vttDoc.getMarkups().getMarkups()) {
			// Check if the markup has the requested label
			if (CVUtils.containsCI(includeLabels, markup.getTagName())) {

				// Get the labeled text boundaries
				int labeledOffset = markup.getOffset();
				int labeledLength = markup.getLength();
				int labeledEnd = labeledOffset + labeledLength;

				// Find the snippet in which the labeled segment occurs
				Position labelPos = new Position(labeledOffset, labeledEnd);
				Entry<Position, Snippet> p2s = pos2snips.floorEntry(labelPos);
				if (p2s == null) {
					LOG.error("" + vttFile.getName() + ": No enclosing snippet found for label position: " + labelPos);
				} else if (!(p2s.getKey().start <= labeledOffset && p2s.getKey().end >= labeledEnd)) {
					LOG.error("" + vttFile.getName() + ": Label is not within snippet. Label position:" + labelPos + ", snippet position:" + p2s.getKey());
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
					List<LabeledSegment> labeledSegments = snippet.getLabeledSegments();
					if (labeledSegments == null) {
						labeledSegments = new ArrayList<LabeledSegment>();
						snippet.setLabeledSegments(labeledSegments);
					}
					labeledSegments.add(ls);
					snippets.add(snippet);
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
	public Collection<Snippet> readSnippets(final File vttFile, Function<VttDocumentDelegate, TreeMap<Position, Snippet>> snippetParser)
			throws IOException {
		VttDocumentDelegate vttDoc = read(vttFile);
		TreeMap<Position, Snippet> pos2snips = snippetParser.apply(vttDoc);

		for (Markup markup : vttDoc.getMarkups().getMarkups()) {
			// Check if the markup is not a SnippetColumn
			if (!"SnippetColumn".equalsIgnoreCase(markup.getTagName())) {

				// Get the labeled text boundaries
				int labeledOffset = markup.getOffset();
				int labeledLength = markup.getLength();
				int labeledEnd = labeledOffset + labeledLength;

				// Find the snippet in which the labeled segment occurs
				Position labelPos = new Position(labeledOffset, labeledEnd);
				Entry<Position, Snippet> p2s = pos2snips.floorEntry(labelPos);
				if (p2s == null) {
					LOG.error("" + vttFile.getName() + ": No enclosing snippet found for label position: " + labelPos);
				} else if (!(p2s.getKey().start <= labeledOffset && p2s.getKey().end >= labeledEnd)) {
					LOG.error("" + vttFile.getName() + ": Label is not within snippet. Label position:" + labelPos + ", snippet position:" + p2s.getKey());
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
	public Collection<Snippet> readSnippetsAll(final File vttFile, Function<VttDocumentDelegate, TreeMap<Position, Snippet>> snippetParser)
			throws IOException {
		VttDocumentDelegate vttDoc = read(vttFile);
		String docText = vttDoc.getText();
		TreeMap<Position, Snippet> pos2snips = snippetParser.apply(vttDoc);
		
		Tags tags = vttDoc.getTags();
		for (Markup markup : vttDoc.getMarkups().getMarkups()) {
			// Check if the markup is not a SnippetColumn
			if (!"SnippetColumn".equalsIgnoreCase(markup.getTagName()) && tags.getTagNames().contains(markup.getTagName())) {

				// Get the labeled text boundaries
				int labeledOffset = markup.getOffset();
				int labeledLength = markup.getLength();
				int labeledEnd = labeledOffset + labeledLength;

				// Find the snippet in which the labeled segment occurs
				Position labelPos = new Position(labeledOffset, labeledEnd);
				Entry<Position, Snippet> p2s = pos2snips.floorEntry(labelPos);
				if (p2s == null) {
					LOG.error("" + vttFile.getName() + ": No enclosing snippet found for label position: " + labelPos);
				} else if (!(p2s.getKey().start <= labeledOffset && p2s.getKey().end >= labeledEnd)) {
					LOG.error("" + vttFile.getName() + ": Label is not within snippet. Label position:" + labelPos + ", snippet position:" + p2s.getKey());
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
					List<LabeledSegment> labeledSegments = snippet.getLabeledSegments();
					if (labeledSegments == null) {
						labeledSegments = new ArrayList<LabeledSegment>();
						snippet.setLabeledSegments(labeledSegments);
					}
					labeledSegments.add(ls);
				}
			}
		}
		return pos2snips.values();
	}
	
	/**
	 * Finds all snippets in a vtt file, adding labeled segments for tags with matching labels.
	 * @param vttFile The VTT file to extract triplets from.
	 * @param labels Labeled segments with any of these labels will be included with the snippets.
	 * @param convertToLowercase If <code>true</code> then all labels and snippet text will be converted to lower case.
	 * @param snippetParser Function to use to parse snippets from <code>vttFile</code>.
	 * @return All snippets in the vtt file, including labeled segments matching the collection of labels.
	 * @throws IOException when a problem occurs reading <code>vttFile</code>.
	 */
	public Collection<Snippet> findSnippets(final File vttFile,
			final Collection<String> labels, final boolean convertToLowercase,
			final Function<VttDocumentDelegate, TreeMap<Position, Snippet>> snippetParser)
			throws IOException {
		return findSnippets(vttFile, labels, convertToLowercase, -1, -1, snippetParser);
	}

	/**
	 * Finds all snippets in a vtt file, adding labeled segments for tags with matching labels.
	 * @param vttFile The VTT file to extract triplets from.
	 * @param labels Labeled segments with any of these labels will be included with the snippets.
	 * @param convertToLowercase If <code>true</code> then all labels and snippet text will be converted to lower case.
	 * @param trimToWordsBeforeLabel Trim the snippets to only include a maximum of this number of words before the labeled value. Set to -1 for no trimming before the labeled value.
	 * @param trimToWordsAfterLabel Trim the snippets to only include a maximum of this number of words after the labeled value. Set to -1 for no trimming after the labeled value.
	 * @param snippetParser Function to use to parse snippets from <code>vttFile</code>.
	 * @return All snippets in the vtt file, including labeled segments matching the collection of labels.
	 * @throws IOException when a problem occurs reading <code>vttFile</code>.
	 */
	public Collection<Snippet> findSnippets(final File vttFile,
			final Collection<String> labels, final boolean convertToLowercase,
			final int trimToWordsBeforeLabel, final int trimToWordsAfterLabel,
			final Function<VttDocumentDelegate, TreeMap<Position, Snippet>> snippetParser)
			throws IOException {
		VttDocumentDelegate vttDoc = read(vttFile);

		TreeMap<Position, Snippet> pos2snips = snippetParser.apply(vttDoc);
		
		String docText = convertToLowercase ? vttDoc.getText().toLowerCase() : vttDoc.getText();
		for (Markup markup : vttDoc.getMarkups().getMarkups()) {
			// Check if the markup has one of the requested labels
			if (CVUtils.containsCI(labels, markup.getTagName())) {
				// Get the labeled text boundaries
				int labeledOffset = markup.getOffset();
				int labeledLength = markup.getLength();
				int labeledEnd = labeledOffset + labeledLength;

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
				
				// Find the snippet in which the labeled segment occurs
				Position labelPos = new Position(labeledOffset, labeledEnd);
				Entry<Position, Snippet> p2s = pos2snips.floorEntry(labelPos);
				if (p2s == null) {
					LOG.error("" + vttFile.getName() + ": No enclosing snippet found for label position: " + labelPos);
				} else if (!(p2s.getKey().start <= labeledOffset && p2s.getKey().end >= labeledEnd)) {
					LOG.error("" + vttFile.getName() + ": Label is not within snippet. Label position:" + labelPos + ", snippet position:" + p2s.getKey());
				} else {
					LabeledSegment ls = new LabeledSegment(convertToLowercase ? markup.getTagName().toLowerCase() : markup.getTagName(),
							labStr, labeledOffset - p2s.getKey().start, labeledLength);
					Snippet snippet = p2s.getValue();
					List<LabeledSegment> labeledSegments = null;

					labeledSegments = snippet.getLabeledSegments();
					if (labeledSegments == null) {
						labeledSegments = new ArrayList<LabeledSegment>();
						snippet.setLabeledSegments(labeledSegments);
					}
					labeledSegments.add(ls);
				}
			}
		}
		Collection<Snippet> snippets = pos2snips.values();
		snippets = trimSnippets(trimToWordsBeforeLabel, trimToWordsAfterLabel, snippets);
		return snippets;
	}

	/**
	 * Create a new collection of snippets by trimming the boundaries of the supplied snippets.
	 * New unlabeled snippets are also added for all unlabeled text (to serve as negative examples).
	 * @param trimToWordsBeforeLabel Number of words before the labeled values to keep in the snippets.
	 * @param trimToWordsAfterLabel Number of words after the labeled values to keep in the snippets.
	 * @param snippets The collection of snippets to trim.
	 * @return A collection of trimmed snippets.
	 */
	private Collection<Snippet> trimSnippets(final int trimToWordsBeforeLabel, final int trimToWordsAfterLabel,
			Collection<Snippet> snippets) {
		if (trimToWordsBeforeLabel <= -1 && trimToWordsAfterLabel <= -1) {
			return snippets;
		}
		Collection<Snippet> newSnippets = new ArrayList<>(snippets.size());
		for (Snippet s : snippets) {
			// If there are no labeled segments, leave snippet as-is
			if (s.getLabeledSegments() == null || s.getLabeledSegments().size() == 0) {
				 newSnippets.add(s);
			} else {
				// if there are labeled segments, expand their boundaries, group the ones that overlap, and create a snippet for each group.
				// find positions of expanded boundaries of labeled segments
				List<LSPosition> newPositions = new ArrayList<>();
				for (LabeledSegment ls : s.getLabeledSegments()) {
					int newSnipStart = 
							trimToWordsBeforeLabel > -1
							? SnippetUtil.findPreviousWordsOffset(s.getText(), ls.getStart(), trimToWordsBeforeLabel)
							: 0;
					int newSnipEnd = trimToWordsAfterLabel > -1
							? SnippetUtil.findFollowingWordsOffset(s.getText(), ls.getStart() + ls.getLength(),  trimToWordsAfterLabel)
							: s.getText().length();
					newPositions.add(new LSPosition (ls, new Position(newSnipStart, newSnipEnd)));
				}
				// find groups of expanded boundaries that overlap
				Collection<Collection<LSPosition>> groups = new ArrayList<>();
				do {
					List<Integer> overlapIndexes = new ArrayList<>();
					LSPosition overlapWith = newPositions.get(0);
					overlapIndexes.add(0);
					for (int i = 1; i < newPositions.size(); i++) {
						if (overlapWith.pos.overlaps(newPositions.get(i).pos)) {
							overlapIndexes.add(i);
						}
					}
					Collection<LSPosition> group = new ArrayList<>();
					for (int i = overlapIndexes.size() - 1; i >= 0; i--) {
						int matchingIdx = overlapIndexes.get(i);
						group.add(newPositions.get(matchingIdx));
						newPositions.remove(matchingIdx);
					}
					groups.add(group);
				} while (!newPositions.isEmpty());
				// Create a snippet for each group, using the smallest start and the greatest end of the members of the group.
				// Also create snippets with no labeled segments for unlabeled text.
				int prevEnd = 0;
				for (Collection<LSPosition> group : groups) {
					int minStart = -1;
					int maxEnd = -1;
					for (LSPosition p : group) {
						if (minStart < 0 || p.pos.start < minStart) {
							minStart = p.pos.start;
						}
						if (maxEnd < 0 || p.pos.end > maxEnd) {
							maxEnd = p.pos.end;
						}
					}
					List<LabeledSegment> lsegs = new ArrayList<>(group.size());
					for (LSPosition p : group) {
						LabeledSegment ls = p.ls;
						if (minStart != 0) {
							ls.setStart(ls.getStart() - minStart);
						}
						lsegs.add(ls);
					}
					// Add unlabeled snippet
					if (prevEnd < minStart) {
						newSnippets.add(new Snippet(s.getText().substring(prevEnd, minStart), new ArrayList<>(0)));
					}
					// Add a snippet for the group
					newSnippets.add(new Snippet(s.getText().substring(minStart, maxEnd), lsegs));
					prevEnd = maxEnd;
				}
				// Add an unlabeled snippet for the tail
				if (prevEnd < s.getText().length()) {
					newSnippets.add(new Snippet(s.getText().substring(prevEnd), new ArrayList<>(0)));
				}
			}
		}
		return newSnippets;
	}

	public List<LSTriplet> removeDuplicates(List<LSTriplet> ls3list) {
		Set<LSTriplet> listToSet = new HashSet<LSTriplet>(ls3list);
		List<LSTriplet> ls3listWithoutDuplicates = new ArrayList<LSTriplet>(listToSet);
		return ls3listWithoutDuplicates;
	}
	
}
