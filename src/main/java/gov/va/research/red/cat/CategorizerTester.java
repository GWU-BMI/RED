package gov.va.research.red.cat;

import gov.va.research.red.Snippet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CategorizerTester {
	
	private Map<String, Pattern> patternCache = new HashMap<>();
	
	public boolean test(Collection<CategorizerRegEx> regularExpressions, Snippet snippet){
		for(CategorizerRegEx segment : regularExpressions){
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
