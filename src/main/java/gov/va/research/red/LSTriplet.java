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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Value Class for storing Labeled Segments and the surrounding textual context
 */
public class LSTriplet {

	// labeled segment
	private List<Token> LS;
	// before labeled segment
	private List<Token> BLS;
	// after labeled segment
	private List<Token> ALS;

	private double sensitivity = 0.0;

	public LSTriplet(List<Token> BLS, List<Token> LS, List<Token> ALS) {
		this.BLS = new ArrayList<>(BLS);
		this.LS = new ArrayList<>(LS);
		this.ALS = new ArrayList<>(ALS);
	}

	public LSTriplet(LSTriplet ls3) {
		this.BLS = new ArrayList<>(ls3.BLS);
		this.LS = new ArrayList<>(ls3.LS);
		this.ALS = new ArrayList<>(ls3.ALS);
	}

	public LSTriplet(String BLS, String LS, String ALS) {
		this.BLS = BLS == null ? null : Tokenizer.tokenize(BLS);
		this.LS = LS == null ? null : Tokenizer.tokenize(LS);
		this.ALS = ALS == null ? null : Tokenizer.tokenize(ALS);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return String.join(",", (BLS == null ? "null" : BLS.toString()), (LS == null ? "null" : LS.toString()), (ALS == null ? "null" : ALS.toString()));
	}

	/**
	 * to be used only when the triplets contain regular expressions instead of
	 * snippets. Joins the regular expressions contained in the BLS, LS and ALS
	 * into a single string.
	 * 
	 * @return A regex representation of the LSTriplet
	 */
	public String toStringRegEx() {
		StringBuilder regex = new StringBuilder();
		if (BLS != null) {
			for (Token t : BLS) {
				regex.append(t.toRegEx());
			}
		}
		if (LS != null) {
			regex.append("(");
			for (Token t : LS) {
				regex.append(t.toRegEx());
			}
			regex.append(")");
		}
		if (ALS != null) {
			for (Token t : ALS) {
				regex.append(t.toRegEx());
			}
		}
		return regex.toString();
	}

	// ///
	// Getters and Setters

	public List<Token> getBLS() {
		return BLS;
	}

	public void setBLS(List<Token> BLS) {
		this.BLS = new ArrayList<>(BLS);
	}

	public List<Token> getLS() {
		return LS;
	}

	public void setLS(List<Token> LS) {
		this.LS = new ArrayList<>(LS);
	}

	public List<Token> getALS() {
		return ALS;
	}

	public void setALS(List<Token> ALS) {
		this.ALS = new ArrayList<>(ALS);
	}

	public static LSTriplet valueOf(final String snippetText,
			final LabeledSegment labeledSegment) {
		String bls = snippetText.substring(0, labeledSegment.getStart());
		String ls = labeledSegment.getLabeledString();
		String als = snippetText.substring(labeledSegment.getStart()
				+ labeledSegment.getLength());

		LSTriplet ls3 = new LSTriplet(bls, ls, als);
		return ls3;
	}

	public double getSensitivity() {
		return sensitivity;
	}

	public void setSensitivity(double sensitivity) {
		this.sensitivity = sensitivity;
	}

	@Override
	public int hashCode() {
		int hc = 17;
		hc = 31 * hc + (BLS == null ? 0 : BLS.hashCode());
		hc = 31 * hc + (LS == null ? 0 : LS.hashCode());
		hc = 31 * hc + (ALS == null ? 0 : ALS.hashCode());
		return hc;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof LSTriplet)) {
			return false;
		}
		LSTriplet t = (LSTriplet) obj;
		return (BLS == t.BLS || (BLS != null && BLS.equals(t.BLS)))
				&& (LS == t.LS || (LS != null && LS.equals(t.LS)))
				&& (ALS == t.ALS || (ALS != null && ALS.equals(t.ALS)));
	}

	public static class IgnoreCaseComparator implements Comparator<LSTriplet> {
		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		@Override
		public int compare(LSTriplet o1, LSTriplet o2) {
			return String.CASE_INSENSITIVE_ORDER.compare(
					o1 == null ? null : o1.toString(),
					o2 == null ? null : o2.toString());
		}
	}

}
