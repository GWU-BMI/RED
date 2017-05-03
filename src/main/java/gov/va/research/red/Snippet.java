package gov.va.research.red;

import java.util.ArrayList;
import java.util.List;

public class Snippet {

	private String text;
	private List<LabeledSegment> posLabeledSegments;
	private List<LabeledSegment> negLabeledSegments;


	public Snippet(final String text, final List<LabeledSegment> posLabeledSegments, final List<LabeledSegment> negLabeledSegments) {
		this.text = text;
		if (posLabeledSegments == null) {
			this.posLabeledSegments = new ArrayList<>();
		} else {
			this.posLabeledSegments = posLabeledSegments;
		}
		if (negLabeledSegments == null) {
			this.negLabeledSegments = new ArrayList<>();
		} else {
			this.negLabeledSegments = negLabeledSegments;
		}
	}

	/**
	 * Copy constructor.
	 * @param snippet The Snippet to copy.
	 */
	public Snippet(Snippet snippet) {
		this.text = snippet.getText();
		this.posLabeledSegments = new ArrayList<LabeledSegment>(snippet.getPosLabeledSegments().size());
		for (LabeledSegment ls : snippet.getPosLabeledSegments()) {
			this.posLabeledSegments.add(new LabeledSegment(ls));
		}
		for (LabeledSegment ls : snippet.getNegLabeledSegments()) {
			this.negLabeledSegments.add(new LabeledSegment(ls));
		}
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public List<LabeledSegment> getPosLabeledSegments() {
		if (posLabeledSegments == null) {
			posLabeledSegments = new ArrayList<LabeledSegment>();
		}
		return posLabeledSegments;
	}
	
	/**
	 * returns  the labeled segment with the parameter as the label.
	 * @param label The label of the labeled segment to return
	 * @return the first labeled segment with the <code>label</code>
	 */
	public LabeledSegment getPosLabeledSegment(String label) {
		if (posLabeledSegments == null) {
			return null;
		}
		for(LabeledSegment labelsegment : posLabeledSegments){
			if(labelsegment.getLabel() != null && labelsegment.getLabel().equalsIgnoreCase(label)){
				return labelsegment;
			}
		}
		return null;
	}

	public void setPosLabeledSegments(List<LabeledSegment> labeledSegments) {
		this.posLabeledSegments = labeledSegments;
	}

	public List<String> getPosLabeledStrings() {
		List<String> labeledStrings = new ArrayList<>();
		if (this.posLabeledSegments != null) {
			for (LabeledSegment ls : this.posLabeledSegments) {
				labeledStrings.add(ls.getLabeledString());
			}
		}
		return labeledStrings;
	}

	public List<LabeledSegment> getNegLabeledSegments() {
		return negLabeledSegments;
	}

	public List<String> getNegLabeledStrings() {
		List<String> labeledStrings = new ArrayList<>();
		if (this.negLabeledSegments != null) {
			for (LabeledSegment ls : this.negLabeledSegments) {
				labeledStrings.add(ls.getLabeledString());
			}
		}
		return labeledStrings;
	}

	public void setNegLabeledSegments(List<LabeledSegment> negLabeledSegments) {
		this.negLabeledSegments = negLabeledSegments;
	}
	
	@Override
	public String toString() {
		return "text:" + text + ", posLabeledSegments: " + posLabeledSegments + ", negLabeledSegments: " + negLabeledSegments;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((negLabeledSegments == null) ? 0 : negLabeledSegments.hashCode());
		result = prime * result + ((posLabeledSegments == null) ? 0 : posLabeledSegments.hashCode());
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
		if (negLabeledSegments == null) {
			if (other.negLabeledSegments != null)
				return false;
		} else if (!negLabeledSegments.equals(other.negLabeledSegments))
			return false;
		if (posLabeledSegments == null) {
			if (other.posLabeledSegments != null)
				return false;
		} else if (!posLabeledSegments.equals(other.posLabeledSegments))
			return false;
		if (text == null) {
			if (other.text != null)
				return false;
		} else if (!text.equals(other.text))
			return false;
		return true;
	}

}