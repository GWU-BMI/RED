package gov.va.research.red;

import java.util.Iterator;
import java.util.Set;


public class MatchedElement implements Comparable<MatchedElement> {

	public static class MatchData {
		private String match;
		private Set<String> matchingRegexs;
		private double confidence;

		public MatchData() {
		}

		public String getMatch() {
			return match;
		}

		public void setMatch(String match) {
			this.match = match;
		}

		public Set<String> getMatchingRegexs() {
			return matchingRegexs;
		}

		public void setMatchingRegexs(Set<String> matchingRegexs) {
			this.matchingRegexs = matchingRegexs;
		}

		public double getConfidence() {
			return confidence;
		}

		public void setConfidence(double confidence) {
			this.confidence = confidence;
		}
		
		
		public void combine(MatchData other) {
			if (!(this.match == other.match || this.match.equals(other.match))) {
				throw new IllegalArgumentException("Match strings are different: " + this.match + " != " + other.match);
			}
			this.matchingRegexs.addAll(other.matchingRegexs);
			this.confidence += other.confidence;
		}
		
		@Override
		public String toString() {
			return "MatchData [match=" + match + ", matchingRegexs="
					+ matchingRegexs + ", confidence=" + confidence + "]";
		}
	}

	public static class MatchPos {
		private int startPos;
		private int endPos;

		public MatchPos() {
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

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + endPos;
			result = prime * result + startPos;
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
			MatchPos other = (MatchPos) obj;
			if (endPos != other.endPos)
				return false;
			if (startPos != other.startPos)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "MatchPos [startPos=" + startPos + ", endPos=" + endPos
					+ "]";
		}
	}

	private MatchPos matchPos = new MatchPos();
	private MatchData matchData = new MatchData();
	private static final String UNIT_SEPARATOR = "\u001F";
	
	public MatchedElement(int startPos, int endPos, String match, Set<String> matchingRegexs, double confidence) {
		this.matchPos.setStartPos(startPos);
		this.matchPos.setEndPos(endPos);
		this.matchData.setMatch(match);
		this.matchData.setMatchingRegexs(matchingRegexs);
		this.matchData.setConfidence(confidence);
	}
	
	public MatchedElement(MatchPos pos, MatchData data) {
		this.matchPos = pos;
		this.matchData = data;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + matchPos.getEndPos();
		result = prime * result + ((matchData.getMatch() == null) ? 0 : matchData.getMatch().hashCode());
		result = prime * result + matchPos.getStartPos();
		if (matchData.getMatchingRegexs() != null) {
			for (String mr : matchData.getMatchingRegexs()) {
				result = prime * result  + mr.hashCode();				
			}
		}
		long confLb = Double.doubleToLongBits(matchData.getConfidence());
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
		if (matchPos.getEndPos() != other.matchPos.getEndPos()) {
			return false;
		}
		if (matchData.getMatch() == null) {
			if (other.matchData.getMatch() != null) {
				return false;
			}
		} else if (!matchData.getMatch().equals(other.matchData.getMatch())) {
			return false;			
		}
		if (matchPos.getStartPos() != other.matchPos.getStartPos()) {
			return false;
		}
		if (matchData.getMatchingRegexs() == null) {
			if (other.matchData.getMatchingRegexs() != null) {
				return false;
			}
		} else if (matchData.getMatchingRegexs().size() != other.matchData.getMatchingRegexs().size()) {
			return false;
		} else {
			Iterator<String> it = matchData.getMatchingRegexs().iterator();
			Iterator<String> otherIt = other.matchData.getMatchingRegexs().iterator();
			while (it.hasNext()) {
				if (!(it.next().equals(otherIt.next()))) {
					return false;
				}
			}
		}
		if (matchData.getConfidence() != other.matchData.getConfidence()) {
			return false;
		}
		return true;
	}
	
	public int getStartPos() {
		return matchPos.getStartPos();
	}

	public void setStartPos(int startPos) {
		this.matchPos.setStartPos(startPos);
	}

	public int getEndPos() {
		return matchPos.getEndPos();
	}

	public void setEndPos(int endPos) {
		this.matchPos.setEndPos(endPos);
	}

	public String getMatch() {
		return matchData.getMatch();
	}

	public void setMatch(String match) {
		this.matchData.setMatch(match);
	}

	public Set<String> getMatchingRegexs() {
		return matchData.getMatchingRegexs();
	}

	public void setMatchingRegexs(Set<String> matchingRegexs) {
		this.matchData.setMatchingRegexs(matchingRegexs);
	}
	
	public double getConfidence() {
		return matchData.getConfidence();
	}

	public void setConfidence(double confidence) {
		this.matchData.setConfidence(confidence);
	}

	public MatchPos getMatchPos() {
		return matchPos;
	}

	public void setMatchPos(MatchPos matchPos) {
		this.matchPos = matchPos;
	}

	public MatchData getMatchData() {
		return matchData;
	}

	public void setMatchData(MatchData matchData) {
		this.matchData = matchData;
	}

	@Override
	public String toString() {
		return "MatchedElement [matchPos=" + matchPos + ", matchData="
				+ matchData + "]";
	}

	@Override
	public int compareTo(MatchedElement other) {
		if (this == other) {
			return 0;
		}
		if (other == null) {
			return 1;
		}
		if (matchData.getConfidence() != other.matchData.getConfidence()) {
			return (matchData.getConfidence() - other.matchData.getConfidence() > 0 ? 1 : -1);
		}
		int length = matchPos.getEndPos() - matchPos.getStartPos();
		int otherLength = other.matchPos.getEndPos() - other.matchPos.getStartPos();
		if (length != otherLength) {
			return length - otherLength;
		}
		if (matchData.getMatch() == null) {
			if (other.matchData.getMatch() != null) {
				return -1;
			}
		} else if (!matchData.getMatch().equals(other.matchData.getMatch())) {
			return matchData.getMatch().compareTo(other.matchData.getMatch());
		}
		if (matchPos.getStartPos() != other.matchPos.getStartPos()) {
			return matchPos.getStartPos() - other.matchPos.getStartPos();
		}
		if (matchPos.getEndPos() != other.matchPos.getEndPos()) {
			return matchPos.getEndPos() - other.matchPos.getEndPos();
		}
		if (matchData.getMatchingRegexs() == null) {
			if (other.matchData.getMatchingRegexs() != null) {
				return -1;
			}
		} else if (other.matchData.getMatchingRegexs() == null) {
			return 1;
		} else if (matchData.getMatchingRegexs().size() != other.matchData.getMatchingRegexs().size()) {
			return matchData.getMatchingRegexs().size() - other.matchData.getMatchingRegexs().size();
		} else {
			Iterator<String> it = matchData.getMatchingRegexs().iterator();
			Iterator<String> otherIt = other.matchData.getMatchingRegexs().iterator();
			while (it.hasNext()) {
				int score = it.next().compareTo(otherIt.next());				
				if (score != 0) {
					return score;
				}
			}
		}
		return 0;
	}
}