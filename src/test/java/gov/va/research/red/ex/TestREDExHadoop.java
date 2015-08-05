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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.xml.stream.XMLStreamException;

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

import bioc.BioCAnnotation;
import bioc.BioCDocument;
import bioc.BioCPassage;
import bioc.io.BioCCollectionReader;
import bioc.io.BioCCollectionWriter;
import bioc.io.BioCDocumentReader;
import bioc.io.BioCFactory;

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
		URL modelURL = this.getClass().getClassLoader().getResource("redex-pain.model");
		File modelFile = new File(modelURL.toURI());
		REDExMapper mapper = new REDExMapper();
		mapDriver = new MapDriver<Text, Text, Text, MatchedElementWritable>(
				mapper);
		mapDriver.getConfiguration().set("regex.file", modelFile.getPath());
		BioCReducer reducer = new BioCReducer();
		reduceDriver = ReduceDriver.newReduceDriver(reducer);
		reduceDriver.getConfiguration().set("value.type", "pain");
		mapReduceDriver = MapReduceDriver.newMapReduceDriver(mapper, reducer);
		mapReduceDriver.getConfiguration().set("regex.file", modelFile.getPath());
		mapReduceDriver.getConfiguration().set("value.type", "pain");
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testMapper() {
		MatchedElement me = new MatchedElement(10, 11, "1", "(?i)developed\\s{1,2}?((\\d+|zero|one|two|three|four|five|six|seven|eight|nine|ten))\\p{Punct}{1,2}?(?:\\d+?|zero|one|two|three|four|five|six|seven|eight|nine|ten)", 0.005767844268204758);
		MatchedElementWritable mew = new MatchedElementWritable(me);
		mapDriver.withInput(new Text("p0|d0|2015-06-08"), new Text(
				"developed 1.1"));
		mapDriver.withOutput(new Text("p0|d0|2015-06-08"), mew);
		mapDriver.setValueComparator(new Comparator<MatchedElementWritable>() {
			@Override
			public int compare(MatchedElementWritable o1,
					MatchedElementWritable o2) {
				if (o1.getMatchedElement().getStartPos() != o2.getMatchedElement().getStartPos()) {
					return o1.getMatchedElement().getStartPos() - o2.getMatchedElement().getEndPos();
				}
				if (o1.getMatchedElement().getEndPos() != o2.getMatchedElement().getEndPos()) {
					return o1.getMatchedElement().getEndPos() - o2.getMatchedElement().getEndPos();
				}
				if (!o1.getMatchedElement().getMatch().equals(o2.getMatchedElement().getMatch())) {
					return o1.getMatchedElement().getMatch().compareTo(o2.getMatchedElement().getMatch());
				}
				return 0;
			}
		});
		try {
			mapDriver.runTest();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	public void testReducer() {
		List<MatchedElementWritable> mewList = new ArrayList<>();
		MatchedElementWritable mew = new MatchedElementWritable(new MatchedElement(10, 11, "1", "", 1));
		mewList.add(mew);
		Text output = new Text(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<!DOCTYPE collection SYSTEM \"BioC.dtd\">"
				+ "<collection>"
				+   "<source></source>"
				+   "<date></date>"
				+   "<key></key>"
				+   "<document>"
				+     "<id>d0</id>"
				+     "<infon key=\"date\">2015-06-08</infon>"
				+     "<infon key=\"Patient ID\">p0</infon>"
				+     "<passage>"
				+       "<offset>0</offset>"
				+       "<annotation id=\"\">"
				+         "<infon key=\"dateTime\">2015-06-17T21:44Z</infon>"
				+         "<infon key=\"confidence\">1.0</infon>"
				+         "<infon key=\"type\">pain</infon>"
				+         "<location offset=\"10\" length=\"1\"></location>"
				+         "<text>1</text>"
				+       "</annotation>"
				+     "</passage>"
				+   "</document>"
				+ "</collection>");
		reduceDriver.withInput(new Text("p0|d0|2015-06-08"), mewList);
		reduceDriver.withOutput(output, new Text());
		reduceDriver.setKeyComparator(new BioCXMLComparator());
		try {
			reduceDriver.runTest();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	public void testMapReduce() {
		mapReduceDriver.withInput(new Text("p0|d0|2015-06-08"), new Text(
				"developed 1.1"));
		Text output = new Text(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<!DOCTYPE collection SYSTEM \"BioC.dtd\">"
				+ "<collection>"
				+   "<source></source>"
				+   "<date></date>"
				+   "<key></key>"
				+   "<document>"
				+     "<id>d0</id>"
				+     "<infon key=\"date\">2015-06-08</infon>"
				+     "<infon key=\"Patient ID\">p0</infon>"
				+     "<passage>"
				+       "<offset>0</offset>"
				+       "<annotation id=\"\">"
				+         "<infon key=\"dateTime\">2015-06-17T21:44Z</infon>"
				+         "<infon key=\"confidence\">1.0</infon>"
				+         "<infon key=\"type\">pain</infon>"
				+         "<location offset=\"10\" length=\"1\"></location>"
				+         "<text>1</text>"
				+       "</annotation>"
				+     "</passage>"
				+   "</document>"
				+ "</collection>");
		mapReduceDriver.withOutput(output, new Text());
		mapReduceDriver.setKeyComparator(new BioCXMLComparator());
		try {
			mapReduceDriver.runTest();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
	
	private class BioCXMLComparator implements Comparator<Text> {

		/* (non-Javadoc)
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		@Override
		public int compare(Text o1, Text o2) {
			BioCFactory fact = BioCFactory.newFactory(BioCFactory.STANDARD);
			try {
				BioCDocumentReader o1Reader = fact.createBioCDocumentReader(new StringReader(o1.toString()));
				BioCDocument o1Doc = o1Reader.readDocument();
				BioCDocumentReader o2Reader = fact.createBioCDocumentReader(new StringReader(o2.toString()));
				BioCDocument o2Doc = o2Reader.readDocument();
				BioCAnnotation o1Ann = o1Doc.getPassage(0).getAnnotation(0);
				BioCAnnotation o2Ann = o2Doc.getPassage(0).getAnnotation(0);
				if (!o1Doc.getID().equals(o2Doc.getID())) {
					return o1Doc.getID().compareTo(o2Doc.getID());
				}
				if (!o1Doc.getInfon("Patient ID").equals(o2Doc.getInfon("Patient ID"))) {
					return o1Doc.getInfon("Patient ID").compareTo(o2Doc.getInfon("Patient ID"));
				}
				if (o1Doc.getPassages().size() != o2Doc.getPassages().size()) {
					return o1Doc.getPassages().size() - o2Doc.getPassages().size();
				}
				
				if (!o1Ann.getText().equals(o2Ann.getText())) {
					return o1Ann.getText().compareTo(o2Ann.getText());
				}
				if (!o1Ann.getInfon("type").equals(o2Ann.getInfon("type"))) {
					return o1Ann.getInfon("type").compareTo(o2Ann.getInfon("type"));
				}
				if (o1Ann.getLocations().get(0).getOffset() != o2Ann.getLocations().get(0).getOffset()) {
					return o1Ann.getLocations().get(0).getOffset() - o2Ann.getLocations().get(0).getOffset();
				}
				if (o1Ann.getLocations().get(0).getLength() != o2Ann.getLocations().get(0).getLength()) {
					return o1Ann.getLocations().get(0).getLength() - o2Ann.getLocations().get(0).getLength();
				}				
			} catch (XMLStreamException e) {
				throw new AssertionError(e);
			}
			System.out.println(o1.toString());
			return 0;
		}
		
	}
}
