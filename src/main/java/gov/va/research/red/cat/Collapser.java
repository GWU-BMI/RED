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
package gov.va.research.red.cat;

import gov.va.research.red.RegEx;
import gov.va.research.red.Snippet;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author doug
 *
 */
public class Collapser {

	private static final Pattern COLLAPSIBLE_PATTERN = Pattern.compile("(?:\\[A-Z\\]\\{1,\\d+\\}(?:\\\\p\\{Punct\\})?\\\\s\\{1,10\\}){2,}",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern WORD_PATTERN = Pattern.compile("\\[A-Z\\]\\{1,(\\d+)\\}");
	
	/**
	 * Collapsed repeated generic word regexes separated by whitespace or punctuation into a single generic word regex with repetition.
	 * Checks for resulting false positives and reverts any changes that cause them.
	 * @param regEx The regular expression to collapse.
	 * @param negSnippets A collection of snippets with labeled segments that should not match the regex unless they are labeled with one of <code>posLabels</code>.
	 * @param posLabels A collection of labels that identifying labeled segments that should match the <code>regEx</code>.
	 */
	public static void collapse(RegEx regEx, Collection<Snippet> negSnippets, Collection<String> posLabels) {
		StringBuilder newRE = collapse(regEx);
		boolean fps = SnippetRegexMatcher.anyMatches(new RegEx(newRE.toString()), negSnippets, posLabels);
		if (!fps) {
			regEx.setRegEx(newRE.toString());
		}

	}

	/**
	 * Collapsed repeated generic word regexes separated by whitespace or punctuation into a single generic word regex with repetition.
	 * @param regEx The regular expression to collapse.
	 */
	static StringBuilder collapse(RegEx regEx) {
		Matcher m = COLLAPSIBLE_PATTERN.matcher(regEx.getRegEx());
		StringBuilder newRE = new StringBuilder();
		if (m.find()) {
			int prevEnd = 0;
			do {
				newRE.append(regEx.getRegEx().substring(prevEnd, m.start()));
				Matcher subm = WORD_PATTERN.matcher(m.group());
				int maxlen = 0;
				int matches = 0;
				while (subm.find()) {
					String lenStr = subm.group(1);
					int length = Integer.valueOf(lenStr);
					if (maxlen < length) {
						maxlen = length;
					}
					matches++;
				}
				int maxWords = (int)(matches + (matches * .2)); // Add 20% for more generalizability
				int maxWordLen = (int)(maxlen + (maxlen * .2));
				newRE.append("(?:[A-Z]{1," + maxWordLen + "}(?:\\s{1,10}|\\p{Punct})){1," + maxWords + "}");
				prevEnd = m.end();
			} while (m.find());
			newRE.append(regEx.getRegEx().substring(prevEnd));
		}
		return newRE;
	}

}
