package gov.va.research.redex;

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
}
