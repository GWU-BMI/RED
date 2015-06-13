package gov.va.research.red;


public class MatchedElement {
	private int startPos, endPos;
	private String match;
	private String matchingRegex;
	private double confidence;
	private static final String UNIT_SEPARATOR = "\u001F";
	
	public MatchedElement(int startPos, int endPos, String match, String matchingRegex, double confidence) {
		this.startPos = startPos;
		this.endPos = endPos;
		this.match = match;
		this.matchingRegex = matchingRegex;
		this.confidence = confidence;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + endPos;
		result = prime * result + ((match == null) ? 0 : match.hashCode());
		result = prime * result + startPos;
		result = prime * result  + ((matchingRegex == null) ? 0 : matchingRegex.hashCode());
		long confLb = Double.doubleToLongBits(confidence);
		result = prime * result + (int)(confLb^(confLb>>>32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		MatchedElement other = (MatchedElement) obj;
		if (endPos != other.endPos) {
			return false;
		}
		if (match == null) {
			if (other.match != null) {
				return false;
			}
		} else if (!match.equals(other.match)) {
			return false;			
		}
		if (startPos != other.startPos) {
			return false;
		}
		if (matchingRegex == null) {
			if (other.matchingRegex != null) {
				return false;
			}
		} else if (!matchingRegex.equals(other.matchingRegex)) {
			return false;
		}
		if (confidence != other.confidence) {
			return false;
		}
		return true;
	}
	
	public int getStartPos() {
		return startPos;
	}

	public void setStartPos(int startPos) {
		this.startPos = startPos;
	}

	public int getEndPos() {
		return endPos;
	}

	public void setEndPos(int endPos) {
		this.endPos = endPos;
	}

	public String getMatch() {
		return match;
	}

	public void setMatch(String match) {
		this.match = match;
	}

	public String getMatchingRegex() {
		return matchingRegex;
	}

	public void setMatchingRegex(String matchingRegex) {
		this.matchingRegex = matchingRegex;
	}
	
	public double getConfidence() {
		return confidence;
	}

	public void setConfidence(double confidence) {
		this.confidence = confidence;
	}

	@Override
	public String toString() {
		return "" + startPos + UNIT_SEPARATOR + endPos + UNIT_SEPARATOR + (match == null ? "" : match) + UNIT_SEPARATOR + (matchingRegex == null ? "" : matchingRegex) + UNIT_SEPARATOR + confidence;
	}
	
	public static MatchedElement fromString(String str) {
		Integer startPos = -1;
		Integer endPos = -1;
		String match = null;
		String matchingRegex = null;
		double confidence = -1;
		String[] values = str.split(UNIT_SEPARATOR);
		if (values[0] != null && values[0].length() > 0) {
			startPos = Integer.parseInt(values[0]);
		}
		if (values[1] != null && values[1].length() > 0) {
			endPos = Integer.parseInt(values[1]);
		}
		if (values[2] != null && values[2].length() > 0) {
			match = values[2];
		}
		if (values[3] != null && values[3].length() > 0) {
			matchingRegex = values[3];
		}
		if (values[4] != null && values[4].length() > 0) {
			confidence = Double.parseDouble(values[4]);
		}
		return new MatchedElement(startPos, endPos, match, matchingRegex, confidence);
	}
	
}