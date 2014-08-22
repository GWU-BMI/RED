package gov.va.research.red.cat;

public class Confidence {
	
	private int confidence;
	private ConfidenceType confidenceType;
	
	public Confidence(int confidence, ConfidenceType confidenceType) {
		this.confidence = confidence;
		this.confidenceType = confidenceType;
	}

	public int getConfidence() {
		return confidence;
	}

	public ConfidenceType getConfidenceType() {
		return confidenceType;
	}
	
}
