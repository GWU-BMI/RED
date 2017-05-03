package gov.va.research.red.regex;

import com.google.re2j.Pattern;

public class RE2JPatternAdapter implements PatternAdapter {

	private Pattern re2jPattern;

	public RE2JPatternAdapter(String regex) {
		this.re2jPattern = Pattern.compile(regex);
	};

	@Override
	public MatcherAdapter matcher(CharSequence input) {
		return new RE2JMatcherAdapter(this.re2jPattern.matcher(input));
	}

	@Override
	public String pattern() {
		return this.re2jPattern.pattern();
	}

}
