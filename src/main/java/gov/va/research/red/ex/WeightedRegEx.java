package gov.va.research.red.ex;

import gov.va.research.red.regex.PatternAdapter;

public interface WeightedRegEx {
	
	public String getRegEx();
	public double getWeight();
	public void setWeight(double weight);
	public PatternAdapter getPattern(Class<? extends PatternAdapter> patternAdapterClass);

}