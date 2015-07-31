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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import gov.va.research.red.CVScore;
import gov.va.research.red.CVUtils;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;

/**
 * @author doug
 *
 */
public class CrossValidateCategorizer {

	public List<CVScore> crossValidateClassifier(List<File> vttFiles, List<String> yesLabels, List<String> noLabels, int folds, boolean biasForRecall)
			throws IOException {
		VTTReader vttr = new VTTReader();
		// get snippets
		List<Snippet> snippetsYes = new ArrayList<>();
		for (File vttFile : vttFiles) {
			for (String label : yesLabels) {
				snippetsYes.addAll(vttr.extractSnippets(vttFile, label, true));
			}
		}
		
		List<Snippet> snippetsNo = new ArrayList<>();
		for (File vttFile : vttFiles) {
			for (String label : noLabels) {
				snippetsNo.addAll(vttr.extractSnippets(vttFile, label, true));
			}
		}
		
		List<Snippet> snippetsNoLabel = new ArrayList<>();
		for (File vttFile : vttFiles) {
			snippetsNoLabel.addAll(vttr.extractSnippets(vttFile, true));
		}
		return crossValidateClassifier(snippetsYes, snippetsNo, snippetsNoLabel, yesLabels, noLabels, folds, biasForRecall);
	}
	
	public List<CVScore> crossValidateClassifier(List<Snippet> snippetsYes,List<Snippet> snippetsNo, List<Snippet> snippetsNoLabel, Collection<String> yesLabels, Collection<String> noLabels, int folds, boolean biasForRecall) throws IOException {
		// randomize the order of the snippets
		if (biasForRecall) {
			snippetsYes.addAll(snippetsNoLabel);
		} else {
			snippetsNo.addAll(snippetsNoLabel);
		}
		Collections.shuffle(snippetsYes, new Random(1));
		Collections.shuffle(snippetsNo, new Random(2));
		
		// partition snippets into one partition per fold
		List<List<Snippet>> partitionsYes = CVUtils.partitionSnippets(folds, snippetsYes);
		List<List<Snippet>> partitionsNo = CVUtils.partitionSnippets(folds, snippetsNo);

		// Run evaluations, "folds" number of times, alternating which partition is being used for testing.
		List<CVScore> results = new ArrayList<>(folds);
		//PrintWriter pw = new PrintWriter(new File("ten-fold cross validation.txt"));
		//int fold = 0;
		System.out.println("Estimating performance with "+folds+"-fold cross validation:");
		for (int i=0;i<folds;i++) {
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
			int k = 0;
			for (Snippet snip: trainingYes) {
				k += 1;
				if (snip.getLabeledStrings().size()==0)
					continue;
				String text = snip.getText();
				String ls = snip.getLabeledStrings().get(0);
				int start = text.indexOf(ls);
				if (start==1)
					continue;
				snippetsTrain.add(text);
				List<Integer> span = new ArrayList<>();
				span.add(start);
				span.add(start+ls.length());
				segspansTrain.add(span);
				labelsTrain.add(1);
			}
			k = 0;
			for (Snippet snip: trainingNo) {
				k += 1;
				if (snip.getLabeledStrings().size()==0)
					continue;
				String text = snip.getText();
				String ls = snip.getLabeledStrings().get(0);
				int start = text.indexOf(ls);
				if (start==1)
					continue;
				snippetsTrain.add(text);
				List<Integer> span = new ArrayList<>();
				span.add(start);
				span.add(start+ls.length());
				segspansTrain.add(span);
				labelsTrain.add(-1);
			}
			IREDClassifier redc = REDClassifierFactory.createModel();
			System.out.println("##### FOLD " + (i+1) + " #####");
			redc.fit(snippetsTrain, segspansTrain, labelsTrain);
			
			List<String> snippetsTest = new ArrayList<>();
			List<Integer> labelsTest = new ArrayList<>();
			k = 0;
			for (Snippet snip: testingYes) {
				k += 1;
				if (snip.getLabeledStrings().size()==0)
					continue;
				String text = snip.getText();
				snippetsTest.add(text);
				labelsTest.add(1);
			}
			k = 0;
			for (Snippet snip: testingNo) {
				k += 1;
				if (snip.getLabeledStrings().size()==0)
					continue;
				String text = snip.getText();
				snippetsTest.add(text);
				labelsTest.add(-1);
			}
			
			List<Integer> labelsPred = redc.predict(snippetsTest, 1);
			CVScore score = new CVScore();
			for (k=0;k<labelsTest.size();k++) {
				int labelTest = labelsTest.get(k);
				int labelPred = labelsPred.get(k);
				if (labelTest==1 && labelPred==1)
					score.setTp(score.getTp()+1);
				else if (labelTest==1 && labelPred==-1)
					score.setFn(score.getFn()+1);
				else if (labelTest==-1 && labelPred==-1)
					score.setTn(score.getTn()+1);
				else if (labelTest==-1 && labelPred==1)
					score.setFp(score.getFp()+1);
			}
			results.add(score);
		}
		//pw.close();
		System.out.println("Done with "+folds+"-fold cross validation.");
		return results;
	}

}
