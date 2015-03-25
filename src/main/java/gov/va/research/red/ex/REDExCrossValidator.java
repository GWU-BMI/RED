package gov.va.research.red.ex;

import gov.va.research.red.CVScore;
import gov.va.research.red.CVUtils;
import gov.va.research.red.CrossValidatable;
import gov.va.research.red.LSTriplet;
import gov.va.research.red.LabeledSegment;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
			List<Object> labelObjs = conf.getList("label");
			List<String> labels = new ArrayList<>(labelObjs.size());
			for (Object label : labelObjs) {
				labels.add((String)label);
			}
			int folds = conf.getInt("folds");
			Boolean allowOvermatches = conf.getBoolean("allowOvermatches", Boolean.TRUE);
			Boolean convertToLowercase = conf.getBoolean("convertToLowercase", Boolean.TRUE);
			Boolean stopAfterFirstFold = conf.getBoolean("stopAfterFirstFold", Boolean.FALSE);
			
			REDExCrossValidator rexcv = new REDExCrossValidator();
			List<CVScore> results = rexcv.crossValidate(vttfiles, labels, folds, allowOvermatches, convertToLowercase, stopAfterFirstFold.booleanValue());
			
			// Display results
			int i = 0;
			for (CVScore s : results) {
				if (s != null) {
					LOG.info("--- Run " + (i++) + " ---");
					LOG.info(s.getEvaluation());
				}
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
		return crossValidate(vttFiles, label, folds, true, true, false);
	}
	
	List<CVScore> crossValidate(List<File> vttFiles, String label, int folds, boolean allowOvermatches, boolean convertToLowercase, boolean stopAfterFirstFold)
				throws IOException {
		Collection<String> labels = new ArrayList<>(1);
		labels.add(label);
		return crossValidate(vttFiles, labels, folds, allowOvermatches, convertToLowercase, stopAfterFirstFold);
	}

	/**
	 * @param vttFiles
	 * @param label
	 * @param folds
	 * @param allowOverMatches
	 *            If <code>false</code> then predicated and actual values must
	 *            match exactly to be counted as a true positive. If
	 *            <code>true</code> then if the predicted and actual values
	 *            overlap but do not match exactly, it is still counted as a
	 *            true positive.
	 * @param convertToLowercase If <code>true</code> then all text is converted to lowercase (in order, for example, to make case-insensitive comparisons easier)
	 * @param stopAfterFirstFold
	 * @return
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	List<CVScore> crossValidate(List<File> vttFiles, Collection<String> labels, int folds,
			boolean allowOverMatches, boolean convertToLowercase, boolean stopAfterFirstFold) throws IOException,
			FileNotFoundException {
		VTTReader vttr = new VTTReader();
		// get snippets
		List<Snippet> snippets = new ArrayList<>();
		for (File vttFile : vttFiles) {
			snippets.addAll(vttr.findSnippets(vttFile, labels, convertToLowercase));
		}
		LOG.info("Cross validating " + snippets.size() + " snippets from " + vttFiles +  " files.");
		
		// randomize the order of the snippets
		Collections.shuffle(snippets);
		
		// partition snippets into one partition per fold
		List<List<Snippet>> partitions = CVUtils.partitionSnippets(folds, snippets);

		// Run evaluations, "folds" number of times, alternating which partition is being used for testing.
		List<CVScore> results = new ArrayList<>(folds);
		try (PrintWriter testingPW = new PrintWriter(new File("testing.txt"));
			 PrintWriter trainingPW = new PrintWriter(new File("training.txt"))	) {
			AtomicInteger fold = new AtomicInteger(0);
			for (List<Snippet> partition : partitions) {
				CVScore score = null;
				try (StringWriter sw = new StringWriter()) {
					try (PrintWriter pw = new PrintWriter(sw)) {
						int newFold = fold.addAndGet(1);
						pw.println("##### FOLD " + newFold + " #####");
						if (stopAfterFirstFold && (newFold > 1)) {
							pw.println(">>> skipping");
							continue;
						}
						// set up training and testing sets for this fold
						List<Snippet> testing = partition;
						List<Snippet> training = new ArrayList<>();
						for (List<Snippet> p : partitions) {
							if (p != testing) {
								training.addAll(p);
							}
						}
			
						// Train
						LSExtractor ex;
						try {
							ex = trainExtractor(labels, training, allowOverMatches, trainingPW);
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
			
						// Test
						REDExtractor rexe = new REDExtractor();
						score = rexe.testExtractor(testing, ex, allowOverMatches, pw);
					}
					LOG.info("\n" + score.getEvaluation());
					testingPW.println();
					testingPW.println(sw.toString());
					testingPW.println();
					testingPW.println(score.getEvaluation());
					testingPW.flush();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				results.add(score);
			};
		}
		return results;
	}

	/**
	 * @param label only snippets with this label will be used in the training.
	 * @param training the snippets to be used for training.
	 * @return an extractor containing regexes discovered during training.
	 * @throws IOException
	 */
	private LSExtractor trainExtractor(String label, List<Snippet> training, boolean allowOverMatches) throws IOException {
		Collection<String> labels = new ArrayList<>(1);
		labels.add(label);
		return trainExtractor(labels, training, allowOverMatches, null);
	}

	/**
	 * @param labels only snippets with these labels will be used in the training.
	 * @param training the snippets to be used for training.
	 * @param pw a print writer for displaying output of the training. May be <code>null</code>.
	 * @return an extractor containing regexes discovered during training.
	 * @throws IOException
	 */
	private LSExtractor trainExtractor(Collection<String> labels, List<Snippet> training, boolean allowOverMatches, PrintWriter pw) throws IOException {
		REDExtractor rexe = new REDExtractor();
		List<LSTriplet> trained = rexe.discoverRegularExpressions(training, labels, allowOverMatches, null);
		if (pw != null) {
			List<Snippet> labelled = new ArrayList<>();
			List<Snippet> unlabelled = new ArrayList<>();
			for (Snippet trainingSnippet : training) {
				boolean isLabelled = false;
				if (trainingSnippet.getLabeledSegments() != null) {
					for (LabeledSegment ls : trainingSnippet.getLabeledSegments()) {
						if (CVUtils.containsCI(labels, ls.getLabel())) {
							isLabelled = true;
							break;
						}
					}
				}
				if (isLabelled) {
					labelled.add(trainingSnippet);
				} else {
					unlabelled.add(trainingSnippet);
				}
			}
			pw.println("--- Training snippets:");
			for (Snippet s : labelled) {
				pw.println("--- pos. for " + labels);
				pw.println(s.getText());
			}
			for (Snippet s : unlabelled) {
				pw.println("--- neg. for " + labels);
				pw.println(s.getText());
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
