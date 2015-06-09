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
import gov.va.research.red.MatchedElementWritable;

import java.io.IOException;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.xml.stream.XMLStreamException;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

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
public class BioCReducer extends Reducer<Text, MatchedElementWritable, Text, Text> {

	private BioCFactory biocFactory;
	private DateFormat dateFormat;
	private DateFormat dateTimeFormat;
	private String type;

	
	@Override
	protected void setup(
			Reducer<Text, MatchedElementWritable, Text, Text>.Context context)
			throws IOException, InterruptedException {
		biocFactory = BioCFactory.newFactory(BioCFactory.STANDARD);
		TimeZone timezone = TimeZone.getTimeZone("UTC");
		dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		dateFormat.setTimeZone(timezone);
		dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
		dateTimeFormat.setTimeZone(timezone);
		type = context.getConfiguration().get("value.type");
	}

	@Override
	protected void reduce(
			Text key,
			Iterable<MatchedElementWritable> values,
			Reducer<Text, MatchedElementWritable, Text, Text>.Context context)
			throws IOException, InterruptedException {
		String[] ids = key.toString().split("|");
		if (ids.length != 3) {
			throw new IOException(
					"Invalid key format. Expected <patient id>|<document id>|<document date/time> but found: "
							+ key);
		}
		String patientId = ids[0];
		String documentId = ids[1];
		String documentDateTime = ids[2];

		BioCDocument biocDoc = new BioCDocument();
		biocDoc.setID(documentId);
		biocDoc.putInfon("Patient ID", patientId);
		biocDoc.putInfon("date", documentDateTime);

		BioCPassage biocPassage = new BioCPassage();
		biocDoc.addPassage(biocPassage);
		Date now = new Date();
		for (MatchedElementWritable matchWritable : values) {
			MatchedElement match = matchWritable.getMatchedElement();
			BioCAnnotation biocAnnotation = new BioCAnnotation();
			BioCLocation biocLocation = new BioCLocation(
					match.getStartPos(), match.getEndPos()
							- match.getStartPos());
			biocAnnotation.addLocation(biocLocation);
			biocAnnotation.putInfon("dateTime",
					dateTimeFormat.format(now));
			biocAnnotation.putInfon("type", type);
			biocAnnotation.putInfon("value", match.getMatch());
			biocAnnotation.putInfon("confidence",
					"" + match.getConfidence());
			biocPassage.addAnnotation(biocAnnotation);
		}
		StringWriter sw = new StringWriter();
		try (BioCDocumentWriter writer = biocFactory.createBioCDocumentWriter(sw)) {
			writer.writeDocument(biocDoc);
		} catch (XMLStreamException e) {
			throw new IOException(e);
		}
		context.write(key, new Text(sw.toString()));
	}

}
