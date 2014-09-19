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

import gov.va.research.red.ConfidenceMeasurer;
import gov.va.research.red.RegEx;
import gov.va.research.red.Confidence;
import gov.va.research.red.ConfidenceSnippet;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;
import gov.va.research.red.VTTReaderTest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author vhaislreddd
 *
 */
public class RegExCategorizerTest {
	
	private static final String CLASSIFIER_TEST_FILE = "diabetes-snippets.vtt";
	private static final URI CLASSIFIER_TEST_URI;
	private static final String YES = "yes";
	private static final String NO = "no";
	static {
		try {
			CLASSIFIER_TEST_URI = VTTReaderTest.class.getResource("/" + CLASSIFIER_TEST_FILE).toURI();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testExtractClassifier(){
		RegExCategorizer crex = new RegExCategorizer();
		try {
			List<String> yesLabels = new ArrayList<>();
			yesLabels.add("yes");
			List<String> noLabels = new ArrayList<>();
			noLabels.add("no");
			crex.findRegexesAndSaveInFile(new File(CLASSIFIER_TEST_URI), yesLabels, noLabels, "classifier2.txt", true);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void testConfidenceMeasurer() throws IOException{
		ConfidenceMeasurer measurer = new ConfidenceMeasurer();
		List<String> yesLabels = new ArrayList<>();
		yesLabels.add(YES);
		List<String> noLabels = new ArrayList<>();
		noLabels.add(NO);
		List<Snippet> snippets = new ArrayList<Snippet>();
		VTTReader vttr = new VTTReader();
		File vttFile = new File(CLASSIFIER_TEST_URI);
		snippets.addAll(vttr.extractSnippets(vttFile, ""));
		RegExCategorizer regExCategorizer = new RegExCategorizer();
		Map<String, Collection<RegEx>> regExMap = regExCategorizer .findRegexesAndSaveInFile(vttFile, yesLabels, noLabels, null, false);
		List<RegEx> yesRegExs = new ArrayList<RegEx>(regExMap.get(YES));
		List<RegEx> noRegExs = new ArrayList<RegEx>(regExMap.get(NO));
		try {
			List<ConfidenceSnippet> confidenceSnippets = measurer.measureConfidence(snippets, yesRegExs, noRegExs);
			for(ConfidenceSnippet catSnippet : confidenceSnippets) {
				Confidence confidence = catSnippet.getConfidence();
				System.out.println("Snippet confidence score "+confidence.getConfidence() + " confidence type "+confidence.getConfidenceType());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Test method for {@link gov.va.research.red.cat.CategorizerLoader.ClassifierLoader#getYesRegEx()}.
	 */
	@Test
	public void testgetYesRegEx() {
		CategorizerLoader loader = new CategorizerLoader("classifier2.txt");
		try {
			List<RegEx> regYesList = loader.getYesRegEx();
			System.out.println("Yes List");
			for(RegEx regEx : regYesList){
				System.out.println(regEx.getRegEx());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Test method for {@link gov.va.research.red.cat.CategorizerLoader.ClassifierLoader#getNoRegEx()}.
	 */
	@Test
	public void testgetNoRegEx() {
		CategorizerLoader loader = new CategorizerLoader("classifier2.txt");
		try {
			List<RegEx> regNoList = loader.getNoRegEx();
			System.out.println("No List");
			for(RegEx regEx : regNoList){
				System.out.println(regEx.getRegEx());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
