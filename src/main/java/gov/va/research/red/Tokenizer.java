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
package gov.va.research.red;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author vhaislreddd
 *
 */
public class Tokenizer {
	private static Map<String,Token> cache = new HashMap<>();
	public static Pattern INTEGER_PATTERN = Pattern.compile("\\d+");
	public static Pattern PUNCTUATION_PATTERN = Pattern.compile("(\\p{Punct})\\1*");
	public static Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

//	private static Pattern BRACKET_PATTERN = Pattern.compile("[()<>\\[\\]{}]");
//	private static Pattern MARK_PATTERN = Pattern.compile("[\\+\\-\\*]");
//	private static Pattern SECTION_PATTERN = Pattern.compile("[\\-\\+=_~#]{2,}");
//	private static Pattern VALUE_INDICATOR__PATTERN = Pattern.compile("[=:<>]");

	public static List<Token> tokenize(String string) {
		String[] substringArr = string.split("\\b");
		List<String> substrings = new ArrayList<>(substringArr.length);
		List<Token> tokenList = new ArrayList<>(substringArr.length);
		for (String substring : substringArr) {
			String lcSubstring = substring.toLowerCase();
			if (lcSubstring.length() > 1) {
				Matcher m = PUNCTUATION_PATTERN.matcher(lcSubstring);
				int prevEnd = 0;
				if (m.find()) {
					do {
						if (prevEnd != m.start()) {
							substrings.add(lcSubstring.substring(prevEnd, m.start()));
						}
						substrings.add(m.group());
						prevEnd = m.end();
					} while (m.find());
					substrings.add(lcSubstring.substring(prevEnd));
				} else {
					substrings.add(lcSubstring);
				}
			} else {
				substrings.add(lcSubstring);
			}
		}
		for (String substring : substrings) {
			if (substring != null && substring.length() != 0) {
				Token token = cache.get(substring);
				if (token == null) {
					TokenType type = null;
					if (WHITESPACE_PATTERN.matcher(substring).matches()) {
						type = TokenType.WHITESPACE;
					} else if (INTEGER_PATTERN.matcher(substring).matches()) {
						type = TokenType.INTEGER;
					} else if (PUNCTUATION_PATTERN.matcher(substring).matches()) {
						type = TokenType.PUNCTUATION;
					} else {
						type = TokenType.WORD;
					}
					token = new Token(substring, type);
					cache.put(substring, token);
				}
				tokenList.add(token);
			}
		}
		return tokenList;
	}
}
