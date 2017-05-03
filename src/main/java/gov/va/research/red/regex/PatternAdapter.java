package gov.va.research.red.regex;

/**
 *  Adapter for regular expressions implementations using the model of java.util.regex.Pattern
 * @author doug
 */
public interface PatternAdapter {
	public MatcherAdapter matcher(CharSequence input);
    public String pattern();
}
