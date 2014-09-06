package gov.va.research.red;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Snippet {

	private String text;
	private Collection<LabeledSegment> labeledSegments;

	public Snippet(final String text, final Collection<LabeledSegment> labeledSegments) {
		this.text = text;
		this.labeledSegments = labeledSegments;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public Collection<LabeledSegment> getLabeledSegments() {
		if (labeledSegments == null) {
			labeledSegments = new ArrayList<LabeledSegment>();
		}
		return labeledSegments;
	}
	
	/**
	 * returns  the labeled segment with the parameter as the label.
	 * @param label
	 * @return
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

	public void setLabeledSegments(Collection<LabeledSegment> labeledSegments) {
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
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((labeledSegments == null) ? 0 : labeledSegments.hashCode());
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