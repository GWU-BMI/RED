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
package gov.va.research.red.ex;

import gov.va.research.red.MatchedElement;

import java.util.Collection;

/**
 * Interface for Information Extractors
 *
 */
public interface Extractor {

	/**
	 * Extracts information from target strings.
	 * @param target String from which to extract information.
	 * @return List of extracted information, one string for each target in the same order as the targets.
	 */
//	public Collection<MatchedElement> extract(final String target);

}
