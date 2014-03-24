package gov.va.research.ree;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CrossValidate {

	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			System.out.println("Arguments: <vttFile> <label> <folds>");
		} else {
			File vttFile = new File(args[0]);
			String label = args[1];
			int folds = Integer.valueOf(args[2]);
			CrossValidate cv = new CrossValidate();
			List<CVScore> results = cv.crossValidate(vttFile, label, folds);
			
			// Display results
			int i = 0;
			for (CVScore s : results) {
				System.out.println("--- Run " + (i++) + " ---");
				System.out.println(s.getEvaluation());
			}
			System.out.println("--- Aggregate ---");
			System.out.println(CVScore.aggregate(results).getEvaluation());

		}
	}

	public List<CVScore> crossValidate(File vttFile, String label, int folds)
			throws IOException {
		VTTReader vttr = new VTTReader();
		List<Snippet> snippets = vttr.extractSnippets(vttFile, label);
		
		List<List<Snippet>> partitions = new ArrayList<>(folds);
		for (int i = 0; i < folds; i++) {
			partitions.add(new ArrayList<Snippet>());
		}

		Collections.shuffle(partitions);

		Iterator<Snippet> snippetIter = snippets.iterator();
		
		int partitionIdx = 0;
		while (snippetIter.hasNext()) {
			if (partitionIdx >= folds) {
				partitionIdx = 0;
			}
			List<Snippet> partition = partitions.get(partitionIdx);
			partition.add(snippetIter.next());
			partitionIdx++;
		}

		
		List<CVScore> results = new ArrayList<>(folds);
		
		for (List<Snippet> partition : partitions) {
			// set up training and testing sets for this fold
			List<Snippet> training = partition;
			List<Snippet> testing = new ArrayList<>();
			for (List<Snippet> p : partitions) {
				if (p != training) {
					testing.addAll(p);
				}
			}
			
			// Train
			List<LSTriplet> trained = vttr.extractRegexExpressions(training, label);
			LSExtractor ex = new LSExtractor(trained);

			// Test
			CVScore score = new CVScore();
			for (Snippet snippet : testing) {
				List<String> candidates = ex.extract(snippet.getText());
				String predicted = chooseBestCandidate(candidates);
				String actual = snippet.getLabeledSegment();
				
				// Score
				if (predicted == null) {
					if (actual == null) {
						score.setTn(score.getTn() + 1);
					} else {
						score.setFn(score.getFn() + 1);
					}
				} else if (actual == null) {
					score.setFp(score.getFp() + 1);
				} else {
					if (predicted.equals(snippet.getLabeledSegment())) {
						score.setTp(score.getTp() + 1);
					} else {
						score.setFp(score.getFp() + 1);
					}
				}
			}
			results.add(score);
		}
		return results;
	}
	

	/**
	 * @param candidates
	 * @return
	 */
	private static String chooseBestCandidate(List<String> candidates) {
		String category = null;
		if (candidates != null && candidates.size() > 0) {
			if (candidates.size() == 1) {
				category = candidates.get(0);
			} else {
				// Multiple candidates, count their frequencies.
				Map<String, Integer> candidate2freq = new HashMap<String, Integer>();
				for (String c : candidates) {
					Integer freq = candidate2freq.get(c);
					if (freq == null) {
						freq = Integer.valueOf(1);
					} else {
						freq = Integer.valueOf(freq.intValue() + 1);
					}
					candidate2freq.put(c, freq);
				}
				// Sort by frequency
				TreeMap<Integer, List<String>> freq2candidates = new TreeMap<>();
				for (Map.Entry<String, Integer> c2f : candidate2freq.entrySet()) {
					List<String> freqCandidates = freq2candidates.get(c2f.getValue());
					if (freqCandidates == null) {
						freqCandidates = new ArrayList<>();
						freq2candidates.put(c2f.getValue(), freqCandidates);
					}
					freqCandidates.add(c2f.getKey());
				}
				List<String> mostFrequentCandidates = freq2candidates.lastEntry().getValue();
				// If there is only one candidate in the group with the highest frequency then use it.
				if (mostFrequentCandidates.size() == 1) {
					category = mostFrequentCandidates.get(0);
				} else {
					// Multiple candidates with the highest frequency.
					// Choose the longest one, and if there is a tie then choose the largest one lexicographically.
					Collections.sort(mostFrequentCandidates, new Comparator<String>() {
						@Override
						public int compare(String o1, String o2) {
							if (o1 == o2) {
								return 0;
							}
							if (o1 == null) {
								return 1;
							}
							if (o2 == null) {
								return -1;
							}
							if (o1.length() == o2.length()) {
								return o2.compareTo(o1);
							}
							return o2.length() - o1.length();
						}
					});
					category = mostFrequentCandidates.get(0);
				}
			}
		}
		return category;
	}

}
