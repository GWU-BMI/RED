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
import java.util.Map;

import gov.va.research.red.CVScore;
import gov.va.research.red.CVUtils;
import gov.va.research.red.RegEx;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;

/**
 * @author doug
 *
 */
public class CrossValidateCategorizer {

	public List<CVScore> crossValidateClassifier(List<File> vttFiles, List<String> yesLabels, List<String> noLabels, int folds)
			throws IOException {
		VTTReader vttr = new VTTReader();
		// get snippets
		List<Snippet> snippetsYes = new ArrayList<>();
		for (File vttFile : vttFiles) {
			for (String label : yesLabels) {
				snippetsYes.addAll(vttr.extractSnippets(vttFile, label));
			}
		}
		
		List<Snippet> snippetsNo = new ArrayList<>();
		for (File vttFile : vttFiles) {
			for (String label : noLabels) {
				snippetsNo.addAll(vttr.extractSnippets(vttFile, label));
			}
		}
		
		List<Snippet> snippetsNoLabel = new ArrayList<>();
		for (File vttFile : vttFiles) {
			snippetsNoLabel.addAll(vttr.extractSnippets(vttFile));
		}
		return crossValidateClassifier(snippetsYes, snippetsNo, snippetsNoLabel, yesLabels, noLabels, folds);
	}
	
	public List<CVScore> crossValidateClassifier(List<Snippet> snippetsYes,List<Snippet> snippetsNo, List<Snippet> snippetsNoLabel, Collection<String> yesLabels, Collection<String> noLabels, int folds) throws IOException {
		// randomize the order of the snippets
		Collections.shuffle(snippetsYes);
		Collections.shuffle(snippetsNo);
		Collections.shuffle(snippetsNoLabel);
		
		// partition snippets into one partition per fold
		List<List<Snippet>> partitionsYes = CVUtils.partitionSnippets(folds, snippetsYes);
		List<List<Snippet>> partitionsNo = CVUtils.partitionSnippets(folds, snippetsNo);
		List<List<Snippet>> partitionsNoLabel = CVUtils.partitionSnippets(folds, snippetsNoLabel);

		// Run evaluations, "folds" number of times, alternating which partition is being used for testing.
		List<CVScore> results = new ArrayList<>(folds);
		PrintWriter pw = new PrintWriter(new File("training and testing.txt"));
		int fold = 0;
		for (int i=0;i<folds;i++) {
			List<Snippet> testingYes = partitionsYes.get(i);
			List<Snippet> testingNo = partitionsNo.get(i);
			List<Snippet> testingNoLabel = partitionsNoLabel.get(i);
			pw.println("##### FOLD " + (++fold) + " #####");
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
			
			List<Snippet> trainingNoLabel = new ArrayList<>();
			for (List<Snippet> p : partitionsNoLabel) {
				if (p != testingNoLabel) {
					trainingNoLabel.addAll(p);
				}
			}

			// Train
			RegExCategorizer regExCategorizer = new RegExCategorizer();
			Map<String, Collection<RegEx>> regExsPosNeg = regExCategorizer.extractRegexClassifications(trainingYes, trainingNo, trainingNoLabel, yesLabels, noLabels);

			// Test
			if (regExsPosNeg != null) {
				List<Snippet> testingAll = new ArrayList<Snippet>();
				testingAll.addAll(testingYes);
				testingAll.addAll(testingNo);
				testingAll.addAll(testingNoLabel);
				CVScore score = regExCategorizer.testClassifier(testingAll, regExsPosNeg.get(Boolean.TRUE.toString()), regExsPosNeg.get(Boolean.FALSE.toString()), null, yesLabels);
				results.add(score);
			}
		}
		pw.close();
		return results;
	}

}
