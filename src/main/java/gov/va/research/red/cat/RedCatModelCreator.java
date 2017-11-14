/*
 *  Copyright 2014,2017 United States Department of Veterans Affairs,
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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;
import gov.va.research.red.VTTSnippetParser;
import gov.va.vinci.nlp.framework.utils.U;

/**
 * @author doug
 *
 */
public class RedCatModelCreator {
	
	private static Logger LOG = LoggerFactory.getLogger("CrossValidateCategorizer");
	
	public static String DELIMITER = "~";

	
	/**
   * createModel 
   * 
   * @param pVTTFiles List of VTT files for use in creating the model.
   * @param pKeywordsFile Keywords required to be in regular expressions.
   * @param pOutputDir Output directory.
   * @param yesLabels Positive labels.
   * @param noLabels Negative labels.
   * @param biasForRecall If true then model will be biased toward recall. If false then bias will be toward precision.
   * @throws Exception When an exception occurs.
   */
	public void  createModel(List<File>   pVTTFiles, 
	                     String       pKeywordsFile,
			                 String       pOutputDir,
			                 List<String> yesLabels, 
			                 List<String> noLabels, 
			                 boolean      biasForRecall) throws Exception {
		
		VTTReader vttr = new VTTReader();
		
		String[] keywords = null;
		if ( pKeywordsFile != null && !pKeywordsFile.isEmpty() ) {
		  keywords = U.readFileIntoStringArray(pKeywordsFile);
		  keywords = normalizeKeywords( keywords );
		}
		
		// get snippets
		List<Snippet> snippetsYes = new ArrayList<>();
		for (File vttFile : pVTTFiles) {
			for (String label : yesLabels) {
				snippetsYes.addAll(vttr.readSnippets(vttFile, label, new VTTSnippetParser()));
			}
		}

		List<Snippet> snippetsNo = new ArrayList<>();
		for (File vttFile : pVTTFiles) {
			for (String label : noLabels) {
				snippetsNo.addAll(vttr.readSnippets(vttFile, label, new VTTSnippetParser()));
			}
		}

		List<Snippet> snippetsUnlabeled = new ArrayList<>();
		for (File vttFile : pVTTFiles) {
			snippetsUnlabeled.addAll(vttr.readSnippets(vttFile, new VTTSnippetParser()));
		}
		createModel(pOutputDir, keywords, snippetsYes, snippetsNo,snippetsUnlabeled, yesLabels, noLabels, biasForRecall);
	} // end Method create Model() =================

	
	// =======================================================
   /**
    * normalizeKeywords lowercases the keywords, and throws out
    *  keywords that have been commented out (with a leading #)
    * 
    * @param pKeywords Keywords to normalize
    * @return String []
    *
    */
   // ======================================================	
  private String[] normalizeKeywords(String[] pKeywords) {
   
    String returnVal[] = null;
    ArrayList<String> keys = null ; 
    
    
    if ( pKeywords != null && pKeywords.length > 0 ) {
      keys = new ArrayList<String>( pKeywords.length);
      for ( String key: pKeywords ){
        if ( key != null && key.length() > 0  && !key.startsWith("#"))
          keys.add( key.toLowerCase().trim());
      } // end loop thru keywords
    }
          
    return returnVal;
    } // End Method normalizeKeywords ============
   


  /**
  * createModel 
  * 
  * @param pOutputDir Output directory for the model.
  * @param keyWords Keywords required to be in regular expressions.
  * @param snipptsYes Positive snippets.
  * @param snippetsNo Negative snippets.
  * @param snippetsUnlabeled Unlabeled snippets.
  * @param yesLabels Positive labels.
  * @param noLabels Negative labels.
  * @param biasForRecall If true then model will be biased toward recall. If false then bias will be toward precision.
  * @throws Exception When an exception occurs.
  */
	public void createModel(String pOutputDir,
	                   String[] keyWords,
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
		
		
		List<String> snippetsTrain = new ArrayList<>();
		List<List<Integer>> segspansTrain = new ArrayList<>();
		List<Integer> labelsTrain = new ArrayList<>();
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
		}
		
		IREDClassifier redc = REDClassifierFactory.createModel();
		
		redc.fit(snippetsTrain, segspansTrain, labelsTrain);
		int positive = 1;
		int negative = -1;
	
		printRegexFileWithKeyWordFilter( pOutputDir, keyWords, redc.getStrictRegexs(positive), "strictRegexes", "Positive");
		printRegexFileWithKeyWordFilter( pOutputDir, keyWords, redc.getStrictRegexs(negative),  "strictRegexes", "Negative");
		
		printRegexFileWithKeyWordFilter( pOutputDir, keyWords, redc.getLessStrictRegexs(positive), "lessStrictRegexes", "Positive");
		printRegexFileWithKeyWordFilter( pOutputDir, keyWords, redc.getLessStrictRegexs(negative),  "lessStrictRegexes", "Negative");
		
		printRegexFileWithKeyWordFilter( pOutputDir, keyWords, redc.getLeastStrictRegexs(positive), "leastStrictRegexes", "Positive");
		printRegexFileWithKeyWordFilter( pOutputDir, keyWords, redc.getLeastStrictRegexs(negative),  "leastStrictRegexes", "Negative");
		
	} // end Method createModel() -------------------------------------
	
	/**
	 * printRegexFileWithKeyWordFilter 
	 * 
	 * @param pOutputDir Output directory for regexes.
	 * @param pKeyWords Keywords to use for filtering regexes.
	 * @param pRegexes Regular expressions.
	 * @param pRegexType   (Strict/LessStrict/LeastStrict )
	 * @param pLabelType   (Positive/Negative, Yes/No,  True/False ... )
	 * @throws Exception When an exception occurs.
	 */
	public static void printRegexFileWithKeyWordFilter(String pOutputDir, String[] pKeyWords, List<String> pListOfRegexes, String pRegexType, String pLabelType) throws Exception {

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
			  
			  String markedRegularExpression = markRegularExpressionsWithKeyword( pKeyWords, regex);
				out.print(markedRegularExpression);
				out.print('\n');
			}
			out.close();

		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Issue with printing out the regex file " + outputFileName + " " + e.toString());
			throw e;
		}
	} // end Method printRegexFile() --------------------------------

  // =======================================================
   /**
    * markRegularExpressionsWithKeyword marks regular expression with a leading # if
    * the regular expression does not contain one of the keywords passed in.
    * 
    * It creates a group around each instance of a keyword found, and returns
    * the number of keywords in the regex pattern in a field 
    * 
    *    So the output is this:
    *       # some regular expression that doesn't have a keyword
    *       1|regEx(keyword)regEx2  
    * 
    * @param pKeyWords Keywords to use for filtering regular expression.
    * @param pRegex Regular expression to filter.
    * @return String
    *
    */
   // ======================================================	
  private static String markRegularExpressionsWithKeyword(String[] pKeyWords, String pRegex) {

    String buff = pRegex;
    String returnVal = buff;
    try {
      int regexFound = 0;
      int p = 0;
      if (pKeyWords == null || pKeyWords.length == 0) {
        ;
      } else {
        for (String keyWord : pKeyWords) {
          if (p <= buff.length()) 
            p = buff.indexOf(keyWord, p + 1);

          if (p > -1) {
            regexFound++;
            buff = buff.substring(0, p) + "(" + keyWord + ")" + buff.substring(p + keyWord.length());
            p = p + keyWord.length() + 2;
          } else break;
        } // end loop thru keyWords
        if (regexFound == 0)
          returnVal = "#" + DELIMITER + pRegex;
        else {
          returnVal = regexFound + DELIMITER + buff;
        }

      } // end if there are any keywords
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("Issue within method markRegularExpressionsWithKeyword " + e.getMessage());
      throw e;
    }

    return returnVal;
  } // End Method markRegularExpressionsWithKeyword ============
    
  } // end Class RedCatModelCreator() -------------------------

	

