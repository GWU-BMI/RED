package gov.va.research.red.cat;

import gov.va.research.red.Snippet;

public class CategorizedSnippet {

	private Snippet snippet;
	private Confidence confidence;
	
	public CategorizedSnippet(Snippet snippet, Confidence confidence) {
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
