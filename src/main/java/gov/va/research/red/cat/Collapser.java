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
import java.util.regex.PatternSyntaxException;

/**
 * @author doug
 *
 */
public class Collapser {

	private static final Pattern COLLAPSIBLE_PATTERN = Pattern.compile("(?:\\[A-Z\\]\\{1,\\d+\\}(?:\\\\p\\{Punct\\})?\\\\s\\{1,10\\}){2,}"/*"(?:\\[A-Z \\](?!\\{))+"*/,
			Pattern.CASE_INSENSITIVE);
	private static final Pattern WORD_PATTERN = Pattern.compile("\\[A-Z\\]\\{1,(\\d+)\\}");
	
	public static void collapse(RegEx regEx, Collection<Snippet> negSnippets, Collection<String> posLabels) {
		Matcher m = COLLAPSIBLE_PATTERN.matcher(regEx.getRegEx());
		if (m.find()) {
			StringBuilder newRE = new StringBuilder();
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
			boolean fps = SnippetRegexMatcher.anyMatches(new RegEx(newRE.toString()), negSnippets, posLabels);
			if (!fps) {
				regEx.setRegEx(newRE.toString());
			} else {
				System.err.println("FP on collapse");
				System.err.println("<:" + regEx.getRegEx());
				System.err.println(">:" + newRE.toString());
			}
		}
	}

}
