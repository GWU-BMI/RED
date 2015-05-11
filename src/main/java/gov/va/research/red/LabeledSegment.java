package gov.va.research.red;

public class LabeledSegment {
	private String label;
	private String labeledString;
	private int start;
	private int length;
	
	public LabeledSegment(final String label, final String labeledSegment, final int start, final int length) {
		this.label = label;
		this.labeledString = labeledSegment;
		this.start = start;
		this.length = length;
	}
	
	/**
	 * Copy constructor
	 * @param ls The LabeledSegment to copy.
	 */
	public LabeledSegment(LabeledSegment ls) {
		this.label = ls.getLabel();
		this.labeledString = ls.getLabeledString();
		this.start = ls.getStart();
		this.length = ls.getLength();
	}

	public String getLabel() {
		return label;
	}
	public void setLabel(String label) {
		this.label = label;
	}
	public String getLabeledString() {
		return labeledString;
	}
	public void setLabeledString(String labeledString) {
		this.labeledString = labeledString;
	}
	public int getStart() {
		return start;
	}
	public void setStart(int start) {
		this.start = start;
	}
	public int getLength() {
		return length;
	}
	public void setLength(int length) {
		this.length = length;
	}

	@Override
	public String toString() {
		return "{label:" + label + ",labeledString:" + labeledString + ",start:" + start + ",length:" + length + "}";
	}

	@Override
	public int hashCode() {
		int result = 17;
		result = 31 * result + (label == null ? 0 : label.hashCode());
		result = 31 * result + (labeledString == null ? 0 : labeledString.hashCode());
		result = 31 * result + start;
		result = 31 * result + length;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof LabeledSegment)) {
			return false;
		}
		LabeledSegment ls = (LabeledSegment)obj;
		return (label == ls.label || (label != null && label.equals(ls.label)))
				&&
				(labeledString == ls.labeledString || (labeledString != null && labeledString.equals(ls.labeledString)))
				&&
				(start == ls.start)
				&&
				(length == ls.length);
	}

	/**
	 * Determines if a labeled segment overlaps with this one.
	 * @param ls A labeled segment.
	 * @return <code>true</code> if the labeled segments overlap, <code>false</code> otherwise.
	 */
	public boolean overlaps(LabeledSegment ls) {
		if (ls == null) {
			return false;
		}
		int thisEnd = this.start + this.length;
		int lsEnd = ls.start + ls.length;
		return (this.start >= ls.start && this.start <= lsEnd) || (thisEnd >= ls.start && thisEnd <= lsEnd);
	}
	
}
