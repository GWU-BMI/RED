package gov.va.research.red.regex;

import java.util.regex.Pattern;

public class JSEPatternAdapter implements PatternAdapter {

	Pattern regexPattern;
	
	public JSEPatternAdapter(String regex) {
		regexPattern = Pattern.compile(regex);
	};

	@Override
	public MatcherAdapter matcher(CharSequence input) {
		return new JSEMatcherAdapter(regexPattern.matcher(input));
	}

	@Override
	public String pattern() {
		return regexPattern.pattern();
	}

}
