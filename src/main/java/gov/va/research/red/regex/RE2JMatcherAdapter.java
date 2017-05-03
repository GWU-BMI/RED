package gov.va.research.red.regex;

import com.google.re2j.Matcher;

public class RE2JMatcherAdapter implements MatcherAdapter {

	private Matcher re2jMatcher;

	public RE2JMatcherAdapter(Matcher matcher) {
		this.re2jMatcher = matcher;
	}

	@Override
	public boolean find() {
		return this.re2jMatcher.find();
	}

	@Override
	public int groupCount() {
		return this.re2jMatcher.groupCount();
	}

	@Override
	public String group(int group) {
		return this.re2jMatcher.group(group);
	}

	@Override
	public int start(int group) {
		return this.re2jMatcher.start(group);
	}

	@Override
	public int end(int group) {
		return this.re2jMatcher.end(group);
	}

	@Override
	public String group() {
		return this.re2jMatcher.group();
	}

	@Override
	public int start() {
		return this.re2jMatcher.start();
	}

	@Override
	public int end() {
		return this.re2jMatcher.end();
	}

}
