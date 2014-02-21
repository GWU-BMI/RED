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
package gov.va.research.ree;

/**
 * @author vhaislreddd
 *
 */
public class LSTriplet {

	private String LS;
	private String BLS;
	private String ALS;
	
	public LSTriplet(String LS, String BLS, String ALS) {
		this.LS = LS;
		this.BLS = BLS;
		this.ALS = ALS;
	}

	/**
	 * @return the lS
	 */
	public String getLS() {
		return LS;
	}

	/**
	 * @param lS the lS to set
	 */
	public void setLS(String lS) {
		LS = lS;
	}

	/**
	 * @return the bLS
	 */
	public String getBLS() {
		return BLS;
	}

	/**
	 * @param bLS the bLS to set
	 */
	public void setBLS(String bLS) {
		BLS = bLS;
	}

	/**
	 * @return the aLS
	 */
	public String getALS() {
		return ALS;
	}

	/**
	 * @param aLS the aLS to set
	 */
	public void setALS(String aLS) {
		ALS = aLS;
	}
	
	
}
