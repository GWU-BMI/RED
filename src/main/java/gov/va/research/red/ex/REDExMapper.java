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
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

/**
 * @author doug
 *
 */
public class REDExMapper extends Mapper<Text, Text, Text, MatchedElementWritable> {

	private Configuration conf;
	private REDExtractor rex;

	@Override
	protected void setup(
			Mapper<Text, Text, Text, MatchedElementWritable>.Context context)
			throws IOException, InterruptedException {
		super.setup(context);
		conf = context.getConfiguration();
		Iterator<Entry<String, String>> it = conf.iterator();
		while (it.hasNext()) {
			Entry<String,String> entry = it.next();
			System.out.println(entry.getKey() + ":" + entry.getValue());
		}
		String regexFilename = conf.get("regex.file");
		Path regexPath = FileSystems.getDefault().getPath("", regexFilename);
		rex = REDExtractor.load(regexPath);
	}

	@Override
	protected void map(
			Text key,
			Text value,
			Mapper<Text, Text, Text, MatchedElementWritable>.Context context)
			throws IOException, InterruptedException {
		List<MatchedElement> matches = rex.extract(value.toString());
		for (MatchedElement me : matches) {
			context.write(key, new MatchedElementWritable(me));
		}
	}
}
