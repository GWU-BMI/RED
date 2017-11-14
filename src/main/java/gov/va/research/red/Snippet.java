package gov.va.research.red;

import java.util.ArrayList;
import java.util.List;

public class Snippet {

	private String text;
	private List<LabeledSegment> labeledSegments;


	public Snippet(final String text, final List<LabeledSegment> labeledSegments) {
		this.text = text;
		if (labeledSegments == null) {
			this.labeledSegments = new ArrayList<>();
		} else {
			this.labeledSegments = labeledSegments;
		}
	}

	/**
	 * Copy constructor.
	 * @param snippet The Snippet to copy.
	 */
	public Snippet(Snippet snippet) {
		this.text = snippet.getText();
		this.labeledSegments = new ArrayList<LabeledSegment>(snippet.getLabeledSegments().size());
		for (LabeledSegment ls : snippet.getLabeledSegments()) {
			this.labeledSegments.add(new LabeledSegment(ls));
		}
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public List<LabeledSegment> getLabeledSegments() {
		if (labeledSegments == null) {
			labeledSegments = new ArrayList<LabeledSegment>();
		}
		return labeledSegments;
	}
	
	/**
	 * returns  the labeled segment with the parameter as the label.
	 * @param label The label of the labeled segment to return
	 * @return the first labeled segment with the <code>label</code>
	 */
	public LabeledSegment getLabeledSegment(String label) {
		if (labeledSegments == null) {
			return null;
		}
		for(LabeledSegment labelsegment : labeledSegments){
			if(labelsegment.getLabel() != null && labelsegment.getLabel().equalsIgnoreCase(label)){
				return labelsegment;
			}
		}
		return null;
	}

	public void setLabeledSegments(List<LabeledSegment> labeledSegments) {
		this.labeledSegments = labeledSegments;
	}

	public List<String> getLabeledStrings() {
		List<String> labeledStrings = new ArrayList<>();
		if (this.labeledSegments != null) {
			for (LabeledSegment ls : this.labeledSegments) {
				labeledStrings.add(ls.getLabeledString());
			}
		}
		return labeledStrings;
	}


	@Override
	public String toString() {
		return "text:" + text + ", labeledSegments: " + labeledSegments;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((labeledSegments == null) ? 0 : labeledSegments.hashCode());
		result = prime * result + ((text == null) ? 0 : text.hashCode());
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
		Snippet other = (Snippet) obj;
		if (labeledSegments == null) {
			if (other.labeledSegments != null)
				return false;
		} else if (!labeledSegments.equals(other.labeledSegments))
			return false;
		if (text == null) {
			if (other.text != null)
				return false;
		} else if (!text.equals(other.text))
			return false;
		return true;
	}

}