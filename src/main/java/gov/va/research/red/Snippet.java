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
}