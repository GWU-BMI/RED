package gov.va.research.red;

public class LSTripletWithSensitivity extends LSTriplet {
	private double sensitivity = 0;
	
	public LSTripletWithSensitivity(LSTriplet triplet) {
		super(triplet.getBLS(), triplet.getLS(), triplet.getALS());
	}

	public double getSensitivity() {
		return sensitivity;
	}

	public void setSensitivity(double sensitivity) {
		this.sensitivity = sensitivity;
	}
	
}
