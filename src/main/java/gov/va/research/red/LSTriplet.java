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


/**
 * Value Class for storing Labeled Segments and the surrounding textual context
 */
public class LSTriplet {

	// labeled segment
	private String LS;
	// before labeled segment
	private String BLS;
	// after labeled segment
	private String ALS;
	
	public LSTriplet(String BLS, String LS, String ALS) {
		this.BLS = BLS;
		this.LS = LS;
		this.ALS = ALS;
	}
	
	public LSTriplet(String triplet) {
		String[] elem = triplet.split(" ");
		this.BLS = elem[0];
		this.LS = elem[1];
		if(elem.length == 3)
			this.ALS = elem[2];
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "" + BLS + " " + LS  + " " + ALS;
	}
	
	/**
	 * to be used only when the triplets contain regular expressions
	 * instead of snippets. Joins the regular expressions contained
	 * in the BLS, LS and ALS into a single string.
	 * @return
	 */
	public String toStringRegEx(){
		return "" + BLS + "(" + LS  + ")" + ALS;
	}

	/////
	// Getters and Setters

	public String getBLS() {
		return BLS;
	}

	public void setBLS(String BLS) {
		this.BLS = BLS;
	}

	public String getLS() {
		return LS;
	}

	public void setLS(String LS) {
		this.LS = LS;
	}

	public String getALS() {
		return ALS;
	}

	public void setALS(String ALS) {
		this.ALS = ALS;
	}
	
	public static LSTriplet valueOf(final String snippetText, final LabeledSegment labeledSegment) {
		String bls = null;
		try {
			bls = snippetText.substring(0, labeledSegment.getStart());
		} catch (Exception e) {
			// probably java.lang.StringIndexOutOfBoundsException from a bad annotation
			e.printStackTrace();
		}
		String ls = labeledSegment.getLabeledString();
		String als = snippetText.substring(labeledSegment.getStart() + labeledSegment.getLength());

		LSTriplet ls3 = new LSTriplet((bls == null ? null : bls), ls, (als == null ? null : als));
		return ls3;
	}
}
