package gov.va.research.red.cat;

import gov.va.research.red.LabeledSegment;
import gov.va.research.red.RegEx;
import gov.va.research.red.Snippet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
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
		/*resultsFile =  new File("categorizerErrorTesting3.txt");
		FileWriter fwriter = null;
		fwriter = new FileWriter(resultsFile,true);
		writer = new BufferedWriter(fwriter);*/
	}
	
	protected void finalize () throws IOException {
		//writer.close();
	}
	
	public boolean test(Collection<RegEx> regularExpressions, Collection<RegEx> negativeregularExpressions, Snippet snippet, boolean actual, PrintWriter pw) throws IOException{
		double maxPosSensitivity =0.0, maxNegSensitivity = 0.0;
		StringBuilder strToWrite = new StringBuilder();
		//strToWrite.append("\nThe snippet ("+actual+")");
		strToWrite.append("\n------------------------\n");
		strToWrite.append(snippet.getText());
		strToWrite.append("\n------------------------\n");
		Collection<LabeledSegment> labeledSegments = snippet.getLabeledSegments();
		if (labeledSegments!=null) for (LabeledSegment ls: labeledSegments) {
			strToWrite.append("---The labeled segment ("+ls.getLabel()+")---\n");
			strToWrite.append(ls.getLabeledString());
			strToWrite.append("\n---\n");
		}
		
		strToWrite.append("\n---Positive regex that matched---\n");
		for(RegEx regEx : regularExpressions){
			Pattern pattern = null;
			if(patternCache.containsKey(regEx.getRegEx())){
				pattern = patternCache.get(regEx.getRegEx());
			}else {
				pattern = Pattern.compile(regEx.getRegEx(), Pattern.CASE_INSENSITIVE);
				patternCache.put(regEx.getRegEx(), pattern);
			}
			Matcher matcher = pattern.matcher(snippet.getText());
			boolean test = matcher.find();
			if(test) {
				double sen = Math.round(regEx.getSensitivity()*1000)/1000.0;
				strToWrite.append(sen+"\t"+regEx.getRegEx()+"\n");
				strToWrite.append("\t"+snippet.getText().substring(matcher.start(), matcher.end())+"\n");
				if (regEx.getSensitivity()>maxPosSensitivity) {
					maxPosSensitivity = regEx.getSensitivity();
				}
			}
		}
		strToWrite.append("\n---Negative regex that matched---\n");
		for(RegEx regEx : negativeregularExpressions){
			Pattern pattern = null;
			if(patternCache.containsKey(regEx.getRegEx())){
				pattern = patternCache.get(regEx.getRegEx());
			}else {
				pattern = Pattern.compile(regEx.getRegEx(), Pattern.CASE_INSENSITIVE);
				patternCache.put(regEx.getRegEx(), pattern);
			}
			Matcher matcher = pattern.matcher(snippet.getText());
			boolean test = matcher.find();
			if(test) {
				double sen = Math.round(regEx.getSensitivity()*1000)/1000.0;
				strToWrite.append(sen+"\t"+regEx.getRegEx()+"\n");
				strToWrite.append("\t"+snippet.getText().substring(matcher.start(), matcher.end())+"\n");
				if (regEx.getSensitivity()>maxNegSensitivity) {
					maxNegSensitivity = regEx.getSensitivity();
				}
			}
		}
		
		boolean predicted = false;
		if (maxPosSensitivity>maxNegSensitivity) {
			predicted = true;
		}
			//if ((!actual && predicted) || (actual && !predicted)) {
			if (actual != predicted) {
				strToWrite.insert(0, "\nThe snippet (Incorrect)");
			} else {
				strToWrite.insert(0, "\nThe snippet (Correct)");
			}
			pw.write(strToWrite.toString());
		return predicted;
	}
	
	public boolean test2(Collection<RegEx> regularExpressions, Collection<RegEx> negativeregularExpressions, String segment, boolean actual) throws IOException{
		double maxPosSensitivity =0.0, maxNegSensitivity = 0.0;
		StringBuilder strToWrite = new StringBuilder();
		strToWrite.append("\nThe labeled segment ("+actual+")");
		strToWrite.append("\n------------------------\n");
		strToWrite.append(segment);
		strToWrite.append("\n------------------------\n");
		strToWrite.append("Positive regex that matched \n\n");
		for(RegEx regEx : regularExpressions){
			Pattern pattern = null;
			if(patternCache.containsKey(regEx.getRegEx())){
				pattern = patternCache.get(regEx.getRegEx());
			}else {
				pattern = Pattern.compile(regEx.getRegEx(), Pattern.CASE_INSENSITIVE);
				patternCache.put(regEx.getRegEx(), pattern);
			}
			Matcher matcher = pattern.matcher(segment);
			//Matcher matcher = pattern.matcher(snippet.);
			boolean test = matcher.find();
			if(test) {
				strToWrite.append(regEx.getRegEx()+"\t"+regEx.getSensitivity());
				strToWrite.append("\n");
				if (Double.compare(regEx.getSensitivity(), maxPosSensitivity) > 0) {
					maxPosSensitivity = regEx.getSensitivity();
				}
			}
		}
		strToWrite.append("\n\nNegative regex that matched \n\n");
		for(RegEx regEx : negativeregularExpressions){
			Pattern pattern = null;
			if(patternCache.containsKey(regEx.getRegEx())){
				pattern = patternCache.get(regEx.getRegEx());
			}else {
				pattern = Pattern.compile(regEx.getRegEx(), Pattern.CASE_INSENSITIVE);
				patternCache.put(regEx.getRegEx(), pattern);
			}
			Matcher matcher = pattern.matcher(segment);
			boolean test = matcher.find();
			if(test) {
				strToWrite.append(regEx.getRegEx()+"\t"+regEx.getSensitivity());
				strToWrite.append("\n");
				if (Double.compare(regEx.getSensitivity(), maxNegSensitivity) > 0) {
					maxNegSensitivity = regEx.getSensitivity();
				}
			}
		}
		
		boolean predicted = false;
		if (Double.compare(maxPosSensitivity, maxNegSensitivity) > 0) {
			predicted = true;
		}
		/*if (actual!=predicted){
			System.out.println(strToWrite.toString());
		}*/
		return predicted;
	}
}
