/*
 *  Copyright 2015 United States Department of Veterans Affairs
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

import gov.va.research.red.LabeledSegment;
import gov.va.research.red.RegEx;
import gov.va.research.red.Snippet;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author vhaislreddd
 *
 */
public class SnippetRegexMatcher {

	/**
	 * @param regex The regular expression to check.
	 * @param snippets The snippets to match the regular expression against.
	 * @param excludeLabels Do not check against snippet labeled segments with these labels.
	 * @return <code>true</code> if there are any matches, <code>false</code> otherwise.
	 */
	static boolean anyMatches(RegEx regex, Collection<Snippet> snippets, Collection<String> excludeLabels) {
		Pattern pattern = REDCategorizer.patternCache.get(regex);
		if (pattern == null) {
			try {
				pattern = Pattern.compile(regex.getRegEx(), Pattern.CASE_INSENSITIVE);
			} catch (Exception e) {
				throw e;
			}
			REDCategorizer.patternCache.put(regex, pattern);
		}
		for (Snippet snippet : snippets) {
			if (snippet.getLabeledSegments() != null) {
				for (LabeledSegment ls : snippet.getLabeledSegments()) {
					if (!excludeLabels.contains(ls.getLabel())) {
						String labeledString = ls.getLabeledString();
						if (labeledString != null && !labeledString.equals("")) {
							Matcher m = pattern.matcher(labeledString);
							if (m.find()) {
								return true;
							}
						}
					}
				}
			} else {
			Matcher m = pattern.matcher(snippet.getText());
				if (m.find()) {
					return true;
				}
			}
		}
		return false;
	}

}
