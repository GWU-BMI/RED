package gov.va.research.red.ex;

import gov.va.research.red.CVResult;
import gov.va.research.red.CVScore;
import gov.va.research.red.CVUtils;
import gov.va.research.red.CrossValidatable;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
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

	private static final Logger LOG = LoggerFactory
			.getLogger(REDExCrossValidator.class);

	public static void main(String[] args) throws IOException,
			ConfigurationException {
		if (args.length != 1) {
			System.out.println("Arguments: <properties file>");
		} else {
			Configuration conf = new PropertiesConfiguration(args[0]);
			List<Object> vttfileObjs = conf.getList("vtt.file");
			List<File> vttfiles = new ArrayList<>(vttfileObjs.size());
			for (Object vf : vttfileObjs) {
				File f = new File((String) vf);
				if (f.exists()) {
					vttfiles.add(new File((String) vf));
				} else {
					throw new FileNotFoundException((String) vf);
				}
			}
			List<Object> labelObjs = conf.getList("label");
			List<String> labels = new ArrayList<>(labelObjs.size());
			for (Object label : labelObjs) {
				labels.add((String) label);
			}
			int folds = conf.getInt("folds");
			Boolean allowOvermatches = conf.getBoolean("allow.overmatches",
					Boolean.TRUE);
			Boolean caseInsensitive = conf.getBoolean("case.insensitive",
					Boolean.TRUE);
			Boolean stopAfterFirstFold = conf.getBoolean(
					"stop.after.first.fold", Boolean.FALSE);
			Boolean shuffle = conf.getBoolean("shuffle", Boolean.TRUE);
			List<Object> holdoutObjs = conf.getList("holdout");
			List<String> holdouts = null;
			if (holdoutObjs == null) {
				holdouts = new ArrayList<>(0);
			} else {
				holdouts = new ArrayList<>(holdoutObjs.size());
				for (Object hoo : holdoutObjs) {
					holdouts.add(hoo.toString());
				}
			}
			Boolean useTier2 = conf.getBoolean("use.tier2", Boolean.TRUE);
			int limit = conf.getInt("snippet.limit", -1);
			new File("log").mkdir();

			REDExCrossValidator rexcv = new REDExCrossValidator();
			List<CVResult> results = rexcv
					.crossValidate(vttfiles, labels, folds, allowOvermatches,
							caseInsensitive, holdouts, useTier2,
							stopAfterFirstFold.booleanValue(), shuffle, limit);

			// Display results
			int i = 0;
			for (CVResult s : results) {
				if (s != null && s.getScore() != null) {
					LOG.info("\n--- Run " + (i++) + " ---\n"
							+ s.getScore().getEvaluation());
				} else {
					LOG.info("\n--- Run " + (i++) + " ---\nnull score");
				}
			}
			CVResult aggregate = CVResult.aggregate(results);
			LOG.info("\n--- Aggregate ---\n"
					+ aggregate.getScore().getEvaluation());
			LOG.info("# Regexes Discovered: " + aggregate.getRegExes().size());
			String regexOutputFile = conf.getString("regex.output.file");
			if (regexOutputFile != null) {
				try (FileWriter fw = new FileWriter(regexOutputFile)) {
					try (PrintWriter pw = new PrintWriter(fw)) {
						for (String regex : aggregate.getRegExes()) {
							pw.println(regex);
						}
					}
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gov.va.research.red.CrossValidatable#crossValidate(java.util.List,
	 * java.lang.String, int)
	 */
	@Override
	public List<CVResult> crossValidate(List<File> vttFiles, String label,
			int folds, boolean shuffle, int limit) throws IOException {
		return crossValidate(vttFiles, label, folds, true, true,
				new ArrayList<>(0), true, false, shuffle, limit);
	}

	public List<CVResult> crossValidate(List<File> vttFiles, String label, int folds,
			Boolean allowOvermatches, Boolean caseInsensitive,
			List<String> holdouts, Boolean useTier2,
			Boolean stopAfterFirstFold, Boolean shuffle, int limit)
			throws IOException {
		Collection<String> labels = new ArrayList<>(1);
		labels.add(label);
		return crossValidate(vttFiles, labels, folds, allowOvermatches,
				caseInsensitive, holdouts, useTier2, stopAfterFirstFold,
				shuffle, limit);
	}

	/**
	 * @param vttFiles
	 *            List of vtt files for training/testing.
	 * @param labels
	 *            The labels to train on. All labels in this list are treated as
	 *            equivalent. Labels not in this list are ignored.
	 * @param folds
	 *            Number of folds in the cross validation.
	 * @param allowOverMatches
	 *            If <code>false</code> then predicated and actual values must
	 *            match exactly to be counted as a true positive. If
	 *            <code>true</code> then if the predicted and actual values
	 *            overlap but do not match exactly, it is still counted as a
	 *            true positive.
	 * @param caseInsensitive
	 *            If <code>true</code> then all text is converted to lowercase
	 *            (in order, for example, to make case-insensitive comparisons
	 *            easier)
	 * @param stopAfterFirstFold
	 *            If <code>true</code> then the cross validation quits after the
	 *            first fold.
	 * @param shuffle
	 *            If <code>true</code> then the snippets will be shuffled before
	 *            cross validation. This will make the cross-validation
	 *            non-deterministic, having a different result each time.
	 * @param limit
	 *            Limit the number of snippets this value. A value <= 0 means no
	 *            limit.
	 * @return The aggregated results of the cross validation, including scores
	 *         and regular expressions.
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	List<CVResult> crossValidate(List<File> vttFiles,
			Collection<String> labels, int folds, Boolean allowOverMatches,
			Boolean caseInsensitive, List<String> holdouts, Boolean useTier2,
			Boolean stopAfterFirstFold, Boolean shuffle, int limit)
			throws IOException, FileNotFoundException {
		VTTReader vttr = new VTTReader();
		// get snippets
		List<Snippet> snippets = new ArrayList<>();
		for (File vttFile : vttFiles) {
			Collection<Snippet> fileSnippets = vttr.findSnippets(vttFile,
					labels, caseInsensitive);
			snippets.addAll(fileSnippets);
		}
		LOG.info("Cross validating " + snippets.size() + " snippets from "
				+ vttFiles + " files.\n" + "Folds: " + folds
				+ "\nallow.overmatches: " + allowOverMatches
				+ "\ncase.insensitive: " + caseInsensitive + "\nholdouts: "
				+ holdouts + "\nuse.tier2: " + useTier2
				+ "\nstop.after.first.fold: " + stopAfterFirstFold
				+ "\nshuffle: " + shuffle + "\nsnippet.limit: " + limit);
		return crossValidateSnippets(snippets, labels, folds, allowOverMatches,
				caseInsensitive, holdouts, useTier2, stopAfterFirstFold,
				shuffle, limit);
	}

	/**
	 * @param snippets
	 *            List of snippets for training/testing.
	 * @param labels
	 *            The labels to train on. All labels in this list are treated as
	 *            equivalent. Labels not in this list are ignored.
	 * @param folds
	 *            Number of folds in the cross validation.
	 * @param allowOverMatches
	 *            If <code>false</code> then predicated and actual values must
	 *            match exactly to be counted as a true positive. If
	 *            <code>true</code> then if the predicted and actual values
	 *            overlap but do not match exactly, it is still counted as a
	 *            true positive.
	 * @param caseInsensitive
	 *            If <code>true</code> then all text is converted to lowercase
	 *            (in order, for example, to make case-insensitive comparisons
	 *            easier)
	 * @param holdouts
	 *            no members of this list will be generalized.
	 * @param useTier2
	 *            if <code>false</code> then tier 2 regular expressions will not
	 *            be used, only tier 1.
	 * @param stopAfterFirstFold
	 *            If <code>true</code> then the cross validation quits after the
	 *            first fold.
	 * @param shuffle
	 *            If <code>true</code> then the snippets will be shuffled before
	 *            cross validation. This will make the cross-validation
	 *            non-deterministic, having a different result each time.
	 * @param limit
	 *            Limit the number of snippets this value. A value &le; 0 means
	 *            no limit.
	 * @return The aggregated results of the cross validation, including scores
	 *         and regular expressions.
	 * @throws FileNotFoundException
	 *             if the log files cannot be written to
	 */
	public List<CVResult> crossValidateSnippets(List<Snippet> snippets,
			Collection<String> labels, int folds, Boolean allowOverMatches,
			Boolean caseInsensitive, List<String> holdouts, Boolean useTier2,
			Boolean stopAfterFirstFold, Boolean shuffle, int limit)
			throws FileNotFoundException {
		// randomize the order of the snippets
		if (shuffle) {
			Collections.shuffle(snippets);
		}

		// limit the number of snippets
		if (limit > 0 && limit < snippets.size()) {
			List<Snippet> limited = new ArrayList<>(limit);
			for (int i = 0; i < limit; i++) {
				limited.add(snippets.get(i));
			}
			snippets = limited;
		}

		// partition snippets into one partition per fold
		List<List<Snippet>> partitions = CVUtils.partitionSnippets(folds,
				snippets);

		// Run evaluations, "folds" number of times, alternating which partition
		// is being used for testing.
		List<CVResult> results = new ArrayList<>(folds);
		File logDir = new File("log");
		if (!logDir.exists()) {
			logDir.mkdir();
		}
		try (PrintWriter testingPW = new PrintWriter(
				new File("log/testing.txt"));
				PrintWriter trainingPW = new PrintWriter(new File(
						"log/training.txt"))) {
			AtomicInteger fold = new AtomicInteger(0);
			for (List<Snippet> partition : partitions) {
				CVScore score = null;
				List<String> regExes = null;
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
						REDExtractor ex = null;
						try {
							ex = trainExtractor(training, allowOverMatches,
									caseInsensitive, holdouts, useTier2,
									trainingPW, "" + newFold);
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
						if (ex == null) {
							pw.println("null REDExtractor");
						} else {
							// Test
							REDExFactory rexe = new REDExFactory();
							score = rexe.test(testing, ex, allowOverMatches,
									caseInsensitive, pw);
							List<List<String>> tieredRegexes = ex.getRegularExpressions();
							regExes = new ArrayList<>();
							for (List<String> tierRegexs : tieredRegexes) {
								regExes.addAll(tierRegexs);
							}
						}
					}
					if (score == null) {
						LOG.info("\n null score");
						testingPW.println("null score");
					} else {
						LOG.info("\n" + score.getEvaluation());
						testingPW.println();
						testingPW.println(sw.toString());
						testingPW.println();
						testingPW.println(score.getEvaluation());
					}
					testingPW.flush();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				results.add(new CVResult(score, regExes));
			}
		}
		return results;
	}

	/**
	 * @param snippets
	 *            List of snippets for training/testing.
	 * @param labels
	 *            The labels to train on. All labels in this list are treated as
	 *            equivalent. Labels not in this list are ignored.
	 * @param allowOverMatches
	 *            If <code>false</code> then predicated and actual values must
	 *            match exactly to be counted as a true positive. If
	 *            <code>true</code> then if the predicted and actual values
	 *            overlap but do not match exactly, it is still counted as a
	 *            true positive.
	 * @param caseInsensitive
	 *            If <code>true</code> then all text is converted to lowercase
	 *            (in order, for example, to make case-insensitive comparisons
	 *            easier)
	 * @param holdouts
	 *            no members of this list will be generalized.
	 * @param useTier2
	 *            if <code>false</code> then tier 2 regular expressions will not
	 *            be used, only tier 1.
	 * @param stopAfterFirstFold
	 *            If <code>true</code> then the cross validation quits after the
	 *            first fold.
	 * @param shuffle
	 *            If <code>true</code> then the snippets will be shuffled before
	 *            cross validation. This will make the cross-validation
	 *            non-deterministic, having a different result each time.
	 * @param limit
	 *            Limit the number of snippets this value. A value &le; 0 means
	 *            no limit.
	 * @return The aggregated results of the cross validation, including scores
	 *         and regular expressions.
	 * @throws FileNotFoundException
	 *             if the log files cannot be written to
	 */
	public List<CVResult> leaveOneOutValidateSnippets(List<Snippet> snippets,
			Collection<String> labels, Boolean allowOverMatches,
			Boolean caseInsensitive, List<String> holdouts, Boolean useTier2,
			Boolean stopAfterFirstFold, Boolean shuffle, int limit)
			throws FileNotFoundException {
		return crossValidateSnippets(snippets, labels, snippets.size(), allowOverMatches, caseInsensitive, holdouts, useTier2, stopAfterFirstFold, shuffle, limit);
	}

	/**
	 * @param labels
	 *            only snippets with these labels will be used in the training.
	 * @param training
	 *            the snippets to be used for training.
	 * @param pw
	 *            a print writer for displaying output of the training. May be
	 *            <code>null</code>.
	 * @return an extractor containing regexes discovered during training.
	 * @throws IOException
	 */
	private REDExtractor trainExtractor(List<Snippet> training,
			Boolean allowOverMatches, Boolean caseInsensitive,
			List<String> holdouts, Boolean useTier2, PrintWriter pw,
			String outputTag) throws IOException {
		REDExFactory rexe = new REDExFactory();
		REDExtractor ex = rexe.train(training, allowOverMatches, outputTag,
				caseInsensitive, false, holdouts, useTier2);
		if (pw != null) {
			List<Snippet> labelled = new ArrayList<>();
			List<Snippet> unlabelled = new ArrayList<>();
			for (Snippet trainingSnippet : training) {
				boolean isLabelled = trainingSnippet.getPosLabeledSegments()
						.size() > 0;
				if (isLabelled) {
					labelled.add(trainingSnippet);
				} else {
					unlabelled.add(trainingSnippet);
				}
			}
			pw.println("--- Training snippets:");
			for (Snippet s : labelled) {
				pw.println("--- pos");
				pw.println(s.getText());
			}
			for (Snippet s : unlabelled) {
				pw.println("--- neg");
				pw.println(s.getText());
			}
			pw.println();
			pw.println("--- Trained Regexes:");
			int rank = 1;
			if (ex == null) {
				pw.println("null REDExtractor");
			} else {
				for (Collection<SnippetRegEx> sres : ex
						.getRankedSnippetRegExs()) {
					pw.println("--- Rank " + rank);
					for (SnippetRegEx trainedSre : sres) {
						pw.println(trainedSre.toString());
						pw.println("----------");
					}
					rank++;
				}
			}
		}
		return ex;
	}

}
