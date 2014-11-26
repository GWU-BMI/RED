package gov.va.research.red.ex;

import gov.va.research.red.LSTriplet;
import gov.va.research.red.MatchedElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LSExtractor implements Extractor {
	private static final Logger LOG = LoggerFactory.getLogger(LSExtractor.class);
	
	private List<LSTriplet> regExpressions;
	private Map<String, Pattern> patternCache = new ConcurrentHashMap<>();
	
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
			returnSet = regExpressions.parallelStream().map((triplet) -> {
				Set<MatchedElement> matchedElements = new HashSet<>();
				Pattern pattern = Pattern.compile(triplet.toStringRegEx(), Pattern.CASE_INSENSITIVE);
				Matcher matcher = pattern.matcher(target);
				boolean test = matcher.find();
				if(test){
					String candidateLS = matcher.group(1);
					if(candidateLS != null && !candidateLS.equals("")){
						int startPos = target.indexOf(candidateLS);
						int endPos = startPos + candidateLS.length();
						matchedElements.add(new MatchedElement(startPos, endPos, candidateLS));
					}
				}
				return matchedElements;
			}).reduce(Collections.newSetFromMap(new ConcurrentHashMap<>()), (s1, s2) -> {
				s1.addAll(s2);
				return s1;
			});
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

