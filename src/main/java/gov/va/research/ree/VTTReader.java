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

import java.io.File;
import java.io.IOException;

import gov.nih.nlm.nls.vtt.Model.VttDocument;

/**
 * @author vhaislreddd
 *
 */
public class VTTReader {

	public VttDocument read(final File vttFile) throws IOException {
		VttDocument vttDoc = new VttDocument();
		boolean valid = vttDoc.ReadFromFile(vttFile);
		if (!valid) {
			throw new IOException("Not a valid VTT file: " + vttFile);
		}
		return vttDoc;
	}

}
