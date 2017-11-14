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
package gov.va.research.red;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;

import gov.nih.nlm.nls.vtt.model.VttDocumentDelegate;

/**
 * @author vhaislreddd
 *
 */
public interface CrossValidatable {

	public List<CVResult> crossValidate(List<File> vttFiles, Collection<String> labels,
			Function<VttDocumentDelegate, TreeMap<Position, Snippet>> snippetParser) throws IOException;

}
