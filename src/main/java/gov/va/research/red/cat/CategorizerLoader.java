package gov.va.research.red.cat;

import gov.va.research.red.RegEx;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CategorizerLoader {
	private String regexFileName;
	private List<RegEx> regexYes;
	private List<RegEx> regexNo;
	private static final String NO_REGEX = "no regex";
	
	public CategorizerLoader(String regexFileName) {
		this.regexFileName = regexFileName;
	}
	
	public List<RegEx> getYesRegEx() throws IOException{
		if(regexYes == null || regexYes.isEmpty())
			regexYes = readRegExYesFromFile();
		return regexYes;
	}
	
	public List<RegEx> getNoRegEx() throws IOException{
		if(regexNo == null || regexNo.isEmpty())
			regexNo = readRegExNoFromFile();
		return regexNo;
	}
	
	private List<RegEx> readRegExYesFromFile() throws IOException{
		List<RegEx> returnList = new ArrayList<>();
		BufferedReader br = loadFile();
		br.readLine();
		while(true){
			String temp = br.readLine();
			if(temp.equals(NO_REGEX))
				break;
			RegEx tempClassifierRegEx = new RegEx(temp);
			returnList.add(tempClassifierRegEx);
		}
		closeReader(br);
		return returnList;
	}
	
	private List<RegEx> readRegExNoFromFile() throws IOException{
		List<RegEx> returnList = new ArrayList<>();
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
			RegEx tempClassifierRegEx = new RegEx(temp);
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

