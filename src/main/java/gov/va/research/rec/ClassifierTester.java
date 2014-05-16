package gov.va.research.rec;

import gov.va.research.ree.LabeledSegment;
import gov.va.research.ree.Snippet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassifierTester {
	
	private Map<String, Pattern> patternCache = new HashMap<>();
	
	public boolean test(List<LabeledSegment> regularExpressions, Snippet snippet){
		for(LabeledSegment segment : regularExpressions){
			Pattern pattern = null;
			if(patternCache.containsKey(segment.getLabeledString())){
				pattern = patternCache.get(segment.getLabeledString());
			}else {
				pattern = Pattern.compile(segment.getLabeledString());
				patternCache.put(segment.getLabeledString(), pattern);
			}
			Matcher matcher = pattern.matcher(snippet.getText());
			boolean test = matcher.find();
			if(test)
				return true;
		}
		return false;
	}
}
