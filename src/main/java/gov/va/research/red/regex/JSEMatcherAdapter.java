package gov.va.research.red.regex;

import java.util.regex.Matcher;

public class JSEMatcherAdapter implements MatcherAdapter {

	Matcher regexMatcher;

	public JSEMatcherAdapter(Matcher matcher) {
		this.regexMatcher = matcher;
	}

	@Override
	public boolean find() {
		return this.regexMatcher.find();
	}

	@Override
	public int groupCount() {
		return this.regexMatcher.groupCount();
	}

	@Override
	public String group(int group) {
		return this.regexMatcher.group(group);
	}

	@Override
	public int start(int group) {
		return this.regexMatcher.start(group);
	}

	@Override
	public int end(int group) {
		return this.regexMatcher.end(group);
	}

	@Override
	public String group() {
		return this.regexMatcher.group();
	}

	@Override
	public int start() {
		return this.regexMatcher.start();
	}

	@Override
	public int end() {
		return this.regexMatcher.end();
	}

}
