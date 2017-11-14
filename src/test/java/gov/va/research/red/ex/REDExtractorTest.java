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
package gov.va.research.red.ex;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import gov.va.research.red.CVScore;
import gov.va.research.red.ConfidenceMeasurer;
import gov.va.research.red.LabeledSegment;
import gov.va.research.red.RegEx;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;
import gov.va.research.red.VTTSnippetParser;
import gov.va.research.red.regex.PatternAdapter;
import gov.va.research.red.regex.JSEPatternAdapter;
import junit.framework.Assert;


/**
 * @author vhaislreddd
 *
 */
public class REDExtractorTest {

	private static Class<? extends PatternAdapter> PATTERN_ADAPTER_CLASS = JSEPatternAdapter.class;
	private static final String TEST_VTT_FILENAME = "weight1000.vtt";
	private static final URI TEST_VTT_URI;
	private static final String YES = "yes";
	private static final String NO = "no";
	static {
		try {
			TEST_VTT_URI = REDExtractorTest.class.getResource("/" + TEST_VTT_FILENAME).toURI();
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

	/**
	 * Test method for {@link gov.va.research.red.ex.REDExFactory#discoverRegularExpressions(java.util.List, java.lang.String, java.lang.String)}.
	 */
	@Test
	public void testExtractRegexExpressions() {
		VTTReader vttr = new VTTReader();
		REDExFactory regExt = new REDExFactory();
		try {
			Collection<Snippet> snippets = vttr.readSnippets(new File(TEST_VTT_URI), "weight", new VTTSnippetParser());
			regExt.train(snippets, true, "test", true, true, new ArrayList<>(0), Boolean.TRUE, Boolean.TRUE, PATTERN_ADAPTER_CLASS);
		} catch (IOException e) {
			throw new AssertionError("Failed extract 'weight' labeled regular expressions from VTT file: " + TEST_VTT_URI, e);
		}
	}

	/**
	 * Test method for {@link gov.va.research.red.ex.REDExFactory#replaceDigitsLS(java.util.List)}.
	 */
	@Test
	public void testReplaceDigitsLS() {
		List<LabeledSegment> lsList = new ArrayList<>(2);
		lsList.add(new LabeledSegment("four", "four", 20, 4));
		lsList.add(new LabeledSegment("four", "4", 25, 1));
		String snipText = "one 1 two 2 three 3 four 4 five 5 six 6 seven 7";
		Snippet s = new Snippet(snipText, lsList);
		SnippetRegEx sre = new SnippetRegEx(s, true);
		Assert.assertTrue(sre.replaceDigits());
		Assert.assertTrue(sre.getPattern(PATTERN_ADAPTER_CLASS).matcher(snipText).find());
		Assert.assertTrue(sre.toString().split("\\d").length == 1);
	}

	/**
	 * Test method for {@link gov.va.research.red.ex.REDExFactory#replaceWhiteSpace(java.util.List)}.
	 */
	@Test
	public void testReplaceWhiteSpace() {
		List<LabeledSegment> lsList = new ArrayList<>(2);
		lsList.add(new LabeledSegment("four", "four", 20, 4));
		lsList.add(new LabeledSegment("four", "4", 25, 1));
		String snipText = "one 1 two 2 three 3 four 4 five 5 six 6 seven 7";
		Snippet s = new Snippet(snipText, lsList);
		SnippetRegEx sre = new SnippetRegEx(s, true);
		Assert.assertTrue(sre.replaceWhiteSpace());
		Assert.assertTrue(sre.getPattern(PATTERN_ADAPTER_CLASS).matcher(snipText).find());
		Assert.assertTrue(sre.toString().split("\\s").length == 1);
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
		File vttFile = new File(TEST_VTT_URI);
		snippets.addAll(vttr.readSnippets(vttFile, "weight", new VTTSnippetParser()));
		REDExFactory regExt = new REDExFactory();
		REDExModel ex = regExt.train(snippets, true, "test", true, true, new ArrayList<>(0), Boolean.TRUE, Boolean.TRUE, PATTERN_ADAPTER_CLASS);
		List<Collection<? extends WeightedRegEx>> snippetRegExs = ex.getRegexTiers();
		List<RegEx> yesRegExs = null;
		if(snippetRegExs != null) {
			yesRegExs = new ArrayList<RegEx>();
			for (Collection<? extends WeightedRegEx> sres : ex.getRegexTiers()) {
				for(WeightedRegEx snippetRegEx : sres) {
					yesRegExs.add(new RegEx(snippetRegEx.toString()));
				}
			}
		}
		List<RegEx> noRegExs = null;
		try {
			/*List<ConfidenceSnippet> confidenceSnippets =*/ measurer.measureConfidence(snippets, yesRegExs, noRegExs);
//			for(ConfidenceSnippet confSnippet : confidenceSnippets) {
//				Confidence confidence = confSnippet.getConfidence();
//				System.out.println("Snippet confidence score "+confidence.getConfidence() + " confidence type "+confidence.getConfidenceType());
//			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// test dump and load
		Path tempFile = Files.createTempFile(null, null);
		REDExModel.dump(ex, tempFile);
		Assert.assertTrue(Files.exists(tempFile));
		REDExModel ex2 = REDExModel.load(tempFile);
		Assert.assertEquals(ex.getRegexTiers().size(), ex2.getRegexTiers().size());
		for (int i = 0; i < ex.getRegexTiers().size(); i++) {
			Assert.assertEquals(ex.getRegexTiers().get(i).size(), ex2.getRegexTiers().get(i).size());
		}
	}
	
	@Test
	public void testScore() {
		List<LabeledSegment> posLabeledSegments = new ArrayList<>(2);
		posLabeledSegments.add(new LabeledSegment("prescriptions", "4", 61, 1));
		posLabeledSegments.add(new LabeledSegment("prescriptions", "five", 83, 4));
		String snipText = "he went to the clinic two times in the last 3 months and got 4 prescriptions, then five more.";
		Collection<Snippet> snippets = new ArrayList<>(1);
		snippets.add(new Snippet(snipText, posLabeledSegments));
		REDExFactory ref = new REDExFactory();
		
		try {
			REDExModel re = ref.train(snippets, true, "test", true, true, new ArrayList<String>(0), true, true, PATTERN_ADAPTER_CLASS);
			CVScore cv = ref.testREDExOnSnippet(re, true, null, snippets.iterator().next(), true, false, PATTERN_ADAPTER_CLASS);
			Assert.assertNotNull(cv);
			Assert.assertEquals(2, cv.getTp());
			Assert.assertEquals(0, cv.getFp());
			Assert.assertEquals(0,  cv.getFn());
			Assert.assertEquals(0, cv.getTn());
		} catch (IOException e) {
			throw new AssertionError(e.getMessage());
		}
	}

}
