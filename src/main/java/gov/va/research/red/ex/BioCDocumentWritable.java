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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.io.BinaryComparable;
import org.apache.hadoop.io.WritableComparable;

import bioc.BioCDocument;

import com.google.gson.Gson;

/**
 * @author doug
 */
public class BioCDocumentWritable extends BinaryComparable
implements WritableComparable<BinaryComparable>{

	private BioCDocument biocDoc;
	private byte[] bytes;
	private Gson gson;

	public BioCDocumentWritable(BioCDocument biocDoc) {
		this.biocDoc = biocDoc;
	}

	/* (non-Javadoc)
	 * @see org.apache.hadoop.io.Writable#write(java.io.DataOutput)
	 */
	@Override
	public void write(DataOutput out) throws IOException {
		out.write(getBytes());
	}

	/* (non-Javadoc)
	 * @see org.apache.hadoop.io.Writable#readFields(java.io.DataInput)
	 */
	@Override
	public void readFields(DataInput in) throws IOException {
		StringBuilder sb = new StringBuilder();
		String line = null;
		while ((line = in.readLine()) != null) {
			sb.append(line);
		}
		this.biocDoc = getGson().fromJson(sb.toString(), BioCDocument.class);
		this.bytes = null;
	}

	/* (non-Javadoc)
	 * @see org.apache.hadoop.io.BinaryComparable#getLength()
	 */
	@Override
	public int getLength() {
		return getBytes().length;
	}

	/* (non-Javadoc)
	 * @see org.apache.hadoop.io.BinaryComparable#getBytes()
	 */
	@Override
	public byte[] getBytes() {
		if (bytes == null) {
			String json = getGson().toJson(biocDoc);
			bytes = json.getBytes();
		}
		return bytes;
	}

	public BioCDocument getBiocDoc() {
		return biocDoc;
	}

	public void setBiocDoc(BioCDocument biocDoc) {
		this.biocDoc = biocDoc;
		bytes = null;
	}
	
	private Gson getGson() {
		if (this.gson == null) {
			this.gson = new Gson();
		}
		return this.gson;
	}
}
