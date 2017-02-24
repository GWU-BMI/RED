package gov.va.research.red.ex;

import java.util.regex.Pattern;

public class WeightedRegExImpl implements WeightedRegEx {
	private String regEx;
	private double weight;
	private transient Pattern pattern;
	
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

	public Pattern getPattern() {
		if (pattern == null) {
			pattern = Pattern.compile(regEx);
		}
		return pattern;
	}
}