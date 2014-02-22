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

}
