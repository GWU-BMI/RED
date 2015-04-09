package gov.va.research.red.ex;

import gov.va.research.red.LSTriplet;
import gov.va.research.red.MatchedElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
	
	private Collection<SnippetRegEx> snippetRegExs;
	private Map<String, Pattern> patternCache = new ConcurrentHashMap<>();
	
	public LSExtractor(Collection<SnippetRegEx> sreList) {
		this.setSnippetRegExs(sreList);
	}

	public LSExtractor(SnippetRegEx snippetRegEx) {
		this.setSnippetRegExs(Arrays.asList(new SnippetRegEx[] { snippetRegEx }));
	}

	@Override
	public List<MatchedElement> extract(String target) {
		if(target == null || target.length() == 0) {
			return null;
		}
		Set<MatchedElement> returnSet = null;
		Collection<SnippetRegEx> snippetREs = getSnippetRegExs();
		if(snippetREs != null && !snippetREs.isEmpty()){
			returnSet = snippetREs.parallelStream().map((sre) -> {
				Set<MatchedElement> matchedElements = new HashSet<>();
				Matcher matcher = sre.getPattern().matcher(target);
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
		Collection<SnippetRegEx> snippetREs = getSnippetRegExs();
		if(snippetREs != null && !snippetREs.isEmpty()){
			Optional<MatchedElement> matchedElement = snippetREs.parallelStream().map((sre) -> {
				Set<MatchedElement> matchedElements = new HashSet<>();
				Matcher matcher = sre.getPattern().matcher(target);
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
	
	public Collection<SnippetRegEx> getSnippetRegExs() {
		return snippetRegExs;
	}

	public void setSnippetRegExs(Collection<SnippetRegEx> snippetRegExs) {
		this.snippetRegExs = snippetRegExs;
	}

}

