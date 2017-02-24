package gov.va.research.red.ex;

import java.util.regex.Pattern;

public interface WeightedRegEx {
	
	public String getRegEx();
	public double getWeight();
	public void setWeight(double weight);
	public Pattern getPattern();

}