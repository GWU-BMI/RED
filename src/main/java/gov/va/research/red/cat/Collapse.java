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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author doug
 *
 */
public class Collapse {

	private static final Pattern ALPHA_WHITESPACE_PUNCT_PATTERN = Pattern.compile("(?:\\\\s\\{1,10\\}|\\\\p\\{Punct\\})*\\[A-Z ?\\](?!\\{)(?:\\\\s\\{1,10\\}|\\\\p\\{Punct\\})*", Pattern.CASE_INSENSITIVE);
	private static final Pattern ALPHA_WHITESPACE_PATTERN = Pattern.compile("(?:\\\\s\\{1,10\\})*\\[A-Z ?\\](?!\\{)(?:\\\\s\\{1,10\\})*", Pattern.CASE_INSENSITIVE);
	private static final Pattern ALPHS_SPACE_PATTERN = Pattern.compile("(?:\\[A-Z \\](?!\\{))+", Pattern.CASE_INSENSITIVE);

	public static void overallCollapser(RegEx regEx) {
		collapse(regEx, ALPHS_SPACE_PATTERN, "[A-Za-z ]{1,", "}");
	}
	
	public static void collapsePunct(RegEx regEx) {
		collapse(regEx, ALPHA_WHITESPACE_PUNCT_PATTERN, "[A-Za-z \\p{Punct}]{1,", "}");
	}
	
	public static void collapseWhitespace(RegEx regEx) {
		collapse(regEx, ALPHA_WHITESPACE_PATTERN, "[A-Za-z ]{1,", "}");
	}
	
	static void collapse(RegEx regEx, Pattern pattern, String replacementBegin, String replacementEnd) {
		String regStr = regEx.getRegEx();
		Matcher matcher = pattern.matcher(regStr);
		int prevMatchEnd = 0;
		StringBuilder regSB = new StringBuilder();
		while (matcher.find()) {
			// rough estimate of the maximum length to allow in replacement regex
			int maxLength = matcher.end() - matcher.start();
			regSB.append(regStr.substring(prevMatchEnd, matcher.start()));
			regSB.append(replacementBegin + maxLength + replacementEnd);
			prevMatchEnd = matcher.end();
		}
		regSB.append(regStr.substring(prevMatchEnd));
		regEx.setRegEx(regSB.toString());
	}

}
