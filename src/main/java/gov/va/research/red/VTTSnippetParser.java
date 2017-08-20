package gov.va.research.red;

import java.util.TreeMap;
import java.util.function.Function;

import gov.nih.nlm.nls.vtt.model.Markup;
import gov.nih.nlm.nls.vtt.model.VttDocumentDelegate;

public 
class VTTSnippetParser implements Function<VttDocumentDelegate, TreeMap<SnippetPosition, Snippet>> {
	@Override
	public TreeMap<SnippetPosition, Snippet> apply(VttDocumentDelegate vttDoc) {
		TreeMap<SnippetPosition, Snippet> pos2snips = new TreeMap<>();
		String docText = vttDoc.getText();
		for (Markup markup : vttDoc.getMarkups().getMarkups()) {
			if ("SnippetColumn".equals(markup.getTagName())) {
				String annotation = markup.getAnnotation();
				if (annotation != null && annotation.contains("<::>columnNumber=\"4\"<::>")) {
					int snippetOffset = markup.getOffset();
					int snippetLength = markup.getLength();
					int snippetEnd = snippetOffset + snippetLength;
					// decrement snippet offset to include any whitespace preceding it
					while (Character.isWhitespace(docText.charAt(snippetOffset - 1))) {
						snippetOffset--;
						if (snippetEnd < docText.length()) {
							snippetEnd++;
						}
					}
					// increment snippet end to include any whitespace following it
					while ((snippetEnd < docText.length()) && Character.isWhitespace(docText.charAt(snippetEnd))) {
						snippetEnd++;
					}
					String snippet = docText.substring(snippetOffset, snippetEnd).toLowerCase();
					SnippetPosition snipPos = new SnippetPosition(snippetOffset, snippetEnd);
					pos2snips.put(snipPos, new Snippet(snippet, null, null));
				}
			}
		}
		return pos2snips;
	}
	
}
