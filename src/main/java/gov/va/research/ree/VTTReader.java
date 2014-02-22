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
import java.util.List;

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
}
