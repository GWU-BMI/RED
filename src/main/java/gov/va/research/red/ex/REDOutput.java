package gov.va.research.red.ex;

public class REDOutput {
	private double CVScore;
	private int offBegin;
	private int offEnd;
	private String regEx;
	private String classLabel;
	
	//construction
	public REDOutput(double cvscore,int offBegin,int offEnd,String regEx,String classLabel){
		this.CVScore=cvscore;
		this.offBegin=offBegin;
		this.offEnd=offEnd;
		this.regEx=regEx;
		this.classLabel=classLabel;
		
		
	}
	
	public double getCVScore(){
		return this.CVScore;
	}
	
	public int getOffBegin(){
		return this.offBegin;
	}
	
	public int getOffEnd(){
		return this.offEnd;
	}
	
	public String getRegEx(){
		return this.regEx;
	}
	
	public String getClassLabel(){
		return this.classLabel;
	}
	
	
	public String toString(){
		return "CVScore:"+this.CVScore+".offBegin:"+this.offBegin+",offEnd:"+this.offEnd+",RegEx:"+this.regEx+",ClassficationLabel:"+this.classLabel;
	}
	
	
	
	
	
	

}
