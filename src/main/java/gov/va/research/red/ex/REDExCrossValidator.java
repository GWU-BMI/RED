package gov.va.research.red.ex;

import gov.va.research.red.CVScore;
import gov.va.research.red.CrossValidatable;
import gov.va.research.red.LSTriplet;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class REDExCrossValidator implements CrossValidatable {

	private static final Logger LOG = LoggerFactory.getLogger(REDExCrossValidator.class);

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
			
			REDExCrossValidator rexcv = new REDExCrossValidator();
			List<CVScore> results = rexcv.crossValidate(vttfiles, label, folds);
			
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

	/* (non-Javadoc)
	 * @see gov.va.research.red.CrossValidatable#crossValidate(java.util.List, java.lang.String, int)
	 */
	@Override
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
			LSExtractor ex = trainExtractor(label, training, pw);

			// Test
			REDExtractor rexe = new REDExtractor();
			CVScore score = rexe.testExtractor(testing, ex, pw);

			results.add(score);
		}
		pw.close();
		return results;
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
	 * @param label only snippets with this label will be used in the training.
	 * @param training the snippets to be used for training.
	 * @return an extractor containing regexes discovered during training.
	 * @throws IOException
	 */
	private LSExtractor trainExtractor(String label, List<Snippet> training) throws IOException {
		return trainExtractor(label, training, null);
	}

	/**
	 * @param label only snippets with this label will be used in the training.
	 * @param training the snippets to be used for training.
	 * @param pw a print writer for displaying output of the training. May be <code>null</code>.
	 * @return an extractor containing regexes discovered during training.
	 * @throws IOException
	 */
	private LSExtractor trainExtractor(String label, List<Snippet> training, PrintWriter pw) throws IOException {
		REDExtractor rexe = new REDExtractor();
		List<LSTriplet> trained = rexe.extractRegexExpressions(training, label, null);
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
	
}
