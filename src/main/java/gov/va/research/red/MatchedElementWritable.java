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
package gov.va.research.red;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.BinaryComparable;
import org.apache.hadoop.io.WritableComparable;

/**
 * @author doug
 *
 */
public class MatchedElementWritable extends BinaryComparable
	implements WritableComparable<BinaryComparable> {

	private MatchedElement matchedElement;
	private byte[] bytes;
	
	public MatchedElementWritable() {
	}

	public MatchedElementWritable(MatchedElement matchedElement) {
		 this.matchedElement = matchedElement;
	}
	
	/* (non-Javadoc)
	 * @see org.apache.hadoop.io.Writable#write(java.io.DataOutput)
	 */
	@Override
	public void write(DataOutput out) throws IOException {
		out.writeUTF(matchedElement.toString());
	}

	/* (non-Javadoc)
	 * @see org.apache.hadoop.io.Writable#readFields(java.io.DataInput)
	 */
	@Override
	public void readFields(DataInput in) throws IOException {
		String meString = in.readUTF();
		this.matchedElement = MatchedElement.fromString(meString);
	}

	/* (non-Javadoc)
	 * @see org.apache.hadoop.io.BinaryComparable#getLength()
	 */
	@Override
	public int getLength() {
		if (bytes == null) {
			bytes = this.matchedElement.toString().getBytes();
		}
		return bytes.length;
	}

	/* (non-Javadoc)
	 * @see org.apache.hadoop.io.BinaryComparable#getBytes()
	 */
	@Override
	public byte[] getBytes() {
		if (bytes == null) {
			bytes = this.matchedElement.toString().getBytes();
		}
		return bytes;
	}

	public MatchedElement getMatchedElement() {
		return this.matchedElement;
	}
	
	public void setMatchedElement(MatchedElement matchedElement) {
		this.matchedElement = matchedElement;
	}
}
