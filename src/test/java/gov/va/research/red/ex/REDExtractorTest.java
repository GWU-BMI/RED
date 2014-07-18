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

import static org.junit.Assert.*;
import gov.va.research.red.LSTriplet;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;
import gov.va.research.red.VTTReaderTest;
import gov.va.research.red.ex.REDExtractor;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * @author vhaislreddd
 *
 */
public class REDExtractorTest {

	private static final String TEST_VTT_FILENAME = "weight1000.vtt";
	private static final URI TEST_VTT_URI;
	static {
		try {
			TEST_VTT_URI = VTTReaderTest.class.getResource("/" + TEST_VTT_FILENAME).toURI();
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
	 * Test method for {@link gov.va.research.red.ex.REDExtractor#extractRegexExpressions(java.util.List, java.lang.String, java.lang.String)}.
	 */
	@Test
	public void testExtractRegexExpressions() {
		VTTReader vttr = new VTTReader();
		List<LSTriplet> regExpList = null;
		REDExtractor regExt = new REDExtractor();
		try {
			List<Snippet> snippets = vttr.extractSnippets(new File(TEST_VTT_URI), "weight");
			regExpList = regExt.extractRegexExpressions(snippets, "weight", "test-snippets.txt");
		} catch (IOException e) {
			throw new AssertionError("Failed extract 'weight' labeled regular expressions from VTT file: " + TEST_VTT_URI, e);
		}
	}

	/**
	 * Test method for {@link gov.va.research.red.ex.REDExtractor#replacePunct(java.util.List)}.
	 */
	@Test
	public void testReplacePunct() {
		fail("Not yet implemented");
	}

	/**
	 * Test method for {@link gov.va.research.red.ex.REDExtractor#replaceDigitsLS(java.util.List)}.
	 */
	@Test
	public void testReplaceDigitsLS() {
		fail("Not yet implemented");
	}

	/**
	 * Test method for {@link gov.va.research.red.ex.REDExtractor#replaceDigitsBLSALS(java.util.List)}.
	 */
	@Test
	public void testReplaceDigitsBLSALS() {
		fail("Not yet implemented");
	}

	/**
	 * Test method for {@link gov.va.research.red.ex.REDExtractor#replaceWhiteSpaces(java.util.List)}.
	 */
	@Test
	public void testReplaceWhiteSpaces() {
		fail("Not yet implemented");
	}

}
