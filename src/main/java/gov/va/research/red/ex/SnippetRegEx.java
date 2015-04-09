/*
 *  Copyright 2015 United States Department of Veterans Affairs,
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
package gov.va.research.red.ex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Pattern;

import gov.va.research.red.LabeledSegment;
import gov.va.research.red.Snippet;
import gov.va.research.red.Token;
import gov.va.research.red.TokenType;
import gov.va.research.red.Tokenizer;

/**
 * @author doug
 *
 */
public class SnippetRegEx {

	// Snippets are represented as a list of segments. Each segment is a list of tokens.
	// The first segment (at index 0) represents the tokens between the beginning of the snippet and the beginning of the first labeled segment, and may be empty if the first labeled segment starts at the beginning of the snippet.
	// The second segment  (at index 1) represents the tokens in the first labeled segment.
	// The third segment (at index 2) represents the tokens between the end of the first labeled segment and the start of the second labeled segment (or the end of the snippet).
	// etc.
	// In this manner, all segments with an odd index will be labeled segments, and those with an even index will not be labeled segments.
	// If there are no labeled segments, then there will be a single segment at index 0

	private List<List<Token>> segments;
	private Pattern pattern;
	private double sensitivity;
	
	/**
	 * Snippet constructor
	 * @param snippet The Snippet to use for the construction.
	 */
	public SnippetRegEx(Snippet snippet) {
		segments = new ArrayList<List<Token>>(snippet.getLabeledSegments().size() + 1);
		int prevEnd = 0;
		for (LabeledSegment ls : snippet.getLabeledSegments()) {
			String segmentStr = snippet.getText().substring(prevEnd, ls.getStart());
			List<Token> segment = Tokenizer.tokenize(segmentStr);
			segments.add(segment);
			List<Token> labeledSegment = Tokenizer.tokenize(ls.getLabeledString());
			segments.add(labeledSegment);
			prevEnd = ls.getStart() + ls.getLength();
		}
		String segmentStr = snippet.getText().substring(prevEnd);
		List<Token> segment = Tokenizer.tokenize(segmentStr);
		segments.add(segment);
	}

	/**
	 * Copy constructor
	 * @param snippetRegEx The SnippetRegEx to copy.
	 */
	public SnippetRegEx(SnippetRegEx snippetRegEx) {
		segments = new ArrayList<List<Token>>(snippetRegEx.segments.size());
		for (List<Token> segment : snippetRegEx.segments) {
			List<Token> newSegment = new ArrayList<Token>(segment.size());
			for (Token token : segment) {
				newSegment.add(new Token(token));
			}
			segments.add(newSegment);
		}
	}

	@Override
	public int hashCode() {
		return segments.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof SnippetRegEx)) {
			return false;
		}
		SnippetRegEx sre = (SnippetRegEx) obj;
		if (segments.size() != sre.segments.size()) {
			return false;
		}
		for (int s = 0; s < segments.size(); s++) {
			List<Token> segment = segments.get(s);
			List<Token> sreSegment = sre.segments.get(s);
			if (segment != sreSegment) {
				if (segment == null || sreSegment == null || (segment.size() != sreSegment.size())) {
					return false;
				}
				for (int t = 0; t < segment.size(); t++) {
					Token token = segment.get(t);
					Token sreToken = sreSegment.get(t);
					if (token != sreToken && (token == null || !token.equals(sreToken))) {
						return false;
					}
				}
			}
		}
		return true;
	}

	@Override
	public String toString() {
		if (pattern == null) {
			pattern = Pattern.compile(getRegEx());
		}
		return pattern.toString();
	}

	/**
	 * @return The java.lang.String representation of the regular expression.
	 */
	private String getRegEx() {
		StringBuilder regex = new StringBuilder();
		for (List<Token> segment : segments) {
			for (Token token : segment) {
				regex.append(token.toRegEx());
			}
		}
		return regex.toString();
	}
	
	/**
	 * @return The java.util.regex.Pattern representation of the regular expression.
	 */
	public Pattern getPattern() {
		if (pattern == null) {
			pattern = Pattern.compile(getRegEx(), Pattern.CASE_INSENSITIVE);
		}
		return pattern;
	}
	
	/**
	 * @return A list of all labeled segments.
	 */
	public List<List<Token>> getLabeledSegments() {
		if (segments != null && segments.size() > 0) {
			List<List<Token>> lsList = new ArrayList<>((segments.size() - 1) / 2);
			for (int i = 1; i < segments.size(); i+=2) {
				lsList.add(segments.get(i));
			}
			return lsList;
		}
		return null;
	}
	
	/**
	 * Sets all labeled segments to the value of the provided segment.
	 * @param labeledSegment The segment with which to replace all labeled segments.
	 */
	public void setLabeledSegments(List<Token> labeledSegment) {
		if (segments != null && segments.size() > 0) {
			for (int i = 1; i < segments.size(); i+=2) {
				segments.set(i, labeledSegment);
			}
		}
	}
	
	/**
	 * @return A list of all unlabeled segments.
	 */
	public List<List<Token>> getUnlabeledSegments() {
		if (segments != null && segments.size() > 0) {
			List<List<Token>> ulsList = new ArrayList<>(((segments.size() - 1) / 2) + 1);
			for (int i = 0; i < segments.size(); i+=2) {
				ulsList.add(segments.get(i));
			}
			return ulsList;
		}
		return null;
	}

	/**
	 * Counts the frequencies of each unique token in the segments.
	 * @return A collection of unique tokens with their frequencies.
	 */
	public Collection<TokenFreq> getTokenFrequencies() {
		Map<Token,TokenFreq> tokenFreqs = new HashMap<>();
		for (List<Token> segment : segments) {
			for (Token t : segment) {
				if (TokenType.WORD.equals(t.getType()) || TokenType.PUNCTUATION.equals(t.getType())) {
					TokenFreq tf = tokenFreqs.get(t);
					if (tf == null) {
						tf = new TokenFreq(t, Integer.valueOf(1));
						tokenFreqs.put(t, tf);
					} else {
						tf.setFreq(Integer.valueOf(tf.getFreq().intValue() + 1));
					}
				}
			}
		}
		return tokenFreqs.values();
	}

	/**
	 * Replaces all digits in the SnippetRegEx with a generalized regex pattern.
	 * @return <code>true</code> if any changes were made, <code>false</code> otherwise.
	 */
	public boolean replaceDigits() {
		boolean changed = false;
		for (List<Token> segment : segments) {
			ListIterator<Token> lsIt = segment.listIterator();
			while (lsIt.hasNext()) {
				Token t = lsIt.next();
				if (TokenType.INTEGER.equals(t.getType())) {
					lsIt.set(new Token("\\d+", TokenType.REGEX));
					changed = true;
				}
			}
		}
		return changed;
	}

	/**
	 * Replaces all whitespace in the SnippetRegEx with a generalized regex pattern
	 * @return <code>true</code> if any changes were made, <code>false</code> otherwise.
	 */
	public boolean replaceWhiteSpace() {
		boolean changed = false;
		for (List<Token> segment : segments) {
			ListIterator<Token> lsIt = segment.listIterator();
			while (lsIt.hasNext()) {
				Token t = lsIt.next();
				if (TokenType.WHITESPACE.equals(t.getType())) {
					lsIt.set(new Token("\\s{1," + Math.round(t.getString().length() * 1.2) + "}", TokenType.REGEX));
					changed = true;
				}
			}
		}
		return changed;
	}
	
	/**
	 * Removes the first token of the first segment.
	 * @return The token that was removed, or null if the first segment was empty.
	 */
	public Token trimFromBeginning() {
		if (segments != null) {
			if (segments.size() > 0) {
				if (segments.get(0) != null) {
					if (segments.get(0).size() > 0) {
						return segments.get(0).remove(0);
					}
				}
			}
		}
		return null;
	}

	/**
	 * Removes the last token of the last segment.
	 * @return The token that was removed, or null if the last segment was empty.
	 */
	public Token trimFromEnd() {
		if (segments != null) {
			if (segments.size() > 0) {
				int lastSegIdx = segments.size() - 1;
				if (segments.get(lastSegIdx) != null) {
					if (segments.get(lastSegIdx).size() > 0) {
						return segments.get(lastSegIdx).remove(lastSegIdx);
					}
				}
			}
		}
		return null;
	}


	/**
	 * Adds a token to the beginning of the first segment.
	 * @param token The token to add.
	 */
	public void addToBeginning(Token token) {
		if (segments != null) {
			if (segments.size() > 0) {
				if (segments.get(0) == null) {
					segments.set(0, new LinkedList<>());
				}
			} else {
				segments.add(new LinkedList<>());
			}
		} else {
			segments = new ArrayList<List<Token>>();
			segments.add(new LinkedList<>());
		}
		segments.get(0).add(0, token);		
	}
	
	/**
	 * Adds a token to the end of the last segment.
	 * @param token The token to add.
	 */
	public void addToEnd(Token token) {
		if (segments != null) {
			if (segments.size() > 0) {
				int lastSegIdx = segments.size() - 1;
				if (segments.get(lastSegIdx) == null) {
					segments.set(lastSegIdx, new LinkedList<>());
				}
			} else {
				segments.add(new LinkedList<>());
			}
		} else {
			segments = new ArrayList<List<Token>>();
			segments.add(new LinkedList<>());
		}
		segments.get(segments.size() - 1).add(token);
	}
	
	/**
	 * @return The length of the first segment.
	 */
	public int getFirstSegmentLength() {
		if (segments == null) {
			return 0;
		}
		if (segments.size() == 0) {
			return 0;
		}
		if (segments.get(0) == null) {
			return 0;
		}
		return segments.get(0).size();
	}
	
	/**
	 * @return The length of the last segment.
	 */
	public int getLastSegmentLength() {
		if (segments == null) {
			return 0;
		}
		if (segments.size() == 0) {
			return 0;
		}
		int lastSegIdx = segments.size() - 1;
		if (segments.get(lastSegIdx) == null) {
			return 0;
		}
		return segments.get(lastSegIdx).size();
	}
	
	class TokenFreq implements Comparable<TokenFreq> {
		private Token token;
		private Integer freq;

		public TokenFreq(Token token, Integer freq) {
			this.token = token;
			this.freq = freq;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		@Override
		public int compareTo(TokenFreq o) {
			return ((freq == null ? Integer.MIN_VALUE : freq) - (o.freq == null ? Integer.MIN_VALUE : o.freq));
		}

		public Token getToken() {
			return token;
		}

		public void setToken(Token token) {
			this.token = token;
		}

		public Integer getFreq() {
			return freq;
		}

		public void setFreq(Integer freq) {
			this.freq = freq;
		}
	}

	/**
	 * Sets the sensitivity of this regular expression.
	 * @param sensitivity The sensitivity of this regular expression.
	 */
	public void setSensitivity(double sensitivity) {
		this.sensitivity = sensitivity;
	}
	
	/**
	 * @return The sensitivity of this regular expression.
	 */
	public double getSensitivity() {
		return this.sensitivity;
	}
}
