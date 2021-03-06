/*
 *  Copyright 2014 United States Department of Veterans Affairs,
 *		Health Services Research & Development Service
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 */
package gov.va.research.red.cat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.va.research.red.CVScore;
import gov.va.research.red.CVUtils;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;
import gov.va.research.red.VTTSnippetParser;

/**
 * @author doug
 *
 */
public class CrossValidateCategorizer {
	
	private static Logger LOG = LoggerFactory.getLogger("CrossValidateCategorizer");

	public List<CVScore> crossValidateClassifier(List<File> vttFiles,
			List<String> yesLabels, List<String> noLabels, int folds,
			boolean biasForRecall) throws IOException, URISyntaxException {
		VTTReader vttr = new VTTReader();
		// get snippets
		List<Snippet> snippetsYes = new ArrayList<>();
		for (File vttFile : vttFiles) {
			for (String label : yesLabels) {
				snippetsYes.addAll(vttr.readSnippets(vttFile, label, new VTTSnippetParser()));
			}
		}

		List<Snippet> snippetsNo = new ArrayList<>();
		for (File vttFile : vttFiles) {
			for (String label : noLabels) {
				snippetsNo.addAll(vttr.readSnippets(vttFile, label, new VTTSnippetParser()));
			}
		}

		List<Snippet> snippetsUnlabeled = new ArrayList<>();
		for (File vttFile : vttFiles) {
			snippetsUnlabeled.addAll(vttr.readSnippets(vttFile, new VTTSnippetParser()));
		}
		return crossValidateClassifier(snippetsYes, snippetsNo,
				snippetsUnlabeled, yesLabels, noLabels, folds, biasForRecall);
	}

	public List<CVScore> crossValidateClassifier(List<Snippet> snippetsYes,
			List<Snippet> snippetsNo, List<Snippet> snippetsUnlabeled,
			Collection<String> yesLabels, Collection<String> noLabels,
			int folds, boolean biasForRecall) throws IOException,
			URISyntaxException {
		// randomize the order of the snippets
		if (biasForRecall) {
			snippetsYes.addAll(snippetsUnlabeled);
		} else {
			snippetsNo.addAll(snippetsUnlabeled);
		}
		Collections.shuffle(snippetsYes);
		Collections.shuffle(snippetsNo);

		// partition snippets into one partition per fold
		List<List<Snippet>> partitionsYes = CVUtils.partitionSnippets(folds,
				snippetsYes);
		List<List<Snippet>> partitionsNo = CVUtils.partitionSnippets(folds,
				snippetsNo);

		// Run evaluations, "folds" number of times, alternating which partition
		// is being used for testing.
		List<CVScore> results = new ArrayList<>(folds);
		// PrintWriter pw = new PrintWriter(new
		// File("ten-fold cross validation.txt"));
		// int fold = 0;
		System.out.println("Estimating performance with " + folds
				+ "-fold cross validation:");
		for (int i = 0; i < folds; i++) {
			List<Snippet> testingYes = partitionsYes.get(i);
			List<Snippet> testingNo = partitionsNo.get(i);
			// set up training and testing sets for this fold
			List<Snippet> trainingYes = new ArrayList<>();
			for (List<Snippet> p : partitionsYes) {
				if (p != testingYes) {
					trainingYes.addAll(p);
				}
			}

			List<Snippet> trainingNo = new ArrayList<>();
			for (List<Snippet> p : partitionsNo) {
				if (p != testingNo) {
					trainingNo.addAll(p);
				}
			}

			List<String> snippetsTrain = new ArrayList<>();
			List<List<Integer>> segspansTrain = new ArrayList<>();
			List<Integer> labelsTrain = new ArrayList<>();
			int numYes = 0;
			for (Snippet snip : trainingYes) {
				if (snip.getLabeledSegments().size() == 0)
					continue;
				String text = snip.getText();
				String ls = snip.getLabeledStrings().get(0);
				int start = text.indexOf(ls);
				if (start == 1)
					continue;
				snippetsTrain.add(text);
				List<Integer> span = new ArrayList<>();
				span.add(start);
				span.add(start + ls.length());
				segspansTrain.add(span);
				labelsTrain.add(1);
				numYes++;
			}
			int numNo = 0;
			for (Snippet snip : trainingNo) {
				if (snip.getLabeledSegments().size() == 0)
					continue;
				String text = snip.getText();
				String ls = snip.getLabeledStrings().get(0);
				int start = text.indexOf(ls);
				if (start == 1)
					continue;
				snippetsTrain.add(text);
				List<Integer> span = new ArrayList<>();
				span.add(start);
				span.add(start + ls.length());
				segspansTrain.add(span);
				labelsTrain.add(-1);
				numNo++;
			}
			IREDClassifier redc = REDClassifierFactory.createModel();
			LOG.info(
					System.lineSeparator()
					+ "##### FOLD " + (i + 1) + " #####" + System.lineSeparator()
					+ "# positive snippets = " + numYes + System.lineSeparator()
					+ "# negative snippets = " + numNo);
			validateForFit(snippetsTrain, segspansTrain, labelsTrain);

			redc.fit(snippetsTrain, segspansTrain, labelsTrain);

			List<String> snippetsTest = new ArrayList<>();
			List<Integer> actualLabels = new ArrayList<>();
			for (Snippet snip : testingYes) {
				String text = snip.getText();
				snippetsTest.add(text);
				actualLabels.add(1);
			}
			for (Snippet snip : testingNo) {
				String text = snip.getText();
				snippetsTest.add(text);
				actualLabels.add(-1);
			}

			List<Integer> predictedLabels = redc.predict(snippetsTest, biasForRecall ? 1 : -1);

			if (actualLabels.size() != predictedLabels.size()) {
				throw new IllegalArgumentException("test classifications and predicted classifications have different sizes: " + actualLabels.size() + ", " + predictedLabels.size());
			}

			CVScore score = new CVScore();
			for (int k = 0; k < actualLabels.size(); k++) {
				int actualLabel = actualLabels.get(k);
				int predLabel = predictedLabels.get(k);
				if (actualLabel == 1 && predLabel == 1)
					score.setTp(score.getTp() + 1);
				else if (actualLabel == 1 && predLabel == -1)
					score.setFn(score.getFn() + 1);
				else if (actualLabel == -1 && predLabel == -1)
					score.setTn(score.getTn() + 1);
				else if (actualLabel == -1 && predLabel == 1)
					score.setFp(score.getFp() + 1);
				else
					throw new IllegalArgumentException("Bad classes when comparing actual and predicted: " + actualLabel + ", " + predLabel);
			}
			results.add(score);
		}
		// pw.close();
		System.out.println("Done with " + folds + "-fold cross validation.");
		return results;
	}

	private boolean validateForFit(List<String> snippetsTrain,
			List<List<Integer>> segspansTrain, Collection<Integer> labelsTrain) {
		boolean valid = true;
		if (snippetsTrain.size() != segspansTrain.size()
				|| segspansTrain.size() != labelsTrain.size()) {
			System.err
					.println("snippet, segment, and label lists have different sizes: "
							+ snippetsTrain.size()
							+ ", "
							+ segspansTrain.size() + ", " + labelsTrain.size());
		}
		for (String st : snippetsTrain) {
			if (st == null || st.trim().length() == 0) {
				System.err.println("bad snippet");
				valid = false;
			}
		}
		for (List<Integer> sgs : segspansTrain) {
			if (sgs == null) {
				System.err.println("segspans was null");
				valid = false;
			} else if (sgs.size() != 2) {
				System.err.println("segspans size was not 2: " + sgs.size());
				valid = false;
			} else if (sgs.get(0) >= sgs.get(1)) {
				System.err.println("illegal segspan: " + sgs.get(0) + ","
						+ sgs.get(1));
				valid = false;
			}
		}
		Set<Integer> uniqueLabels = new HashSet<>(labelsTrain);
		if (uniqueLabels == null || uniqueLabels.size() == 0) {
			System.err.println("empty training labels");
			valid = false;
		} else if (!uniqueLabels.contains(1)) {
			System.err.println("no positive training labels");
			valid = false;
		} else if (!uniqueLabels.contains(-1)) {
			System.err.println("no negative training labels");
			valid = false;
		}

		return valid;
	}

}
