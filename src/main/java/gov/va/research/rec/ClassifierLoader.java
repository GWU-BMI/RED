package gov.va.research.rec;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClassifierLoader {
	private String regexFileName;
	private List<ClassifierRegEx> regexYes;
	private List<ClassifierRegEx> regexNo;
	private static final String NO_REGEX = "no regex";
	
	public ClassifierLoader(String regexFileName) {
		this.regexFileName = regexFileName;
	}
	
	public List<ClassifierRegEx> getYesRegEx() throws IOException{
		if(regexYes == null || regexYes.isEmpty())
			regexYes = readRegExYesFromFile();
		return regexYes;
	}
	
	public List<ClassifierRegEx> getNoRegEx() throws IOException{
		if(regexNo == null || regexNo.isEmpty())
			regexNo = readRegExNoFromFile();
		return regexNo;
	}
	
	private List<ClassifierRegEx> readRegExYesFromFile() throws IOException{
		List<ClassifierRegEx> returnList = new ArrayList<>();
		BufferedReader br = loadFile();
		br.readLine();
		while(true){
			String temp = br.readLine();
			if(temp.equals(NO_REGEX))
				break;
			ClassifierRegEx tempClassifierRegEx = new ClassifierRegEx(temp);
			returnList.add(tempClassifierRegEx);
		}
		closeReader(br);
		return returnList;
	}
	
	private List<ClassifierRegEx> readRegExNoFromFile() throws IOException{
		List<ClassifierRegEx> returnList = new ArrayList<>();
		BufferedReader br = loadFile();
		while(true){
			String temp = br.readLine();;
			if(temp.equals(NO_REGEX)){
				break;
			}
		}
		while(true){
			String temp = br.readLine();
			if(temp == null)
				break;
			ClassifierRegEx tempClassifierRegEx = new ClassifierRegEx(temp);
			returnList.add(tempClassifierRegEx);
		}
		closeReader(br);
		return returnList;
	}
	
	private BufferedReader loadFile() throws FileNotFoundException{
		BufferedReader br = new BufferedReader(new FileReader(getRegexFileName()));
		return br;
	}
	
	private void closeReader(BufferedReader br) throws IOException{
		br.close();
	}

	public String getRegexFileName() {
		return regexFileName;
	}

	public void setRegexFileName(String regexFileName) {
		this.regexFileName = regexFileName;
	}
}

