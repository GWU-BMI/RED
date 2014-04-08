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
package gov.va.research.ree;

import gov.nih.nlm.nls.vtt.Model.VttDocument;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author vhaislreddd
 *
 */
public class VTTReaderTest {

	private static final String TEST_VTT_FILENAME = "weight1000.vtt";

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	/**
	 * Test method for {@link gov.va.research.ree.VTTReader#read(java.io.File)}.
	 */
	@Test
	public void testRead() {
		VTTReader vttr = new VTTReader();
		try {
			vttr.read(new File(TEST_VTT_FILENAME));
		} catch (IOException e) {
			throw new AssertionError("Failed to read VTT file: " + TEST_VTT_FILENAME, e);
		}
	}

	/**
	 * Test method for {@link gov.va.research.ree.VTTReader#extractLSTriplets(java.io.File, java.lang.String)}.
	 */
	@Test
	public void testExtractLSTriplets() {
		VTTReader vttr = new VTTReader();
		List<LSTriplet> ls3List = null;
		try {
			ls3List = vttr.extractLSTriplets(new File(TEST_VTT_FILENAME), "weight");
		} catch (IOException e) {
			throw new AssertionError("Failed extract 'weight' labeled segment triplets from VTT file: " + TEST_VTT_FILENAME, e);
		}
		Assert.assertNotNull(ls3List);
		Assert.assertTrue("List of 'weight' labeled segment triplets was empty", ls3List.size() > 0);
	}
	
	/**
	 * Test method for {@link gov.va.research.ree.VTTReader#extractRegexExpressions(java.io.File, java.lang.String)}.
	 */
	@Test
	public void testExtractRegexExpressions(){
		VTTReader vttr = new VTTReader();
		List<LSTriplet> regExpList = null;
		try {
			regExpList = vttr.extractRegexExpressions(new File(TEST_VTT_FILENAME), "weight");
		} catch (IOException e) {
			throw new AssertionError("Failed extract 'weight' labeled regular expressions from VTT file: " + TEST_VTT_FILENAME, e);
		}
		/*List<LSTriplet> ls3list = null;
		try {
			ls3list = vttr.extractLSTriplets(new File(TEST_VTT_FILENAME), "weight");
		} catch (IOException e) {
			throw new AssertionError("Failed extract 'weight' labeled segment triplets from VTT file: " + TEST_VTT_FILENAME, e);
		}
		StringBuilder dataToTestBld = new StringBuilder("");
		for(LSTriplet triplet : ls3list){
			dataToTestBld.append(triplet.toString()+" ");
		}
		Pattern pattern = null;
		Matcher matcher = null;
		boolean test = false;
		System.out.println("Testing regular expressions against the document...");
		for(LSTriplet triplet : regExpList){
			pattern = Pattern.compile(triplet.toStringRegEx());
			 matcher = pattern.matcher(dataToTestBld.toString());
			 test = matcher.find();
			 Assert.assertTrue(test);
			 if(!test){
				 System.out.println("false");
				break;
			 }else
				 System.out.println("true");
		}
		System.out.println("Testing the document against the regular expressions...");
		for(LSTriplet triplet : ls3list){
			String toTest = triplet.toString();
			test = false;
			for(LSTriplet tripletRegEx : regExpList){
				pattern = Pattern.compile(tripletRegEx.toStringRegEx());
				 matcher = pattern.matcher(toTest);
				 test = matcher.find();
				 if(test){
					 break;
				 }
			}
			if(!test){
				System.out.println(toTest);
			}
			Assert.assertTrue(test);
		}*/
	}

	/**
	 * Test method for {@link gov.va.research.ree.VTTReader#extractSnippets(java.io.File, java.lang.String)}.
	 */
	@Test
	public void testExtractSnippets() {
		VTTReader vttr = new VTTReader();
		URL testFileURL = this.getClass().getResource("/test-snippets.vtt");
		File vttFile = null;
		try {
			vttFile = new File(testFileURL.toURI());
		} catch (URISyntaxException e1) {
			throw new AssertionError("Failed to open test vtt file");
		}
		List<Snippet> snippets = null;
		try {
			snippets = vttr.extractSnippets(vttFile, "weight");
		} catch (IOException e) {
			throw new AssertionError("Failed extract 'weight' labeled segment triplets from VTT file: " + vttFile, e);
		}
		Assert.assertNotNull(snippets);
		Assert.assertEquals(5, snippets.size());

		{
			Snippet firstSnip = snippets.get(0);
			Assert.assertEquals(1, firstSnip.getLabeledSegments().size());
			LabeledSegment firstSnipFirstLS = firstSnip.getLabeledSegments().iterator().next();
			Assert.assertEquals("184", firstSnipFirstLS.getLabeledString());
		}
		{
			Snippet secondSnip = snippets.get(1);
			Assert.assertEquals(1, secondSnip.getLabeledSegments().size());
			LabeledSegment secondSnipFirstLS = secondSnip.getLabeledSegments().iterator().next();
			Assert.assertEquals("184", secondSnipFirstLS.getLabeledString());
		}
		{
			Snippet thirdSnip = snippets.get(2);
			Assert.assertEquals(1, thirdSnip.getLabeledSegments().size());
			LabeledSegment thirdSnipFirstLS = thirdSnip.getLabeledSegments().iterator().next();
			Assert.assertEquals("184.5", thirdSnipFirstLS.getLabeledString());
		}
		{
			Snippet fourthSnip = snippets.get(3);
			Assert.assertEquals(2, fourthSnip.getLabeledSegments().size());
			List<String> labeledStrings = new ArrayList<>(2);
			for (LabeledSegment ls : fourthSnip.getLabeledSegments()) {
				labeledStrings.add(ls.getLabeledString());
			}
			Assert.assertTrue(labeledStrings.contains("184"));
			Assert.assertTrue(labeledStrings.contains("83.4"));
		}
		{
			Snippet fifthSnip = snippets.get(4);
			Assert.assertEquals(2, fifthSnip.getLabeledSegments().size());
			List<String> labeledStrings = new ArrayList<>(2);
			for (LabeledSegment ls : fifthSnip.getLabeledSegments()) {
				labeledStrings.add(ls.getLabeledString());
			}
			Assert.assertTrue(labeledStrings.contains("184"));
			Assert.assertTrue(labeledStrings.contains("83.4"));
		}
	}
}
