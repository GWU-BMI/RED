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
package gov.va.research.red;

import gov.va.research.red.LSTriplet;
import gov.va.research.red.LabeledSegment;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
	private static final URI TEST_VTT_URI;
	static {
		try {
			TEST_VTT_URI = VTTReaderTest.class.getResource("/" + TEST_VTT_FILENAME).toURI();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

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
	 * Test method for {@link gov.va.research.red.VTTReader#read(java.io.File)}.
	 */
	@Test
	public void testRead() {
		VTTReader vttr = new VTTReader();
		try {
			vttr.read(new File(TEST_VTT_URI));
		} catch (IOException e) {
			throw new AssertionError("Failed to read VTT file: " + TEST_VTT_URI, e);
		}
	}

	/**
	 * Test method for {@link gov.va.research.red.VTTReader#extractLSTriplets(java.io.File, java.lang.String)}.
	 */
	@Test
	public void testExtractLSTriplets() {
		VTTReader vttr = new VTTReader();
		List<LSTriplet> ls3List = null;
		try {
			ls3List = vttr.extractLSTriplets(new File(TEST_VTT_URI), "weight");
		} catch (IOException e) {
			throw new AssertionError("Failed extract 'weight' labeled segment triplets from VTT file: " + TEST_VTT_URI, e);
		}
		Assert.assertNotNull(ls3List);
		Assert.assertTrue("List of 'weight' labeled segment triplets was empty", ls3List.size() > 0);
	}

	/**
	 * Test method for {@link gov.va.research.red.VTTReader#extractSnippets(java.io.File, java.lang.String)}.
	 */
	@Test
	public void testExtractSnippets() {
		VTTReader vttr = new VTTReader();
		File vttFile = new File(TEST_VTT_URI);

		List<Snippet> snippets = null;
		try {
			snippets = vttr.extractSnippets(vttFile, "weight");
		} catch (IOException e) {
			throw new AssertionError("Failed extract 'weight' labeled segment triplets from VTT file: " + vttFile, e);
		}
		Assert.assertNotNull(snippets);
		Assert.assertEquals(1000, snippets.size());

		// first 25 snippets should have no labeled segements
		Iterator<Snippet> snipIt = snippets.iterator();
		for (int i = 0; i < 24; i++) {
			Snippet snip = snipIt.next();
			Assert.assertEquals(0, snip.getLabeledSegments().size());
		}
		{
			Snippet snip = snipIt.next();
			Assert.assertEquals(1, snip.getLabeledSegments().size());
			LabeledSegment ls = snip.getLabeledSegments().iterator().next();
			Assert.assertEquals("151", ls.getLabeledString());
		}
		{
			Snippet snip = snipIt.next();
			Assert.assertEquals(1, snip.getLabeledSegments().size());
			LabeledSegment ls = snip.getLabeledSegments().iterator().next();
			Assert.assertEquals("160", ls.getLabeledString());
		}
		for (int i = 0; i < 4; i++) {
			Snippet snip = snipIt.next();
			Assert.assertEquals(0, snip.getLabeledSegments().size());
		}
		{
			Snippet snip = snipIt.next();
			Assert.assertEquals(1, snip.getLabeledSegments().size());
			LabeledSegment ls = snip.getLabeledSegments().iterator().next();
			Assert.assertEquals("60.0", ls.getLabeledString());
		}
		// Snippet 60 should have multiple labeled segments
		{
			Snippet snip = snippets.get(60);
			Assert.assertEquals(3, snip.getLabeledSegments().size());
			Iterator<LabeledSegment> lsIt = snip.getLabeledSegments().iterator();
			LabeledSegment ls1 = lsIt.next();
			Assert.assertEquals("91.4", ls1.getLabeledString());
			LabeledSegment ls2 = lsIt.next();
			Assert.assertEquals("201", ls2.getLabeledString());
			LabeledSegment ls3 = lsIt.next();
			Assert.assertEquals("59", ls3.getLabeledString());
		}
		// Last snippet should have no labeled segments
		{
			Snippet snip = null;
			while (snipIt.hasNext()) {
				snip = snipIt.next();
			}
			Assert.assertEquals(0, snip.getLabeledSegments().size());
		}
	}
}
