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
		fwriter = new FileWriter(resultsFile);
		writer = new BufferedWriter(fwriter);
	}
	
	protected void finalize () throws IOException {
		writer.close();
	}
	
	public boolean test(Collection<RegEx> regularExpressions, Collection<RegEx> negativeregularExpressions, Snippet snippet) throws IOException{
		int posScore = 0,negScore = 0, maxPosSpecifity =0, maxNegSpecifity = 0;
		writer.newLine();
		writer.newLine();
		writer.newLine();
		writer.write("The snippet \n\n");
		writer.write(snippet.getText());
		writer.newLine();
		writer.newLine();
		writer.write("Positive regex that matched \n\n");
		for(RegEx segment : regularExpressions){
			Pattern pattern = null;
			if(patternCache.containsKey(segment.getRegEx())){
				pattern = patternCache.get(segment.getRegEx());
			}else {
				pattern = Pattern.compile(segment.getRegEx());
				patternCache.put(segment.getRegEx(), pattern);
			}
			Matcher matcher = pattern.matcher(snippet.getText());
			boolean test = matcher.find();
			if(test) {
				writer.write(segment.getRegEx());
				writer.newLine();
				posScore++;
				if (segment.getSpecifity() > maxPosSpecifity) {
					maxPosSpecifity = segment.getSpecifity();
				}
			}
		}
		writer.newLine();
		writer.newLine();
		writer.write("Negative regex that matched \n\n");
		for(RegEx segment : negativeregularExpressions){
			Pattern pattern = null;
			if(patternCache.containsKey(segment.getRegEx())){
				pattern = patternCache.get(segment.getRegEx());
			}else {
				pattern = Pattern.compile(segment.getRegEx());
				patternCache.put(segment.getRegEx(), pattern);
			}
			Matcher matcher = pattern.matcher(snippet.getText());
			boolean test = matcher.find();
			if(test) {
				writer.write(segment.getRegEx());
				writer.newLine();
				negScore++;
				if (segment.getSpecifity() > maxNegSpecifity) {
					maxNegSpecifity = segment.getSpecifity();
				}
			}
		}
		writer.flush();
		if (maxPosSpecifity > maxNegSpecifity) {
			return true;
		}
		/*if (posScore > negScore) {
			return true;
		}*/
		/*if (posScore == negScore && posScore > 0) {
			return true;
		}*/
		return false;
	}
}
