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

import gov.va.research.red.CVScore;
import gov.va.research.red.CVUtils;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;
import gov.va.research.red.VTTSnippetParser;
import gov.va.vinci.nlp.framework.utils.U;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author doug
 *
 */
public class RedCatModelCreator {
	
	private static Logger LOG = LoggerFactory.getLogger("CrossValidateCategorizer");

	public void  createModel(List<File>   vttFiles, 
			                 String       pOutputDir,
			                 List<String> yesLabels, 
			                 List<String> noLabels, 
			                 boolean      biasForRecall) throws Exception {
		
		VTTReader vttr = new VTTReader();
		// get snippets
		List<Snippet> snippetsYes = new ArrayList<>();
		for (File vttFile : vttFiles) {
			for (String label : yesLabels) {
				snippetsYes.addAll(vttr.readSnippets(vttFile, label, true, new VTTSnippetParser()));
			}
		}

		List<Snippet> snippetsNo = new ArrayList<>();
		for (File vttFile : vttFiles) {
			for (String label : noLabels) {
				snippetsNo.addAll(vttr.readSnippets(vttFile, label, false, new VTTSnippetParser()));
			}
		}

		List<Snippet> snippetsUnlabeled = new ArrayList<>();
		for (File vttFile : vttFiles) {
			snippetsUnlabeled.addAll(vttr.readSnippets(vttFile, new VTTSnippetParser()));
		}
		createModel(pOutputDir, snippetsYes, snippetsNo,snippetsUnlabeled, yesLabels, noLabels, biasForRecall);
	}

	public void createModel(String pOutputDir, 
			                List<Snippet> snippetsYes,
				            List<Snippet> snippetsNo, 
				            List<Snippet> snippetsUnlabeled,
				            Collection<String> yesLabels, 
				            Collection<String> noLabels,
				            boolean biasForRecall) throws Exception {
		
		
		// randomize the order of the snippets
		if (biasForRecall) {
			snippetsYes.addAll(snippetsUnlabeled);
		} else {
			snippetsNo.addAll(snippetsUnlabeled);
		}
		Collections.shuffle(snippetsYes);
		Collections.shuffle(snippetsNo);


		// set up training and testing sets for this fold
		List<Snippet> trainingYes = snippetsYes;
		List<Snippet> trainingNo  = snippetsNo;
		
		
		List<String> snippetsTrain = new ArrayList<>();
		List<List<Integer>> segspansTrain = new ArrayList<>();
		List<Integer> labelsTrain = new ArrayList<>();
		int numYes = 0;
		for (Snippet snip : trainingYes) {
			if (snip.getPosLabeledSegments().size() == 0)
				continue;
			String text = snip.getText();
			String ls = snip.getPosLabeledStrings().get(0);
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
			if (snip.getNegLabeledSegments().size() == 0)
				continue;
			String text = snip.getText();
			String ls = snip.getNegLabeledStrings().get(0);
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
			
			redc.fit(snippetsTrain, segspansTrain, labelsTrain);
			int positive = 1;
			int negative = -1;
		
			printRegexFile( pOutputDir, redc.getStrictRegexs(positive), "strictRegexes", "Positive");
			printRegexFile( pOutputDir, redc.getStrictRegexs(negative),  "strictRegexes", "Negative");
			
			printRegexFile( pOutputDir, redc.getLessStrictRegexs(positive), "lessStrictRegexes", "Positive");
			printRegexFile( pOutputDir, redc.getLessStrictRegexs(negative),  "lessStrictRegexes", "Negative");
			
			printRegexFile( pOutputDir, redc.getLeastStrictRegexs(positive), "leastStrictRegexes", "Positive");
			printRegexFile( pOutputDir, redc.getLeastStrictRegexs(negative),  "leastStrictRegexes", "Negative");
			
		
		
	} // end Method createModel() -------------------------------------
	
	/**
	 * printRegexFile 
	 * 
	 * @param pOutputDir
	 * @param pRegexes    
	 * @param pRegexType   (Strict/LessStrict/LeastStrict )
	 * @param pLabelType   (Positive/Negative, Yes/No,  True/False ... )
	 * @throws Exception 
	 */
	public static void printRegexFile(String pOutputDir, List<String> pListOfRegexes, String pRegexType, String pLabelType) throws Exception {

		String outputFileName = pOutputDir + "/" + pRegexType + "_" + pLabelType + ".regex";
		try {
			File aDir = new File(pOutputDir);
			if (!aDir.exists()) {
				try {
					U.mkDir(pOutputDir);

				} catch (Exception e) {
					e.printStackTrace();
					System.err.println("Issue making the output dir " + e.toString());
					throw e;
				}
			}

			PrintWriter out = new PrintWriter(outputFileName);
			for (String regex : pListOfRegexes) {
				out.print(regex);
				out.print('\n');
			}
			out.close();

		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Issue with printing out the regex file " + outputFileName + " " + e.toString());
			throw e;
		}
	} // end Method printRegexFile() --------------------------------

	
}
