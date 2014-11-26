package gov.va.research.red.cat;

import gov.va.research.red.RegEx;
import gov.va.research.red.Snippet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CategorizerTester {
	
	private Map<String, Pattern> patternCache = new HashMap<>();
	private File resultsFile = null;
	private BufferedWriter writer = null;
	
	public CategorizerTester() throws IOException {
		resultsFile = new File("categorizerErrorTesting");
		FileWriter fwriter = null;
		fwriter = new FileWriter(resultsFile,true);
		writer = new BufferedWriter(fwriter);
	}
	
	protected void finalize () throws IOException {
		writer.close();
	}
	
	public boolean test(Collection<RegEx> regularExpressions, Collection<RegEx> negativeregularExpressions, Snippet snippet, boolean actual) throws IOException{
		int posScore = 0,negScore = 0;
		double maxPosSpecifity =0.0, maxNegSpecifity = 0.0;
		StringBuilder strToWrite = new StringBuilder();
		strToWrite.append("\n");
		strToWrite.append("\n");
		strToWrite.append("\n");
		strToWrite.append("The snippet \n\n");
		strToWrite.append(snippet.getText());
		strToWrite.append("\n");
		strToWrite.append("\n");
		strToWrite.append("Positive regex that matched \n\n");
		for(RegEx segment : regularExpressions){
			Pattern pattern = null;
			if(patternCache.containsKey(segment.getRegEx())){
				pattern = patternCache.get(segment.getRegEx());
			}else {
				pattern = Pattern.compile(segment.getRegEx(), Pattern.CASE_INSENSITIVE);
				patternCache.put(segment.getRegEx(), pattern);
			}
			Matcher matcher = pattern.matcher(snippet.getText());
			boolean test = matcher.find();
			if(test) {
				strToWrite.append(segment.getRegEx()+"\t"+segment.getSpecifity());
				strToWrite.append("\n");
				posScore++;
				if (Double.compare(segment.getSpecifity(), maxPosSpecifity) > 0) {
					maxPosSpecifity = segment.getSpecifity();
				}
			}
		}
		strToWrite.append("\n");
		strToWrite.append("\n");
		strToWrite.append("Negative regex that matched \n\n");
		for(RegEx segment : negativeregularExpressions){
			Pattern pattern = null;
			if(patternCache.containsKey(segment.getRegEx())){
				pattern = patternCache.get(segment.getRegEx());
			}else {
				pattern = Pattern.compile(segment.getRegEx(), Pattern.CASE_INSENSITIVE);
				patternCache.put(segment.getRegEx(), pattern);
			}
			Matcher matcher = pattern.matcher(snippet.getText());
			boolean test = matcher.find();
			if(test) {
				strToWrite.append(segment.getRegEx()+"\t"+segment.getSpecifity());
				strToWrite.append("\n");
				negScore++;
				if (Double.compare(segment.getSpecifity(), maxNegSpecifity) > 0) {
					maxNegSpecifity = segment.getSpecifity();
				}
			}
		}
		//writer.flush();
		
		boolean predicted = false;
		if (Double.compare(maxPosSpecifity, maxNegSpecifity) > 0) {
			predicted = true;
		}
		/*if (posScore > negScore) {
			predicted = true;
		} else {
			predicted =  false;
		}*/
		/*if (posScore == negScore && posScore > 0) {
			return true;
		}*/
		if ((!actual && predicted) || (actual && !predicted)) {
			writer.write(strToWrite.toString());
			writer.flush();
		}
		return predicted;
	}
}
