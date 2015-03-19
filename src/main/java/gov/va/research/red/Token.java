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

import java.util.regex.Pattern;

/**
 * @author vhaislreddd
 *
 */
public class Token {

	private static final Pattern REGEX_SPECIAL_CHARACTERS_PATTERN = Pattern.compile("[\\.\\^\\$\\*\\+\\?\\(\\)\\[\\{\\\\\\|\\-\\]]");

	private String string;
	private TokenType type;
	
	public Token(String token, TokenType type) {
		this.string = token;
		this.type = type;
	}

	public String getString() {
		return string;
	}

	public TokenType getType() {
		return type;
	}

	@Override
	public int hashCode() {
		int hc = 17;
		hc = hc * 31 + (string == null ? 0 : string.hashCode());
		hc = hc * 31 + (type == null ? 0 : type.hashCode());
		return hc;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Token)) {
			return false;
		}
		Token t = (Token)o;
		return (string == t.string || (string != null && string.equals(t.string)))
				&& (type == t.type || (type != null && type.equals(t.type)));
	}

	@Override
	public String toString() {
		return "" + (string == null ? "null" : string) + "<" + (type == null ? "null" : type.toString()) + ">";
	}
	
	public String toRegex() {
		switch (type) {
		case REGEX: return string;
		default: return REGEX_SPECIAL_CHARACTERS_PATTERN.matcher(string).replaceAll("\\\\$0");
		}
	}
}
