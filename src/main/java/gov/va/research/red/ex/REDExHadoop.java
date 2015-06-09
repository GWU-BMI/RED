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

import gov.va.research.red.MatchedElement;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.xml.stream.XMLStreamException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

import bioc.BioCAnnotation;
import bioc.BioCDocument;
import bioc.BioCLocation;
import bioc.BioCPassage;
import bioc.io.BioCDocumentWriter;
import bioc.io.BioCFactory;

/**
 * @author doug
 *
 */
public class REDExHadoop  {

	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
	    GenericOptionsParser optionParser = new GenericOptionsParser(conf, args);
	    String[] remainingArgs = optionParser.getRemainingArgs();
	    if (!(remainingArgs.length != 2 | remainingArgs.length != 4)) {
	      System.err.println("Usage: wordcount <in> <out> [-skip skipPatternFile]");
	      System.exit(2);
	    }
	    Job job = Job.getInstance(conf, "word count");
	    job.setJarByClass(REDExHadoop.class);
	    job.setMapperClass(REDExMapper.class);
	    job.setReducerClass(BioCReducer.class);
	    job.setOutputKeyClass(Text.class);
	    job.setOutputValueClass(Text.class);

	    List<String> otherArgs = new ArrayList<String>();
	    for (int i=0; i < remainingArgs.length; ++i) {
	      if ("-skip".equals(remainingArgs[i])) {
	        job.addCacheFile(new Path(remainingArgs[++i]).toUri());
	        job.getConfiguration().setBoolean("wordcount.skip.patterns", true);
	      } else {
	        otherArgs.add(remainingArgs[i]);
	      }
	    }
	    FileInputFormat.addInputPath(job, new Path(otherArgs.get(0)));
	    FileOutputFormat.setOutputPath(job, new Path(otherArgs.get(1)));

	    System.exit(job.waitForCompletion(true) ? 0 : 1);
	}

//	private class REDExMapper extends
//			Mapper<String, Text, Object, List<MatchedElement>> {
//
//		private Configuration conf;
//		private REDExtractor rex;
//
//		@Override
//		protected void setup(
//				Mapper<String, Text, Object, List<MatchedElement>>.Context context)
//				throws IOException, InterruptedException {
//			super.setup(context);
//			conf = context.getConfiguration();
//			String regexFilename = conf.get("regex.file");
//			Path regexPath = conf.getLocalPath(null, regexFilename);
//			rex = REDExtractor.load(Paths.get(regexPath.toUri()));
//		}
//
//		@Override
//		protected void map(
//				String key,
//				Text value,
//				Mapper<String, Text, Object, List<MatchedElement>>.Context context)
//				throws IOException, InterruptedException {
//			List<MatchedElement> matches = rex.extract(value.toString());
//			context.write(key, matches);
//		}
//
//	}

//	private class BioCReducer extends Reducer<Text, List<MatchedElement>, Text, Text> {
//
//		private BioCFactory biocFactory;
//		private DateFormat dateFormat;
//		private DateFormat dateTimeFormat;
//		private String type;
//
//		
//		@Override
//		protected void setup(
//				Reducer<Text, List<MatchedElement>, Text, Text>.Context context)
//				throws IOException, InterruptedException {
//			biocFactory = BioCFactory.newFactory(BioCFactory.STANDARD);
//			TimeZone timezone = TimeZone.getTimeZone("UTC");
//			dateFormat = new SimpleDateFormat("yyyy-MM-dd");
//			dateFormat.setTimeZone(timezone);
//			dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
//			dateTimeFormat.setTimeZone(timezone);
//			type = context.getConfiguration().get("value.type");
//		}
//
//		@Override
//		protected void reduce(
//				Text key,
//				Iterable<List<MatchedElement>> values,
//				Reducer<Text, List<MatchedElement>, Text, Text>.Context context)
//				throws IOException, InterruptedException {
//			String[] ids = key.toString().split("|");
//			if (ids.length != 3) {
//				throw new IOException(
//						"Invalid key format. Expected <patient id>|<document id>|<document date/time> but found: "
//								+ key);
//			}
//			String patientId = ids[0];
//			String documentId = ids[1];
//			String documentDateTime = ids[2];
//
//			BioCDocument biocDoc = new BioCDocument();
//			biocDoc.setID(documentId);
//			biocDoc.putInfon("Patient ID", patientId);
//			biocDoc.putInfon("date", documentDateTime);
//
//			BioCPassage biocPassage = new BioCPassage();
//			biocDoc.addPassage(biocPassage);
//			Date now = new Date();
//			for (List<MatchedElement> matches : values) {
//				for (MatchedElement match : matches) {
//					BioCAnnotation biocAnnotation = new BioCAnnotation();
//					BioCLocation biocLocation = new BioCLocation(
//							match.getStartPos(), match.getEndPos()
//									- match.getStartPos());
//					biocAnnotation.addLocation(biocLocation);
//					biocAnnotation.putInfon("dateTime",
//							dateTimeFormat.format(now));
//					biocAnnotation.putInfon("type", type);
//					biocAnnotation.putInfon("value", match.getMatch());
//					biocAnnotation.putInfon("confidence",
//							"" + match.getConfidence());
//					biocPassage.addAnnotation(biocAnnotation);
//				}
//			}
//			StringWriter sw = new StringWriter();
//			try (BioCDocumentWriter writer = biocFactory.createBioCDocumentWriter(sw)) {
//				writer.writeDocument(biocDoc);
//			} catch (XMLStreamException e) {
//				throw new IOException(e);
//			}
//			context.write(key, new Text(sw.toString()));
//		}
//
//	}
}
