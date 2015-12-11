package gov.va.research.red;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public class CVScore {
	private int tp;
	private int tn;
	private int fp;
	private int fn;

	public CVScore(){
		init();
	};

	public CVScore(final int tp, final int tn, final int fp, final int fn) {
		this.tp = tp;
		this.tn = tn;
		this.fp = fp;
		this.fn = fn;
	}

	public void init() {
		this.tp = 0;
		this.tn = 0;
		this.fp = 0;
		this.fn = 0;
	}
	
	public String getEvaluation() throws IOException {
		String cm = null;
		try (
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw)) {
			pw.println("\tT\tF\t< Actual");
			pw.println("T\t" + this.tp + "\t" + this.fp);
			pw.println("F\t" + this.fn + "\t" + this.tn);
			pw.println("^");
			pw.println("Predicted");
			pw.println();
			pw.println("Precision (PPV)\t" + calcPrecision());
			pw.println("Recall (Sens.,TPR)\t" + calcRecall());
			pw.println("Specificity (TNR)\t" + calcSpecificity()); 
			pw.println("F1-score\t" + calcF1());
			pw.println("Accuracy\t" + calcAccuracy());
			cm = sw.toString();
		}
		return cm;
	}
	
	public float calcPrecision() {
		return ((float)this.tp) / (this.tp + this.fp);
	}
	
	public float calcRecall() {
		return ((float)this.tp) / (this.tp + this.fn);
	}

	public float calcSpecificity() {
		return ((float)this.tn/(this.fp + this.tn));
	}
	
	public float calcF1() {
		float prec = calcPrecision();
		float rec = calcRecall();
		return (2 * ((prec * rec)/(prec + rec)));
	}
	
	public float calcAccuracy() {
		return (float)(this.tp + this.tn)/(this.tp + this.tn + this.fp + this.fn);
	}

	public int getTp() {
		return tp;
	}

	public void setTp(int tp) {
		this.tp = tp;
	}

	public synchronized void incrementTp() {
		this.tp++;
	}

	public int getTn() {
		return tn;
	}

	public void setTn(int tn) {
		this.tn = tn;
	}

	public synchronized void incrementTn() {
		this.tn++;
	}

	public int getFp() {
		return fp;
	}

	public void setFp(int fp) {
		this.fp = fp;
	}

	public synchronized void incrementFp() {
		this.fp++;
	}

	public int getFn() {
		return fn;
	}

	public void setFn(int fn) {
		this.fn = fn;
	}

	public synchronized void incrementFn() {
		this.fn++;
	}

	public CVScore add(CVScore cvs) {
		if (cvs != null) {
			this.tp += cvs.tp;
			this.tn += cvs.tn;
			this.fp += cvs.fp;
			this.fn += cvs.fn;
		}
		return this;
	}

	public static CVScore aggregate(List<CVScore> scores) {
		CVScore aggregate = new CVScore(0,0,0,0);
		synchronized(scores) {
			for (CVScore score : scores) {
				aggregate.add(score);
			}
		}
		return aggregate;
	}

	@Override
	public String toString() {
		return "tp:" + tp + ",fp:" + fp + ",fn:" + fn + ",tn:" + tn;
	}
}