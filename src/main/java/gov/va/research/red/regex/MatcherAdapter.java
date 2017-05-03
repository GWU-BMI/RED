package gov.va.research.red.regex;

public interface MatcherAdapter {
	public boolean find();
    public int groupCount();
    public String group();
    public String group(int group);
    public int start();
    public int start(int group);
    public int end();
    public int end(int group);
}
