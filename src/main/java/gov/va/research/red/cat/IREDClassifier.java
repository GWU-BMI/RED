package gov.va.research.red.cat;

import gov.va.research.red.RegEx;
import gov.va.research.red.Snippet;

import java.util.Collection;
import java.util.List;
import java.util.Map;


public interface IREDClassifier {
	public void fit(List<String> snippets, List<List<Integer>> segspans, List<Integer> labels);
	public List<Integer> predict(List<String> snippets, int labelForUndecided);
	public List<String> getStrictRegexs(int label);
	public List<String> getLessStrictRegexs(int lable);
	public List<String> getLeastStrictRegexs(int label);
}
