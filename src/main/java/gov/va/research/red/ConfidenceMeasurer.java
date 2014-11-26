package gov.va.research.red;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfidenceMeasurer {
	
	public List<ConfidenceSnippet> measureConfidence(List<Snippet> snippets, List<RegEx> yesRegExs, List<RegEx> noRegExs) throws IOException {
		List<ConfidenceSnippet> confidenceSnippets = new ArrayList<ConfidenceSnippet>(snippets.size());
		Map<RegEx , Pattern> patternMap = new HashMap<RegEx, Pattern>();
		for (Snippet snippet : snippets) {
			int yesScore = measureScore(yesRegExs, snippet, patternMap);
			int noScore = measureScore(noRegExs, snippet, patternMap);
			if (yesScore > noScore) {
				confidenceSnippets.add(new ConfidenceSnippet(snippet, new Confidence(yesScore - noScore, ConfidenceType.YES)));
			} else if (noScore > yesScore) {
				confidenceSnippets.add(new ConfidenceSnippet(snippet, new Confidence(noScore - yesScore, ConfidenceType.NO)));
			} else {
				confidenceSnippets.add(new ConfidenceSnippet(snippet, new Confidence(0, ConfidenceType.UNCERTAIN)));
			}
		}
		return confidenceSnippets;
	}
	
	private int measureScore(List<RegEx> regExes, Snippet snippet, Map<RegEx , Pattern> patternMap) {
		int score = 0;
		if(regExes == null || regExes.isEmpty()) {
			return score;
		}
		for(RegEx regEx : regExes) {
			Pattern pattern = patternMap.get(regEx);
			if (pattern == null) {
				pattern = Pattern.compile(regEx.getRegEx(), Pattern.CASE_INSENSITIVE);
				patternMap.put(regEx, pattern);
			}
			Matcher matcher = pattern.matcher(snippet.getText());
			if(matcher.find()) {
				score++;
			}
		}
		return score;
	}
	
}
