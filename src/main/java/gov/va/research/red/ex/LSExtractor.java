package gov.va.research.red.ex;

import gov.va.research.red.LSTriplet;
import gov.va.research.red.MatchedElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
	
	private List<LSTriplet> ls3list;
	private Map<String, Pattern> patternCache = new ConcurrentHashMap<>();
	
	public LSExtractor(List<LSTriplet> ls3list){
		this.setLSTriplets(ls3list);
	}

	@Override
	public List<MatchedElement> extract(String target) {
		if(target == null || target.length() == 0) {
			return null;
		}
		Set<MatchedElement> returnSet = null;
		List<LSTriplet> regExpressions = getLSTriplets();
		if(regExpressions != null && !regExpressions.isEmpty()){
			returnSet = regExpressions.parallelStream().map((triplet) -> {
				String localTarget = target;
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
	
	public MatchedElement extractFirst(String target) {
		if(target == null || target.equals("")) {
			return null;
		}
		List<LSTriplet> regExpressions = getLSTriplets();
		if(regExpressions != null && !regExpressions.isEmpty()){
			Optional<MatchedElement> matchedElement = regExpressions.parallelStream().map((triplet) -> {
				Set<MatchedElement> matchedElements = new HashSet<>();
				Pattern pattern = null;
				try {
					pattern = Pattern.compile(triplet.toStringRegEx(), Pattern.CASE_INSENSITIVE);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Matcher matcher = pattern.matcher(target);
				boolean test = matcher.find();
				if(test) {
					String candidateLS = matcher.group(1);
					if(candidateLS != null && !candidateLS.equals("")){
						int startPos = target.indexOf(candidateLS);
						int endPos = startPos + candidateLS.length();
						matchedElements.add(new MatchedElement(startPos, endPos, candidateLS));
					}
				}
				return matchedElements;
			}).filter((me) -> !me.isEmpty()).map((me) -> { return me.iterator().next(); }).findFirst();
			return matchedElement.orElse(null);
		}
		return null;
	}
	
	public List<LSTriplet> getLSTriplets() {
		return ls3list;
	}

	public void setLSTriplets(List<LSTriplet> regExpressions) {
		this.ls3list = regExpressions;
	}

}

