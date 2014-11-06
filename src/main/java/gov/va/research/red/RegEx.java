package gov.va.research.red;

public class RegEx {
	private String regEx;
	private double specifity = 0.0;
	
	public RegEx(String regEx) {
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

	public double getSpecifity() {
		return specifity;
	}

	public void setSpecifity(double specifity) {
		this.specifity = specifity;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((regEx == null) ? 0 : regEx.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RegEx other = (RegEx) obj;
		if (regEx == null) {
			if (other.regEx != null)
				return false;
		} else if (!regEx.equals(other.regEx))
			return false;
		return true;
	}
	
}
