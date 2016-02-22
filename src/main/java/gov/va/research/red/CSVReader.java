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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import java.util.regex.Pattern;

/**
 * Reads VTT files
 */
public class CSVReader {

	private static final Logger LOG = LoggerFactory.getLogger(CSVReader.class);
	private static final Pattern START_SNIPPET_PATTERN = Pattern
			.compile("(?m)\"?\\r?\\n(\\d+),(\\d+),(\\d+),\"?");

	/**
	 * Extracts snippet data from a csv file
	 * 
	 * @param csvFile
	 *            The CSV file to extract triplets from.
	 * @param convertToLowercase
	 *            If <code>true</code> then all text is converted to lowercase
	 *            (in order, for example, to make case-insensitive comparisons
	 *            easier)
	 * @return All snippets in the csv file.
	 * @throws IOException
	 *             when a problem occurs reading <code>csvFile</code>.
	 */
	public static Collection<SnippetData> readSnippetData(final File csvFile,
			final boolean convertToLowercase) throws IOException {
		byte[] bytes = Files.readAllBytes(Paths.get(csvFile.getPath()));
		String fileContents = new String(bytes);
		return readSnippetData(fileContents, convertToLowercase);
	}

	public static Collection<SnippetData> readSnippetData(String fileContents,
			boolean convertToLowercase) {
		Matcher m = START_SNIPPET_PATTERN.matcher(fileContents);
		List<SnippetData> snippets = new ArrayList<>();
		// first snippet
		m.find();
		String patientID = m.group(1);
		String documentID = m.group(2);
		String snippetNumber = m.group(3);
		int snippetTextStart = m.end();
		while (m.find()) {
			// matched next snippet
			String snippetText = fileContents.substring(snippetTextStart,
					m.start());
			if (convertToLowercase) {
				snippetText = snippetText.toLowerCase();
			}
			SnippetData sd = new SnippetData(patientID, documentID,
					snippetNumber, snippetText, snippetTextStart,
					snippetText.length());
			snippets.add(sd);
			patientID = m.group(1);
			documentID = m.group(2);
			snippetNumber = m.group(3);
			snippetTextStart = m.end();
		}
		// add the last snippet
		String snippetText = fileContents.substring(snippetTextStart);
		if (convertToLowercase) {
			snippetText = snippetText.toLowerCase();
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
		SnippetData sd = new SnippetData(patientID, documentID, snippetNumber,
				snippetText, snippetTextStart, snippetText.length());
		snippets.add(sd);
		return snippets;
	}
}
