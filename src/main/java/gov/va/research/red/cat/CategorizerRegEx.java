package gov.va.research.red.cat;

public class CategorizerRegEx {
	private String regEx;
	
	public CategorizerRegEx(String regEx) {
		this.regEx = regEx;
	}

	public String getRegEx() {
		return regEx;
	}

	public void setRegEx(String regEx) {
		this.regEx = regEx;
	}

	@Override
	public String toString() {
		return regEx;
	}
	
}
