package gov.va.research.red.cat;

import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CategorizerConfidenceMeasurer {
	
	private RegExCategorizer regExCategorizer;
	private static final String YES = "yes";
	private static final String NO = "no";
	
	public CategorizerConfidenceMeasurer() {
		regExCategorizer = new RegExCategorizer();
	}
	
	public static void main(String args[]){
		CategorizerConfidenceMeasurer measurer = new CategorizerConfidenceMeasurer();
		List<String> yesLabels = new ArrayList<>();
		yesLabels.add("yes");
		List<String> noLabels = new ArrayList<>();
		noLabels.add("no");
		try {
			measurer.measureConfidence(new File("target/test-classes/diabetes-snippets.vtt"), yesLabels, noLabels);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void measureConfidence(final File vttFile, List<String> yesLabels, List<String> noLabels) throws IOException {
		List<Snippet> snippets = new ArrayList<Snippet>();
		VTTReader vttr = new VTTReader();
		snippets.addAll(vttr.extractSnippets(vttFile, ""));
		Map<String, Collection<CategorizerRegEx>> regExMap = regExCategorizer.findRegexesAndSaveInFile(vttFile, yesLabels, noLabels, null, false);
		List<CategorizerRegEx> yesRegExs = new ArrayList<CategorizerRegEx>(regExMap.get(YES));
		List<CategorizerRegEx> noRegExs = new ArrayList<CategorizerRegEx>(regExMap.get(NO));
		List<CategorizedSnippet> confidenceSnippets = new ArrayList<CategorizedSnippet>();
		Map<CategorizerRegEx , Pattern> patternMap = new HashMap<CategorizerRegEx, Pattern>();
		for (Snippet snippet : snippets) {
			int yesScore = measureScore(yesRegExs, snippet, patternMap);
			int noScore = measureScore(noRegExs, snippet, patternMap);
			if (yesScore > noScore) {
				confidenceSnippets.add(new CategorizedSnippet(snippet, new Confidence(yesScore - noScore, ConfidenceType.YES)));
			} else if (noScore > yesScore) {
				confidenceSnippets.add(new CategorizedSnippet(snippet, new Confidence(noScore - yesScore, ConfidenceType.NO)));
			} else {
				confidenceSnippets.add(new CategorizedSnippet(snippet, new Confidence(0, ConfidenceType.UNCERTAIN)));
			}
		}
		for(CategorizedSnippet catSnippet : confidenceSnippets) {
			System.out.println("Score "+catSnippet.getConfidence().getConfidence()+" Confidence Type "+catSnippet.getConfidence().getConfidenceType());
		}
	}
	
	private int measureScore(List<CategorizerRegEx> regExes, Snippet snippet, Map<CategorizerRegEx , Pattern> patternMap) {
		int score = 0;
		for(CategorizerRegEx regEx : regExes) {
			Pattern pattern = patternMap.get(regEx);
			if (pattern == null) {
				pattern = Pattern.compile(regEx.getRegEx());
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
