package gov.va.research.red;

public class MatchedElement {
	private int startPos, endPos;
	private String match;
	private String matchingRegex;
	
	public MatchedElement(int startPos, int endPos, String match, String matchingRegex) {
		this.startPos = startPos;
		this.endPos = endPos;
		this.match = match;
		this.matchingRegex = matchingRegex;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + endPos;
		result = prime * result + ((match == null) ? 0 : match.hashCode());
		result = prime * result + startPos;
		result = prime * result  + ((matchingRegex == null) ? 0 : matchingRegex.hashCode());
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

	@Override
	public String toString() {
		return "" + startPos + "|" + (match == null ? "null" : match) + "|" + endPos + "|" + (matchingRegex == null ? "null" : matchingRegex);
	}
	
	
}