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
import java.util.Arrays;

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
		out.writeInt(matchedElement.getStartPos());
		out.writeInt(matchedElement.getEndPos());
		out.writeInt(matchedElement.getMatch().length());
		out.writeChars(matchedElement.getMatch());
		out.writeInt(matchedElement.getMatchingRegex().length());
		out.writeChars(matchedElement.getMatchingRegex());
		out.writeDouble(matchedElement.getConfidence());
	}

	/* (non-Javadoc)
	 * @see org.apache.hadoop.io.Writable#readFields(java.io.DataInput)
	 */
	@Override
	public void readFields(DataInput in) throws IOException {
		int startPos = in.readInt();
		int endPos = in.readInt();
		int matchLen = in.readInt();
		char[] matchChars = new char[matchLen];
		for (int i = 0; i < matchLen; i++) {
			matchChars[i] = in.readChar();
		}
		String match = new String(matchChars);
		int matchingRegexLen = in.readInt();
		char[] matchingRegexChars = new char[matchingRegexLen];
		for (int i = 0; i < matchingRegexLen; i++) {
			matchingRegexChars[i] = in.readChar();
		}
		String matchingRegex = new String(matchingRegexChars);
		double confidence = in.readDouble();
		this.matchedElement = new MatchedElement(startPos, endPos, match, matchingRegex, confidence);
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

	@Override
	public boolean equals(Object other) {
		// TODO Auto-generated method stub
		return super.equals(other);
	}
	
}
