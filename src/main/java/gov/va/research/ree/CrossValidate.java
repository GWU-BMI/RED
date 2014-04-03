package gov.va.research.ree;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrossValidate {

	private static final Logger LOG = LoggerFactory.getLogger(CrossValidate.class);
	
	public static void main(String[] args) throws IOException, ConfigurationException {
		if (args.length != 1) {
			System.out.println("Arguments: <properties file>");
		} else {
			Configuration conf = new PropertiesConfiguration(args[0]);
			List<Object> vttfileObjs = conf.getList("vtt.file");
			List<File> vttfiles = new ArrayList<>(vttfileObjs.size());
			for (Object vf : vttfileObjs) {
				vttfiles.add(new File((String)vf));
			}
			String label = conf.getString("label");
			int folds = conf.getInt("folds");
			
			CrossValidate cv = new CrossValidate();
			List<CVScore> results = cv.crossValidate(vttfiles, label, folds);
			
			// Display results
			int i = 0;
			for (CVScore s : results) {
				LOG.info("--- Run " + (i++) + " ---");
				LOG.info(s.getEvaluation());
			}
			LOG.info("--- Aggregate ---");
			LOG.info(CVScore.aggregate(results).getEvaluation());

		}
	}

	public List<CVScore> crossValidate(List<File> vttFiles, String label, int folds)
			throws IOException {
		VTTReader vttr = new VTTReader();
		// get snippets
		List<Snippet> snippets = new ArrayList<>();
		for (File vttFile : vttFiles) {
			snippets.addAll(vttr.extractSnippets(vttFile, label));
		}
		
		// randomize the order of the snippets
		Collections.shuffle(snippets);
		
		// partition snippets into one partition per fold
		List<List<Snippet>> partitions = partitionSnippets(folds, snippets);

		// Run evaluations, "folds" number of times, alternating which partition is being used for testing.
		List<CVScore> results = new ArrayList<>(folds);
		PrintWriter pw = new PrintWriter(new File("training and testing.txt"));
		int fold = 0;
		for (List<Snippet> partition : partitions) {
			pw.println("##### FOLD " + (++fold) + " #####");
			// set up training and testing sets for this fold
			List<Snippet> testing = partition;
			List<Snippet> training = new ArrayList<>();
			for (List<Snippet> p : partitions) {
				if (p != testing) {
					training.addAll(p);
				}
			}

			// Train
			LSExtractor ex = trainExtractor(label, vttr, training, pw);

			// Test
			CVScore score = testExtractor(testing, ex, pw);

			results.add(score);
		}
		pw.close();
		return results;
	}

	/**
	 * @param pw
	 * @param testing
	 * @param ex
	 * @return
	 */
	private CVScore testExtractor(List<Snippet> testing,
			LSExtractor ex, PrintWriter pw) {
		if (pw != null) {
			pw.println();
		}
		CVScore score = new CVScore();
		for (Snippet snippet : testing) {
			List<String> candidates = ex.extract(snippet.getText());
			String predicted = chooseBestCandidate(candidates);
			String actual = snippet.getLabeledSegment();
			if (pw != null) {
				pw.println("--- Test Snippet:");
				pw.println(snippet.getText());
				pw.println("Predicted: " + predicted + ", Actual: " + actual);
			}
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
		return score;
	}
	
	/**
	 * is called from the VTTReader class. Method is used when trimming
	 * regex's. It is used to see if any false positives are genereated
	 * by the new regex.
	 * @param testing
	 * @param ex
	 * @param pw
	 * @return
	 */
	public CVScore testExtractor(List<Snippet> testing,
			LSExtractor ex) {
		/*if (pw != null) {
			pw.println();
		}*/
		CVScore score = new CVScore();
		for (Snippet snippet : testing) {
			List<String> candidates = ex.extract(snippet.getText());
			String predicted = chooseBestCandidate(candidates);
			String actual = snippet.getLabeledSegment();
			/*if (pw != null) {
				pw.println("--- Test Snippet:");
				pw.println(snippet.getText());
				pw.println("Predicted: " + predicted + ", Actual: " + actual);
			}*/
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
		return score;
	}

	/**
	 * @param label
	 * @param vttr
	 * @param pw
	 * @param training
	 * @return
	 * @throws IOException
	 */
	private LSExtractor trainExtractor(String label, VTTReader vttr,
			List<Snippet> training, PrintWriter pw) throws IOException {
		List<LSTriplet> trained = vttr.extractRegexExpressions(training, label);
		if (pw != null) {
			pw.println("--- Training snippets:");
			for (Snippet trainingSnippet : training) {
				pw.println(trainingSnippet.getText());
				pw.println("----------");
			}
			pw.println();
			pw.println("--- Trained Regexes:");
			for (LSTriplet trainedTriplet : trained) {
				pw.println(trainedTriplet.toStringRegEx());
				pw.println("----------");
			}
		}
		LSExtractor ex = new LSExtractor(trained);
		return ex;
	}

	/**
	 * @param folds
	 * @param snippets
	 * @return
	 */
	private List<List<Snippet>> partitionSnippets(int folds,
			List<Snippet> snippets) {
		List<List<Snippet>> partitions = new ArrayList<>(folds);
		for (int i = 0; i < folds; i++) {
			partitions.add(new ArrayList<Snippet>());
		}
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
		return partitions;
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
