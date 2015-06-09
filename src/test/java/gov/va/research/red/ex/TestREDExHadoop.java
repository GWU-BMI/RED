/*
 *  Copyright 2015 United States Department of Veterans Affairs,
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

import static org.junit.Assert.fail;
import gov.va.research.red.MatchedElement;
import gov.va.research.red.MatchedElementWritable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mrunit.mapreduce.MapDriver;
import org.apache.hadoop.mrunit.mapreduce.MapReduceDriver;
import org.apache.hadoop.mrunit.mapreduce.ReduceDriver;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author doug
 *
 */
public class TestREDExHadoop {

	private MapDriver<Text, Text, Text, MatchedElementWritable> mapDriver;
	private ReduceDriver<Text, MatchedElementWritable, Text, Text> reduceDriver;
	private MapReduceDriver<Text, Text, Text, MatchedElementWritable, Text, Text> mapReduceDriver;

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
		REDExMapper mapper = new REDExMapper();
		mapDriver = new MapDriver<Text, Text, Text, MatchedElementWritable>(
				mapper);
		mapDriver.getConfiguration().set("regex.file", "redex-pain.model");
		BioCReducer reducer = new BioCReducer();
		reduceDriver = ReduceDriver.newReduceDriver(reducer);
		mapReduceDriver = MapReduceDriver.newMapReduceDriver(mapper, reducer);
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testMapper() {
		MatchedElement me = new MatchedElement(2, 5, "5/10", "", 1);
		MatchedElementWritable mew = new MatchedElementWritable(me);
		mapDriver.withInput(new Text("p0|d0|2015-06-08"), new Text(
				"w 5/10"));
		mapDriver.withOutput(new Text("p0|d0|2015-06-08"), mew);
		try {
			mapDriver.runTest();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	public void testReducer() {
		List<MatchedElementWritable> mewList = new ArrayList<>();
		MatchedElementWritable mew = new MatchedElementWritable(new MatchedElement(9, 10, "5", "", 1));
		mewList.add(mew);
		Text output = new Text();
		reduceDriver.withInput(new Text("p0|d0|2015-06-08"), mewList);
		reduceDriver.withOutput(new Text("p0|d0|2015-06-08"), output);
		try {
			reduceDriver.runTest();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	public void testMapReduce() {
		mapReduceDriver.withInput(new Text("p0|d0|2015-06-08"), new Text(
				"Achieved 5 METs\nPain score 5/10\nKatz score : 5"));
		Text output = new Text();
		mapReduceDriver.withOutput(new Text("p0|d0|2015-06-08"), output);
		try {
			mapReduceDriver.runTest();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
}
