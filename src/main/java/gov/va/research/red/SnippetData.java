package gov.va.research.red;

public class SnippetData {
	private final String patientID;
	private final String documentID;
	private final String snippetNumber;
	private final String snippetText;
	private final int offset;
	private final int length;
	public SnippetData(String patientID, String documentID,
			String snippetNumber, String snippetText, int offset, int length) {
		super();
		this.patientID = patientID;
		this.documentID = documentID;
		this.snippetNumber = snippetNumber;
		this.snippetText = snippetText;
		this.offset = offset;
		this.length = length;
	}
	public String getPatientID() {
		return patientID;
	}
	public String getDocumentID() {
		return documentID;
	}
	public String getSnippetNumber() {
		return snippetNumber;
	}
	public String getSnippetText() {
		return snippetText;
	}
	public int getOffset() {
		return offset;
	}
	public int getLength() {
		return length;
	}
}
