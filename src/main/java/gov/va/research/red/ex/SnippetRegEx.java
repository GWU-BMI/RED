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
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private static final Logger LOG = LoggerFactory.getLogger(SnippetRegEx.class);

	// Snippets are represented as a list of segments. Each segment is a list of tokens with a segment type.
	private static final String DIGIT_TEXT = "zero|one|two|three|four|five|six|seven|eight|nine|ten";
	private static final Pattern DIGIT_TEXT_PATTERN = Pattern.compile(DIGIT_TEXT);
	private List<Segment> segments;
	private Pattern pattern;
	private double sensitivity;
	
	/**
	 * Snippet constructor
	 * @param snippet The Snippet to use for the construction.
	 */
	public SnippetRegEx(Snippet snippet) {
		segments = new ArrayList<Segment>(snippet.getPosLabeledSegments().size() + 2);
		int prevEnd = 0;
		for (LabeledSegment ls : snippet.getPosLabeledSegments()) {
			if (ls.getStart() < prevEnd) {
				LOG.debug("Overlapping labeled segments found, skipping all but the first.");
				continue;
			}
			String segmentStr;
			try {
				segmentStr = snippet.getText().substring(prevEnd, ls.getStart());
				List<Token> tokens = Tokenizer.tokenize(segmentStr);
				segments.add(new Segment(tokens, false));
				tokens = Tokenizer.tokenize(ls.getLabeledString());
				segments.add(new Segment(tokens, true));
				prevEnd = ls.getStart() + ls.getLength();
			} catch (Exception e) {
				LOG.error(e.getMessage() + "\n<Snippet Text>\n" + snippet.getText() + "\n</SnippetText>\nstartIdx = " + prevEnd + "\nendIdx = " + ls.getStart());
			}
		}
		String segmentStr = snippet.getText().substring(prevEnd);
		List<Token> tokens = Tokenizer.tokenize(segmentStr);
		segments.add(new Segment(tokens, false));
	}

	/**
	 * Copy constructor
	 * @param snippetRegEx The SnippetRegEx to copy.
	 */
	public SnippetRegEx(SnippetRegEx snippetRegEx) {
		segments = new ArrayList<Segment>(snippetRegEx.segments.size());
		for (Segment segment : snippetRegEx.segments) {
			List<Token> newTokens = new ArrayList<Token>(segments.size());
			for (Token token : segment.getTokens()) {
				newTokens.add(new Token(token));
			}
			segments.add(new Segment(newTokens, segment.isLabeled()));
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
			Segment segment = segments.get(s);
			Segment sreSegment = sre.segments.get(s);
			if (segment != sreSegment) {
				if (segment == null || sreSegment == null || (segment.getTokens().size() != sreSegment.getTokens().size()) || (segment.isLabeled() != sreSegment.isLabeled())) {
					return false;
				}
				for (int t = 0; t < segment.getTokens().size(); t++) {
					Token token = segment.getTokens().get(t);
					Token sreToken = sreSegment.getTokens().get(t);
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
			pattern = getPattern(true);
		}
		return pattern.toString();
	}

	/**
	 * @return The java.lang.String representation of the regular expression.
	 */
	private String getRegEx(boolean caseInsensitive) {
		StringBuilder regex = new StringBuilder(caseInsensitive ? "(?i)" : "");
		StringBuilder segmentSB = new StringBuilder();
		for (Segment segment : segments) {
			segmentSB.setLength(0);
			for (Token token : segment.getTokens()) {
				segmentSB.append(token.toRegEx());
			}
			String segmentStr = segmentSB.toString();
			if (segment.isLabeled()) {
				// A capture group looking like ((?:whatever)) does not work, so remove the (?: piece
				if (segmentStr.startsWith("(?:") && segmentStr.endsWith(")")) {
					segmentStr = "(" + segmentStr.substring(3);
				}
				regex.append("(" + segmentStr + ")");
			} else {
				regex.append(segmentStr);
			}
		}
		return regex.toString();
	}
	
	/**
	 * @param caseInsensitive if <code>true</code> then the pattern is case-insensitive.
	 * @return The java.util.regex.Pattern representation of the regular expression.
	 */
	public Pattern getPattern(boolean caseInsensitive) {
		if (pattern == null) {
			String regex = getRegEx(caseInsensitive);
			pattern = Pattern.compile(regex);
		}
		return pattern;
	}
	
	/**
	 * @return A list of all labeled segments.
	 */
	public List<Segment> getLabeledSegments() {
		if (segments != null && segments.size() > 0) {
			List<Segment> lsList = new ArrayList<>((segments.size() - 1) / 2);
			for (Segment segment : segments) {
				if (segment.isLabeled())
				lsList.add(segment);
			}
			return lsList;
		}
		return null;
	}
	
	/**
	 * Sets all labeled segments to the value of the provided segment.
	 * @param labeledSegment The segment with which to replace all labeled segments.
	 */
	public void setLabeledSegments(Segment labeledSegment) {
		if (segments != null && segments.size() > 0) {
			ListIterator<Segment> segInt = segments.listIterator();
			while (segInt.hasNext()) {
				Segment s = segInt.next();
				if (s.isLabeled()) {
					segInt.set(labeledSegment);
				}
			}
		} else {
			segments = new ArrayList<>(1);
			segments.add(labeledSegment);
		}
	}
	
	/**
	 * @return A list of all unlabeled segments.
	 */
	public List<Segment> getUnlabeledSegments() {
		if (segments != null && segments.size() > 0) {
			List<Segment> ulsList = new ArrayList<>(((segments.size() - 1) / 2) + 1);
			for (Segment segment : segments) {
				if (!segment.isLabeled()) {
					ulsList.add(segment);
				}
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
		for (Segment segment : segments) {
			for (Token t : segment.getTokens()) {
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
		for (Segment segment : segments) {
			ListIterator<Token> lsIt = segment.getTokens().listIterator();
			while (lsIt.hasNext()) {
				Token t = lsIt.next();
				if (TokenType.INTEGER.equals(t.getType()) || DIGIT_TEXT_PATTERN.matcher(t.getString()).matches()) {
					lsIt.set(new Token("(?:\\d+" + (segment.isLabeled() ? "|" : "?|") + DIGIT_TEXT + ")", TokenType.REGEX));
					changed = true;
				}
			}
		}
		return changed;
	}

	/**
	 * Replaces all punctuation marks in the SnippetRegEx with corresponding generalized regex patterns.
	 * @return <code>true</code> if any changes were made, <code>false</code> otherwise.
	 */
	public boolean replacePunct() {
		boolean changed = false;
		for (Segment segment : segments) {
			ListIterator<Token> lsIt = segment.getTokens().listIterator();
			while (lsIt.hasNext()) {
				Token t = lsIt.next();
				if (TokenType.PUNCTUATION.equals(t.getType())) {
					Token newToken = null;
					switch (t.getString()) {
					case "(":
					case "[":
					case "{":
						newToken = new Token("[\\(\\[\\{]", TokenType.REGEX);
						break;
					case ")":
					case "]":
					case "}":
						newToken = new Token("[\\)\\]\\}]", TokenType.REGEX);
						break;
					case ".":
					case ",":
					case ";":
						newToken = new Token("[\\.,;]", TokenType.REGEX);
						break;
					case ":":
					case "=":
						newToken = new Token("[=:]", TokenType.REGEX);
						break;
					case "<":
					case ">":
						newToken = new Token("[<>]", TokenType.REGEX);
						break;
					case "\"":
					case "'":
					case "`":
						newToken = new Token("[\"'`]", TokenType.REGEX);
						break;
					default:
						newToken = null;
					}
					if (newToken != null) {
						lsIt.set(newToken);
						changed = true;
					}
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
		for (Segment segment : segments) {
			ListIterator<Token> lsIt = segment.getTokens().listIterator();
			while (lsIt.hasNext()) {
				Token t = lsIt.next();
				if (TokenType.WHITESPACE.equals(t.getType())) {
					lsIt.set(new Token("\\s{1," + ((int)Math.ceil(t.getString().length() * 2.1)) + "}?", TokenType.REGEX));
					changed = true;
				}
			}
		}
		return changed;
	}

	public Token trimFromBeginning() {
		return getBeginningToken(true);
	}
	
	public Token trimFromEnd() {
		return getEndToken(true);
	}
	
	public Token getBeginningToken() {
		return getBeginningToken(false);
	}
	
	public Token getEndToken() {
		return getEndToken(false);
	}

	/**
	 * Gets or Removes the first token from the first segment.
	 * @return The first token, or null if the first segment was empty.
	 */
	Token getBeginningToken(final boolean remove) {
		if (segments != null) {
			if (segments.size() > 0) {
				if (segments.get(0) != null) {
					if (segments.get(0).getTokens() != null) {
						if (segments.get(0).getTokens().size() > 0) {
							if (remove) {
								return segments.get(0).getTokens().remove(0);
							} else {
								return segments.get(0).getTokens().get(0);
							}
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Gets or Removes the last token of the last segment.
	 * @return The last token, or null if the last segment was empty.
	 */
	Token getEndToken(final boolean remove) {
		if (segments != null) {
			if (segments.size() > 0) {
				int lastSegIdx = segments.size() - 1;
				Segment lastSeg = segments.get(lastSegIdx);
				if (lastSeg != null && lastSeg.getTokens() != null) {
					int lastSegSize = lastSeg.getTokens().size();
					if (lastSegSize > 0) {
						if (remove) {
							return lastSeg.getTokens().remove(lastSegSize - 1);
						} else {
							return lastSeg.getTokens().get(lastSegSize - 1);
						}
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
					segments.set(0, new Segment(new ArrayList<Token>(), false));
				}
			} else {
				segments.add(new Segment(new ArrayList<Token>(), false));
			}
		} else {
			segments = new ArrayList<Segment>();
			segments.add(new Segment(new ArrayList<Token>(), false));
		}
		segments.get(0).getTokens().add(0, token);		
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
					segments.set(lastSegIdx, new Segment(new ArrayList<>(), false));
				}
			} else {
				segments.add(new Segment(new ArrayList<>(), false));
			}
		} else {
			segments = new ArrayList<Segment>();
			segments.add(new Segment(new ArrayList<>(), false));
		}
		segments.get(segments.size() - 1).getTokens().add(token);
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
		if (segments.get(0).getTokens() == null) {
			return 0;
		}
		return segments.get(0).getTokens().size();
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
		if (segments.get(lastSegIdx).getTokens() == null) {
			return 0;
		}
		return segments.get(lastSegIdx).getTokens().size();
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
