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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author doug
 *
 */
public class CVUtils {
	/**
	 * @param folds
	 *            Number of folds (partitions) for cross-validation.
	 * @param snippets
	 *            The snippets to partition.
	 * @return <code>folds</code> lists of snippets, partitioned evenly.
	 */
	public static List<List<Snippet>> partitionSnippets(int folds,
			List<Snippet> snippets) {
		List<List<Snippet>> partitions = new ArrayList<>(folds);
		for (int i = 0; i < folds; i++) {
			partitions.add(new ArrayList<Snippet>());
		}
		Iterator<Snippet> snippetIter = snippets.iterator();
		int partitionIdx = 0;
		while (snippetIter.hasNext()) {
			if (partitionIdx >= folds) {
				partitionIdx = 0;
			}
			List<Snippet> partition = partitions.get(partitionIdx);
			partition.add(snippetIter.next());
			partitionIdx++;
		}
		return partitions;
	}

	/**
	 * Determines if a collection of strings contains a given string, ignoring
	 * case differences.
	 * 
	 * @param strings
	 *            a collection of strings
	 * @param string
	 *            a string
	 * @return <code>true</code> if <code>string</code> is contained in
	 *         <code>strings</code> where performing a case insentitive
	 *         comparison.
	 */
	public static boolean containsCI(final Collection<String> strings,
			final String string) {
		for (String s : strings) {
			if (s.equalsIgnoreCase(string)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determines if a collection of strings contains a given string, ignoring
	 * case differences.
	 * 
	 * @param strings
	 *            an array of strings
	 * @param string
	 *            a string
	 * @return <code>true</code> if <code>string</code> is contained in
	 *         <code>strings</code> where performing a case insentitive
	 *         comparison
	 */
	public static boolean containsCI(final String[] strings, final String string) {
		for (String s : strings) {
			if (s.equalsIgnoreCase(string)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determines if one collection of strings contains any members of another
	 * collection of strings. (do the collections intersect)
	 * 
	 * @param strings1
	 *            a collection of strings
	 * @param strings2
	 *            a collection of strings
	 * @param allowOverMatches
	 *            if <code>true</code> then substring matches count as matches
	 * @param caseInsensitive
	 *            if <code>true</code> then comparisons are performed in a
	 *            case-insensitive manner
	 * @return <code>true</code> if there are any members in common between
	 *         <code>strings1</code> and <code>strings2</code>
	 */
	public static boolean containsAny(final Collection<String> strings1,
			final Collection<String> strings2, boolean allowOverMatches,
			boolean caseInsensitive) {
		Collection<String> stringColl1 = null;
		Collection<String> stringColl2 = null;
		if (caseInsensitive) {
			Collection<String> lcStrings1 = new ArrayList<>(strings1.size());
			for (String s1 : strings1) {
				lcStrings1.add(s1.toLowerCase());
			}
			Collection<String> lcStrings2 = new ArrayList<>(strings2.size());
			for (String s2 : strings2) {
				lcStrings2.add(s2.toLowerCase());
			}
			stringColl1 = lcStrings1;
			stringColl2 = lcStrings2;
		} else {
			stringColl1 = strings1;
			stringColl2 = strings2;
		}
		for (String s1 : stringColl1) {
			for (String s2 : stringColl2) {
				if (allowOverMatches) {
					if (s1.contains(s2) || s2.contains(s1)) {
						return true;
					}
				} else {
					if (s1.equals(s2)) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
