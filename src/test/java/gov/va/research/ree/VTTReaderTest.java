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
		/*StringBuilder regExBld = new StringBuilder("");
		for(String regExp : regExpList){
			//System.out.println(regExp);
			regExBld.append(regExp);
		}
		String regExpFull = regExBld.toString();*/
		List<LSTriplet> ls3list = null;
		try {
			ls3list = vttr.extractLSTriplets(new File(TEST_VTT_FILENAME), "weight");
		} catch (IOException e) {
			throw new AssertionError("Failed extract 'weight' labeled segment triplets from VTT file: " + TEST_VTT_FILENAME, e);
		}
		/*List<LSTriplet> ls3list = new ArrayList<LSTriplet>();
		ls3list.add(new LSTriplet("brought her the attention of the music industry, winning her the music", "selling artist and was titled 2012", "now debuted three additional studio recorded albums, a best of the albums"));
		ls3list.add(new LSTriplet("American singer-songwriter, record producer, actor and choreographer and music. His music is", "selling artist and was titled 2012", "He has sold 10 million albums and 58 million singles worldwide as. His best album is"));*/
		StringBuilder dataToTestBld = new StringBuilder("");
		for(LSTriplet triplet : ls3list){
			dataToTestBld.append(triplet.toString()+" ");
		}
		/*System.out.println("String original");
		System.out.println(dataToTestBld.toString());
		System.out.println();*/
		/*System.out.println("Regular Expression");
		System.out.println(regExpFull);
		System.out.println();*/
		//System.out.println("Matches result==>"+"brought her the attention of the music industry, winning her the music".matches(".{1,7}\\s\\bher\\b\\s\\bthe\\b\\s.{1,9}\\s.{1,2}\\s\\bthe\\b\\s\\bmusic\\b\\s.{1,8}\\p{Punct}\\s.{1,7}\\s\\bher\\b\\s\\bthe\\b\\s\\bmusic\\b"));
		Pattern pattern = null;
		Matcher matcher = null;
		boolean test = false;
		for(LSTriplet triplet : regExpList){
			//System.out.println(triplet.getBLS()+"\n"+triplet.getLS()+"\n"+triplet.getALS()+"\nnew");
			 /*pattern = Pattern.compile(triplet.getBLS());
			 matcher = pattern.matcher(dataToTestBld.toString());
			 test = matcher.find();
			 if(!test){
				 System.out.println("false");
				 break;
			 }else
				 System.out.println("true");
			 
			 pattern = Pattern.compile(triplet.getLS());
			 matcher = pattern.matcher(dataToTestBld.toString());
			 test = matcher.find();
			 if(!test){
				 System.out.println("false");
				break;
			 }else
				 System.out.println("true");
			 
			 pattern = Pattern.compile(triplet.getALS());
			 matcher = pattern.matcher(dataToTestBld.toString());
			 test = matcher.find();
			 if(!test){
				 System.out.println("false");
				break;
			 }else
				 System.out.println("true");*/
			pattern = Pattern.compile(triplet.toStringRegEx());
			 matcher = pattern.matcher(dataToTestBld.toString());
			 test = matcher.find();
			 if(!test){
				 System.out.println("false");
				break;
			 }else
				 System.out.println("true");
		}
		System.out.println("new test");
		for(LSTriplet triplet : ls3list){
			String toTest = triplet.toString();
			test = false;
			for(LSTriplet tripletRegEx : regExpList){
				pattern = Pattern.compile(tripletRegEx.toStringRegEx());
				 matcher = pattern.matcher(toTest);
				 test = matcher.find();
				 if(test){
					 System.out.println("true");
					 break;
				 }
			}
			if(!test){
				System.out.println("false");
				System.out.println(toTest);
				//break;
			}
		}
		/*if(test)
			System.out.println("true");*/
	}

}
