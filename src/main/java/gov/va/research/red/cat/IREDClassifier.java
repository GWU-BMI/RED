package gov.va.research.red.cat;

import java.util.List;


public interface IREDClassifier {
	public void fit(List<String> snippets, List<List<Integer>> segspans, List<Integer> labels);
	public List<Integer> predict(List<String> snippets, int labelForUndecided);
}
