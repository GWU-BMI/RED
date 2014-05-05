package gov.va.research.ree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LSExtractor implements Extractor {
	
	private List<LSTriplet> regExpressions;
	private Map<String, Pattern> patternCache = new HashMap<>();
	
	public LSExtractor(List<LSTriplet> regExpressions){
		this.setRegExpressions(regExpressions);
	}

	@Override
	public List<MatchedElement> extract(String target) {
		if(target == null || target.equals(""))
			return null;
		Set<MatchedElement> returnSet = null;
		List<LSTriplet> regExpressions = getRegExpressions();
		if(regExpressions != null && !regExpressions.isEmpty()){
			returnSet = new HashSet<>();
			for(LSTriplet triplet : regExpressions){
				Pattern pattern = null;
				if(patternCache.containsKey(triplet.toStringRegEx())){
					pattern = patternCache.get(triplet.toStringRegEx()); 
				}else{
					pattern = Pattern.compile(triplet.toStringRegEx());
					patternCache.put(triplet.toStringRegEx(), pattern);
				}
				Matcher matcher = pattern.matcher(target);
				boolean test = matcher.find();
				if(test){
					String candidateLS = matcher.group(1);
					if(candidateLS != null && !candidateLS.equals("")){
						int startPos = target.indexOf(candidateLS);
						int endPos = startPos + candidateLS.length();
						returnSet.add(new MatchedElement(startPos, endPos, candidateLS));
					}
				}
			}
		}
		if(returnSet == null || returnSet.isEmpty())
			return null;
		List<MatchedElement> returnedList = new ArrayList<>();
		returnedList.addAll(returnSet);
		return returnedList;
	}
	
	//getter  setter
	
	public List<LSTriplet> getRegExpressions() {
		return regExpressions;
	}

	public void setRegExpressions(List<LSTriplet> regExpressions) {
		this.regExpressions = regExpressions;
	}

}

