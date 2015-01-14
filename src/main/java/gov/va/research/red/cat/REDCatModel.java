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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * @author doug
 *
 */
public class REDCatModel {

	private Map<String, Collection<RegEx>> regexesByCategory;
	private Map<String, Collection<RegExPattern>> patternsByCategory;

	public REDCatModel(Map<String, Collection<RegEx>> regexesByCategory) {
		this.regexesByCategory = regexesByCategory;
	}
	
	public CategoryConfidence categorize(final String text) {
		TreeMap<Double, String> categoryMatches = new TreeMap<>();
		Map<String, Collection<RegExPattern>> patternsByCategory = getPatternsByCategory();
		for (String category : patternsByCategory.keySet()) {
			double score = 0.0;
			for (RegExPattern pattern : patternsByCategory.get(category)) {
				if (pattern.getPattern().matcher(text).find()) {
					score += pattern.getRegEx().getSensitivity();
				}
			}
			categoryMatches.put(Double.valueOf(score), category);
		}
		Entry<Double,String> maxEntry = categoryMatches.lastEntry();
		double confidence = (maxEntry == null ? 0 : maxEntry.getKey().doubleValue());
		String category = (maxEntry == null ? null : maxEntry.getValue());
		CategoryConfidence cc = new CategoryConfidence(category, confidence);
		return cc;
	}

	public Map<String, Collection<RegEx>> getRegexesByCategory() {
		return Collections.unmodifiableMap(regexesByCategory);
	}
	
	private Map<String, Collection<RegExPattern>> getPatternsByCategory() {
		if (patternsByCategory == null) {
			patternsByCategory = new HashMap<>();
			for (String category : regexesByCategory.keySet()) {
				Collection<RegEx> regExes = regexesByCategory.get(category);
				Collection<RegExPattern> patterns = new ArrayList<>(regExes.size());
				for (RegEx regEx : regExes) {
					patterns.add(new RegExPattern(regEx));
				}
				patternsByCategory.put(category, patterns);
			}
		}
		return patternsByCategory;
	}
	
	private class RegExPattern {
		private RegEx regEx;
		private Pattern pattern;
		public RegExPattern(RegEx regEx, Pattern pattern) {
			this.regEx = regEx;
			this.pattern = pattern;
		}
		public RegExPattern(RegEx regEx) {
			this(regEx, null);
		}
		public RegEx getRegEx() {
			return regEx;
		}
		public void setRegEx(RegEx regEx) {
			this.regEx = regEx;
			this.pattern = null;
		}
		public Pattern getPattern() {
			if (pattern == null && regEx != null) {
				pattern = Pattern.compile(regEx.getRegEx());
			}
			return pattern;
		}
	}

	public class CategoryConfidence {
		private String category;
		private double confidence;
		public CategoryConfidence(String category, double confidence) {
			this.category = category;
			this.confidence = confidence;
		}
		public String getCategory() {
			return category;
		}
		public void setCategory(String category) {
			this.category = category;
		}
		public double getConfidence() {
			return confidence;
		}
		public void setConfidence(double confidence) {
			this.confidence = confidence;
		}
		
	}
}
