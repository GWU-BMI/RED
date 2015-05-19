package gov.va.research.red.ex;

import gov.va.research.red.MatchedElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

public class REDExtractor implements Extractor {
	private static transient final Logger LOG = LoggerFactory.getLogger(REDExtractor.class);
	
	private List<Collection<SnippetRegEx>> rankedSnippetRegExs;
	
	public REDExtractor(Collection<SnippetRegEx> sres) {
		this.rankedSnippetRegExs = new ArrayList<>(1);
		this.rankedSnippetRegExs.add(sres);
	}
	
	public REDExtractor(List<Collection<SnippetRegEx>> rankedSres) {
		this.rankedSnippetRegExs = rankedSres;
	}

	public REDExtractor(SnippetRegEx snippetRegEx) {
		this.rankedSnippetRegExs = new ArrayList<>(1);
		this.rankedSnippetRegExs.add(Arrays.asList(new SnippetRegEx[] { snippetRegEx }));
	}

	@Override
	public Collection<MatchedElement> extract(String target) {
		if(target == null || target.length() == 0) {
			return null;
		}
		Set<MatchedElement> returnSet = null;
		for (Collection<SnippetRegEx> snippetREs : this.rankedSnippetRegExs) {
			if(snippetREs != null && !snippetREs.isEmpty()) {
				returnSet = snippetREs.parallelStream().map((sre) -> {
					MatchFinder mf = new MatchFinder(sre, target);
					Set<MatchedElement> mes = mf.call();
					return mes;
				}).reduce(Collections.newSetFromMap(new ConcurrentHashMap<>()), (s1, s2) -> {
					s1.addAll(s2);
					return s1;
				});
			}
			if (returnSet != null && !returnSet.isEmpty()) {
				break;
			}
		}
		if(returnSet == null || returnSet.isEmpty()) {
			return null;
		}
		return returnSet;
	}
	
	public MatchedElement extractFirst(String target) {
		if(target == null || target.equals("")) {
			return null;
		}
		for (Collection<SnippetRegEx> snippetREs : this.rankedSnippetRegExs) {
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
							matchedElements.add(new MatchedElement(startPos, endPos, candidateLS, sre.toString()));
						}
					}
					return matchedElements;
				}).filter((me) -> !me.isEmpty()).map((me) -> { return me.iterator().next(); }).findFirst();
				if (matchedElement.isPresent()) {
					return matchedElement.orElse(null);
				}
			}
		}
		return null;
	}
	
	public List<Collection<SnippetRegEx>> getRankedSnippetRegExs() {
		return this.rankedSnippetRegExs;
	}

	public void setRankedSnippetRegExs(List<Collection<SnippetRegEx>> rankedSnippetRegExs) {
		this.rankedSnippetRegExs = rankedSnippetRegExs;
	}
	
	public List<String> getRegularExpressions() {
		List<String> regexs = new ArrayList<>(this.rankedSnippetRegExs.size());
		for (Collection<SnippetRegEx> sres : this.rankedSnippetRegExs) {
			for (SnippetRegEx sre : sres) {
				regexs.add(sre.toString());
			}
		}
		return regexs;
	}

	private class MatchFinder implements Callable<Set<MatchedElement>> {
		SnippetRegEx sre;
		String target;
		
		public MatchFinder(SnippetRegEx sre, String target) {
			this.sre = sre;
			this.target = target;
		}

		@Override
		public Set<MatchedElement> call() {
			Set<MatchedElement> matchedElements = new HashSet<>();
			//LOG.debug("Pattern: " + sre.toString());
			Matcher matcher = sre.getPattern().matcher(target);
			boolean test = matcher.find();
			if(test){
				String candidateLS = matcher.group(1);
				if(candidateLS != null && !candidateLS.equals("")){
					int startPos = target.indexOf(candidateLS);
					int endPos = startPos + candidateLS.length();
					matchedElements.add(new MatchedElement(startPos, endPos, candidateLS, sre.toString()));
				}
			}
			return matchedElements;
		}
	}

	/**
	 * Dumps (serializes) the REDExtractor to a file.
	 * @param rex The REDExtractor to dump.
	 * @param path The path of the file to receive the dumped REDExtractor.
	 * @throws IOException
	 */
	public static void dump(REDExtractor rex, Path path) throws IOException {
		Gson gson = new Gson();
		String json = gson.toJson(rex);
		Files.write(path, json.getBytes(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
	}
	
	/**
	 * Loads (deserializes) a REDExtractor from a file.
	 * @param path The path of the file containing the dumped REDExtractor.
	 * @return a REDExtractor represented in the file.
	 * @throws IOException
	 */
	public static REDExtractor load(Path path) throws IOException {
		Gson gson = new Gson();
		String json = new String(Files.readAllBytes(path));
		REDExtractor rex = gson.fromJson(json, REDExtractor.class);
		return rex;
	}

}

