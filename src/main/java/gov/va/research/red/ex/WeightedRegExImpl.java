package gov.va.research.red.ex;

import gov.va.research.red.regex.PatternAdapter;
import gov.va.research.red.regex.RE2JPatternAdapter;
import gov.va.research.red.regex.JSEPatternAdapter;

public class WeightedRegExImpl implements WeightedRegEx {
	private String regEx;
	private double weight;
	private transient PatternAdapter pattern;
	
	public WeightedRegExImpl(String regex, double weight) {
		this.regEx = regex;
		this.weight = weight;
	}

	public String getRegEx() {
		return regEx;
	}

	public void setRegEx(String regEx) {
		this.pattern = null;
		this.regEx = regEx;
	}

	public double getWeight() {
		return weight;
	}

	public void setWeight(double weight) {
		this.weight = weight;
	}

	public PatternAdapter getPattern(Class<? extends PatternAdapter> patternAdapterClass) {
		if (pattern == null) {
			if (patternAdapterClass.equals(JSEPatternAdapter.class)) {
				this.pattern = new JSEPatternAdapter(regEx);
			} else if (patternAdapterClass.equals(RE2JPatternAdapter.class)) {
				this.pattern = new RE2JPatternAdapter(regEx);
			} else {
				throw new java.lang.IllegalArgumentException(patternAdapterClass.getName());
			}
		}
		return this.pattern;
	}
}