package gov.va.research.red;

import gov.va.research.v3nlp.common.util.ASCII;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SnippetSplitter {

	private static final Pattern START_TEXT_PATTERN = Pattern
			.compile("#<-{70}>\\r?\\n#<Text Content>\\r?\\n#<-{70}>\\r?\\n");
	private static final Pattern START_SNIPPET_PATTERN = Pattern
			.compile("(?m)\"?\\r?\\n?(\\d+),(\\d+(?:\\.\\d+E[+-]\\d+)?),(\\d+),\"?");
	private static final Pattern START_TAGS_PATTERN = Pattern
			.compile("#<-{70}>\\r?\\n#<Tags Configuration>\\r?\\n#<Name\\|Category\\|Bold\\|Italic\\|Underline\\|Display\\|FR\\|FG\\|FB\\|BR\\|BG\\|BB\\|FontFamily\\|FontSize>\\r?\\n#<-{70}>\\r?\\n(?:[^|]*\\|){13}.*\\r?\\n#<-{70}>\\r?\\n(?:(?:[^|]*\\|){13}.*\\r?\\n)+?#<-{70}>\\r?\\n#<MarkUps Information>\\r?\\n#<Offset\\|Length\\|TagName\\|TagCategory\\|Annotation\\|TagText>\\r?\\n#<-{70}>\\r?\\n");
	private static final Pattern TAG_PATTERN = Pattern
			.compile("\\s*(\\d+)\\|\\s*(\\d+)\\|\\s*(\\w+)\\s*\\|\\s*\\|.*\\|(.*)");

	/**
	 * Extracts RED-style snippets from VTT file content having no VTT snippet tags.
	 * @param fileContents String containing the contents of a VTT file.
	 * @param posLabels Array of tag labels that will be treated as positive labels. Everything else will be treated as negative.
	 * @return A <code>SortedMap</code> of file offset positions to RED-style snippets
	 */
	public static SortedMap<Integer, Snippet> splitVTT(String fileContents,
			String[] posLabels) {
		// Parse a vtt file with no snippet boundary tags
		Matcher m = START_TEXT_PATTERN.matcher(fileContents);
		m.find();
		int textOffset = m.end();
		String noHeaderFileContents = fileContents.substring(textOffset);
		fileContents = null;
		m = START_SNIPPET_PATTERN.matcher(noHeaderFileContents);
		SortedMap<Integer, Snippet> snippets = new TreeMap<>();
		// first snippet
		m.find();
		int offset = m.end();
		while (m.find()) {
			// matched next snippet
			String snippetText = noHeaderFileContents.substring(offset,
					m.start());
			Snippet s = new Snippet(snippetText, null, null);
			Snippet prevEntry = snippets.put(offset, s);
			if (prevEntry != null) {
				throw new IllegalArgumentException(
						"Multiple snippets beginning at offset " + m.start());
			}
			offset = m.end();
		}
		// find the start of the tags
		m = START_TAGS_PATTERN.matcher(noHeaderFileContents);
		m.find();
		// add the last snippet
		String snippetText = null;
		try {
			snippetText = noHeaderFileContents.substring(offset, m.start());
		} catch (StringIndexOutOfBoundsException e) {
			e.printStackTrace();
		}
		if (snippetText.endsWith("\n")) {
			snippetText = snippetText.substring(0, snippetText.length() - 1);
		}
		if (snippetText.endsWith("\r")) {
			snippetText = snippetText.substring(0, snippetText.length() - 1);
		}
		if (snippetText.endsWith("\"")) {
			snippetText = snippetText.substring(0, snippetText.length() - 1);
		}
		Snippet s = new Snippet(snippetText, null, null);
		Snippet prevEntry = snippets.put(offset, s);
		if (prevEntry != null) {
			throw new IllegalArgumentException(
					"Multiple snippets beginning at offset " + m.start());
		}
		// process the tags
		int startTagsOffset = m.end();
		String tagText = noHeaderFileContents.substring(startTagsOffset);
		m = TAG_PATTERN.matcher(tagText);
		tagText = null;
		Set<String> extraLabels = new HashSet<>();
		while (m.find()) {
			int fileOffset = Integer.parseInt(m.group(1));
			int length = Integer.parseInt(m.group(2));
			String tag = m.group(3);
			SortedMap<Integer, Snippet> headMap = snippets.headMap(fileOffset + 1);
			Integer snippetOffset = headMap.lastKey();
			Snippet snippetForTag = snippets.get(snippetOffset);
			int offsetFromBeginningOfSnippet = fileOffset - snippetOffset;
			if (offsetFromBeginningOfSnippet >= snippetForTag.getText().length()) {
				throw new IllegalArgumentException("Illegal offset "
						+ offsetFromBeginningOfSnippet + " + in snippet starting at "
						+ snippetOffset + "\n<Snippet Text>\n"
						+ snippetForTag.getText() + "\n</Snippet Text>");
			}
			if ((offsetFromBeginningOfSnippet + length) > snippetForTag.getText().length()) {
				// Trim off any double-quotes that may have accidentally been included in the annotation
				System.err.println("Tagged text extends past end of snippet. Trying to trim extraneous double-quotes...");
				if (noHeaderFileContents.charAt((fileOffset + length) - 1) == '"') {
					length--;
					System.err.println("... trimmed.");
				}
				if ((offsetFromBeginningOfSnippet + length) > snippetForTag.getText().length()) {
					throw new IllegalArgumentException("Tagged text extends past end of snippet.\nsnippet text = " + snippetForTag.getText() + "\nsnippetOffset = " + snippetOffset + "\nlength = " + length);
				}
			}
			String labeledText = snippetForTag.getText().substring(offsetFromBeginningOfSnippet,
					offsetFromBeginningOfSnippet + length);
			LabeledSegment ls = new LabeledSegment(tag, labeledText,
					offsetFromBeginningOfSnippet, length);
			if (CVUtils.containsCI(posLabels, tag)) {
				snippetForTag.getPosLabeledSegments().add(ls);
			} else {
				snippetForTag.getNegLabeledSegments().add(ls);
				extraLabels.add(tag);
			}
		}
		System.out.println("Extra labels: " + extraLabels);
		return snippets;
	}

	/**
	 * Extracts RED-style snippets from a VTT file having no VTT snippet tags.
	 * @param vttFile A VTT file containing no snippet tags.
	 * @param posLabels Array of tag labels that will be treated as positive labels. Everything else will be treated as negative.
	 * @throws IOException if the and I/O error occurs while reading the file
	 * @return A <code>SortedMap</code> of file offset positions to RED-style snippets
	 */
	public static SortedMap<Integer, Snippet> splitVTT(Path vttFile,
			String[] posLabels) throws IOException {
		String fileContent = new String(Files.readAllBytes(vttFile), "UTF-8");
		return splitVTT(ASCII.toASCII8(fileContent), posLabels);
	}

}
