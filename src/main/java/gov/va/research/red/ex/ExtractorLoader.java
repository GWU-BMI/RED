package gov.va.research.red.ex;

import gov.va.research.red.LSTriplet;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExtractorLoader {
	private String regexFileName;
	private List<LSTriplet> regExList;
	
	public ExtractorLoader(String regexFileName) {
		this.regexFileName = regexFileName;
	}
	
	public List<LSTriplet> getRegularExpressions() throws IOException{
		if(regExList == null || regExList.isEmpty())
			regExList = readRegExFromFile();
		return regExList;
	}
	
	private List<LSTriplet> readRegExFromFile() throws IOException{
		BufferedReader br = loadFile();
		List<LSTriplet> returnList = new ArrayList<>();
		while(true){
			String temp = br.readLine();
			if(temp == null)
				break;
			returnList.add(new LSTriplet(temp));
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
