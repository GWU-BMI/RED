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
package gov.va.research.red.ex;

import gov.va.research.red.Token;

import java.util.List;

/**
 * @author vhaislreddd
 * Represents a segment of a snippet.
 */
public class Segment {

	private List<Token> tokens;
	private boolean labeled;
	
	public Segment(List<Token> tokens, boolean labeled) {
		this.tokens = tokens;
		this.labeled = labeled;
	}

	public List<Token> getTokens() {
		return tokens;
	}

	public void setTokens(List<Token> tokens) {
		this.tokens = tokens;
	}

	public boolean isLabeled() {
		return labeled;
	}

	public void setLabeled(boolean labeled) {
		this.labeled = labeled;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Segment)) {
			return false;
		}
		Segment o = (Segment)obj;
		if (this.labeled != o.labeled) {
			return false;
		}
		if (this.tokens == o.tokens) {
			return true;
		}
		if (this.tokens == null || o.tokens == null) {
			return false;
		}
		if (this.tokens.size() != o.tokens.size()) {
			return false;
		}
		for (int i = 0; i < this.tokens.size(); i++) {
			if (!this.tokens.get(i).equals(o.tokens.get(i))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		int hc = 17;
		hc = 31 * hc + (labeled ? 1 : 0);
		if (tokens != null) {
			for (Token t : tokens) {
				hc = 31 * hc + t.hashCode();
			}
		}
		return hc;
	}



}
