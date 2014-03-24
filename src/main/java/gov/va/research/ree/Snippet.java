package gov.va.research.ree;

public class Snippet {
	private String text;
	private String label;
	private String labeledSegment;
	private int labeledSegmentStart;
	private int labeledSegmentLength;

	public Snippet(final String text, final String label,
			final String labeledSegment, final int labeledSegmentStart,
			final int labeledSegmentLength) {
		this.text = text;
		this.label = label;
		this.labeledSegment = labeledSegment;
		this.labeledSegmentStart = labeledSegmentStart;
		this.labeledSegmentLength = labeledSegmentLength;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getLabeledSegment() {
		return labeledSegment;
	}

	public void setLabeledSegment(String labeledSegment) {
		this.labeledSegment = labeledSegment;
	}

	public int getLabeledSegmentStart() {
		return labeledSegmentStart;
	}

	public void setLabeledSegmentStart(int labeledSegmentStart) {
		this.labeledSegmentStart = labeledSegmentStart;
	}

	public int getLabeledSegmentLength() {
		return labeledSegmentLength;
	}

	public void setLabeledSegmentLength(int labeledSegmentLength) {
		this.labeledSegmentLength = labeledSegmentLength;
	}
}