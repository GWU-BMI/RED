package gov.va.research.red.ex;

import gov.va.research.red.LSTriplet;
import gov.va.research.red.LSTripletWithSensitivity;
import gov.va.research.red.Snippet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SensitivityMeasurer {
	Map<String, Pattern> patternCache = new HashMap<String, Pattern>();
	
	public List<LSTripletWithSensitivity> measureSensitivity(List<Snippet> snippets, List<LSTriplet> regExList) {
		List<LSTripletWithSensitivity> sensitivityTriplets = new ArrayList<LSTripletWithSensitivity>(regExList.size());
		for (LSTriplet triplet : regExList) {
			int count = sensitivityCount(triplet, snippets);
			double sensitivity = ((double)count)/((double)snippets.size());
			LSTripletWithSensitivity sensitivityTriplet = new LSTripletWithSensitivity(triplet);
			sensitivityTriplet.setSensitivity(sensitivity);
			sensitivityTriplets.add(sensitivityTriplet);
		}
		return sensitivityTriplets;
	}
	
	private int sensitivityCount(LSTriplet regEx, List<Snippet> snippets) {
		Pattern pattern = patternCache.get(regEx.toStringRegEx());
		int count = 0;
		if (pattern == null) {
			pattern = Pattern.compile(regEx.toStringRegEx());
			patternCache.put(regEx.toStringRegEx(), pattern);
		}
		for (Snippet snippt : snippets) {
			Matcher matcher = pattern.matcher(snippt.getText());
			if (matcher.find()) {
				count++;
			}
		}
		return count;
	}
}
