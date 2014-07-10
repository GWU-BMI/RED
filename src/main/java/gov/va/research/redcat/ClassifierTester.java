package gov.va.research.redcat;

import gov.va.research.redex.Snippet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassifierTester {
	
	private Map<String, Pattern> patternCache = new HashMap<>();
	
	public boolean test(List<ClassifierRegEx> regularExpressions, Snippet snippet){
		for(ClassifierRegEx segment : regularExpressions){
			Pattern pattern = null;
			if(patternCache.containsKey(segment.getRegEx())){
				pattern = patternCache.get(segment.getRegEx());
			}else {
				pattern = Pattern.compile(segment.getRegEx());
				patternCache.put(segment.getRegEx(), pattern);
			}
			Matcher matcher = pattern.matcher(snippet.getText());
			boolean test = matcher.find();
			if(test)
				return true;
		}
		return false;
	}
}
