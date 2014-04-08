package gov.va.research.ree;

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