package gov.va.research.ree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LSExtractor implements Extractor {
	
	private List<LSTriplet> regExpressions;
	private Map<String, Pattern> patternCache = new HashMap<>();
	
	public LSExtractor(List<LSTriplet> regExpressions){
		this.setRegExpressions(regExpressions);
	}

	@Override
	public List<String> extract(String target) {
		if(target == null || target.equals(""))
			return null;
		List<String> returnList = null;
		List<LSTriplet> regExpressions = getRegExpressions();
		if(regExpressions != null && !regExpressions.isEmpty()){
			returnList = new ArrayList<>();
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
					if(candidateLS != null && !candidateLS.equals(""))
						returnList.add(candidateLS);
				}
			}
		}
		return returnList;
	}
	
	//getter  setter
	
	public List<LSTriplet> getRegExpressions() {
		return regExpressions;
	}

	public void setRegExpressions(List<LSTriplet> regExpressions) {
		this.regExpressions = regExpressions;
	}

}
