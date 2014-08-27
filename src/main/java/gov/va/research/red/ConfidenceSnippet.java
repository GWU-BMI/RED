package gov.va.research.red;


public class ConfidenceSnippet {

	private Snippet snippet;
	private Confidence confidence;
	
	public ConfidenceSnippet(Snippet snippet, Confidence confidence) {
		this.snippet = snippet;
		this.confidence = confidence;
	}
	
	public Snippet getSnippet() {
		return snippet;
	}
	public Confidence getConfidence() {
		return confidence;
	}
	
}
