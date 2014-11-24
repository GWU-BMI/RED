package gov.va.research.red.ex;

import gov.va.research.red.CVScore;
import gov.va.research.red.LSTriplet;
import gov.va.research.red.LabeledSegment;
import gov.va.research.red.MatchedElement;
import gov.va.research.red.Snippet;
import gov.va.research.red.VTTReader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class REDExtractor {

	private static final Logger LOG = LoggerFactory
			.getLogger(REDExtractor.class);
	private LSExtractor leExt = new LSExtractor(null);
	private Map<String, Pattern> patternCache = new HashMap<String, Pattern>();

	public List<LSTriplet> discoverRegexes(List<File> vttFiles, String label,
			String outputFileName) throws IOException {
		// get snippets
		VTTReader vttr = new VTTReader();
		List<Snippet> snippets = new ArrayList<>();
		for (File vttFile : vttFiles) {
			snippets.addAll(vttr.extractSnippets(vttFile, label));
		}
		return extractRegexExpressions(snippets, label, outputFileName);
	}

	public List<LSTriplet> extractRegexExpressions(
			final List<Snippet> snippets, final String label,
			final String outputFileName) throws IOException {
		List<LSTriplet> ls3list = new ArrayList<>(snippets.size());
		for (Snippet snippet : snippets) {
			if (snippet.getLabeledSegments() != null) {
				for (LabeledSegment ls : snippet.getLabeledSegments()) {
					if (label.equals(ls.getLabel())) {
						ls3list.add(LSTriplet.valueOf(snippet.getText(), ls));
					}
				}
			}
		}
		if (ls3list != null && !ls3list.isEmpty()) {
			// replace all the punctuation marks with their regular expressions.
			replacePunct(ls3list);
			// replace all the digits in the LS with their regular expressions.
			replaceDigitsLS(ls3list);
			// replace the digits in BLS and ALS with their regular expressions.
			replaceDigitsBLSALS(ls3list);
			// replace the white spaces with regular expressions.
			replaceWhiteSpaces(ls3list);

			Map<LSTriplet, TripletMatches> tripsWithFP = findTripletsWithFalsePositives(
					ls3list, snippets, label);
			if (tripsWithFP != null && tripsWithFP.size() > 0) {
				LOG.warn("False positive regexes found before trimming");
				for (Map.Entry<LSTriplet, TripletMatches> twfp : tripsWithFP
						.entrySet()) {
					LOG.warn("RegEx: " + twfp.getKey().toStringRegEx());
					LOG.warn("Correct matches:");
					for (Snippet correct : twfp.getValue().getCorrect()) {
						LOG.warn("<correct value='"
								+ correct.getLabeledStrings() + "'>\n"
								+ correct.getText() + "\n</correct>");
					}
					LOG.warn("False positive matches:");
					for (Snippet fp : twfp.getValue().getFalsePositive()) {
						Pattern p = Pattern.compile(twfp.getKey()
								.toStringRegEx());
						Matcher m = p.matcher(fp.getText());
						m.find();
						LOG.warn("<fp actual='" + fp.getLabeledStrings()
								+ "' predicted='" + m.group(1) + "'>\n"
								+ fp.getText() + "\n</fp>");
					}
				}
			}

			// check if we can remove the first regex from bls. Keep on
			// repeating
			// the process till we can't remove any regex's from the bls's.
			trimRegEx(snippets, ls3list);
			ls3list = removeDuplicates(ls3list);

			leExt.setRegExpressions(ls3list);
			CVScore scoreAfterTrimming = testExtractor(snippets, leExt);

			// remove reduntant expressions
			List<LSTriplet> testList = new ArrayList<LSTriplet>(ls3list);
			int fnToTestAgainst = scoreAfterTrimming.getFn();
			CVScore tempScore = null;
			for (LSTriplet triplet : ls3list) {
				testList.remove(triplet);
				leExt.setRegExpressions(testList);
				tempScore = testExtractor(snippets, leExt);
				if (tempScore.getFn() < fnToTestAgainst)
					fnToTestAgainst = tempScore.getFn();
				else
					testList.add(triplet);
			}
			ls3list = testList;

			// the tree replacement algorithm
			List<PotentialMatch> potentialListBLS = new ArrayList<>();
			List<PotentialMatch> potentialListALS = new ArrayList<>();
			treeReplacementLogic(ls3list, potentialListBLS, potentialListALS);
			Comparator<PotentialMatch> comp = new Comparator<PotentialMatch>() {

				@Override
				public int compare(PotentialMatch o1, PotentialMatch o2) {
					if (o1.terms.size() < o2.terms.size())
						return 1;
					if (o1.terms.size() > o2.terms.size())
						return -1;
					return 0;
				}

			};
			Collections.sort(potentialListBLS, comp);
			Collections.sort(potentialListALS, comp);
			replacePotentialMatches(potentialListBLS, ls3list, true, snippets);
			replacePotentialMatches(potentialListALS, ls3list, false, snippets);
			List<LSTriplet> returnList = new ArrayList<LSTriplet>(ls3list.size());
			for (LSTriplet triplet : ls3list) {
				boolean add = true;
				for (LSTriplet tripletAdded : returnList) {
					if (tripletAdded.toStringRegEx().equals(triplet.toStringRegEx())) {
						add = false;
						break;
					}
				}
				if (add) {
					returnList.add(triplet);
				}
			}
			measureSensitivity(snippets, returnList);
			if (outputFileName != null && !outputFileName.equals("")) {
				File file = new File(outputFileName);
				File fileSensitvity = new File("sensitivity-"+outputFileName);
				if (!file.exists()){
					file.createNewFile();
				}
				if (!fileSensitvity.exists()) {
					fileSensitvity.createNewFile();
				}
				FileWriter fWriter = new FileWriter(file, false);
				PrintWriter pWriter = new PrintWriter(fWriter);
				FileWriter fWriterSens = new FileWriter(fileSensitvity, false);
				PrintWriter pWriterSens = new PrintWriter(fWriterSens);
				for (LSTriplet triplet : returnList) {
					pWriter.println(triplet.toString());
					pWriterSens.println(triplet.getSensitivity());
				}
				pWriter.close();
				fWriter.close();
				pWriterSens.close();
				fWriterSens.close();
			}
			return returnList;
		}
		return null;
	}
	
	/**
	 * @param ls3list
	 * @return
	 */
	private List<LSTriplet> removeDuplicates(final List<LSTriplet> ls3list) {
		LOG.debug("# Regexes List = {}", ls3list.size());
		Set<LSTriplet> ls3set = new HashSet<>(ls3list);
		LOG.debug("# Regexes Set = {}", ls3set.size());
		List<LSTriplet> newlist = new ArrayList<>(ls3set);
		return newlist;
	}

	private void measureSensitivity(List<Snippet> snippets, List<LSTriplet> regExList) {
		for (LSTriplet triplet : regExList) {
			int count = sensitivityCount(triplet, snippets);
			double sensitivity = ((double)count)/((double)snippets.size());
			triplet.setSensitivity(sensitivity);
		}
	}
	
	private int sensitivityCount(LSTriplet regEx, List<Snippet> snippets) {
		Pattern pattern = patternCache.get(regEx.toStringRegEx());
		int count = 0;
		if (pattern == null) {
			pattern = Pattern.compile(regEx.toStringRegEx());
			patternCache.put(regEx.toStringRegEx(), pattern);
		}
		for (Snippet snippt : snippets) {
			Matcher matcher = pattern.matcher(snippt.getText());
			if (matcher.find()) {
				count++;
			}
		}
		return count;
	}

	private void replacePotentialMatches(List<PotentialMatch> potentialMatches,
			List<LSTriplet> ls3List, boolean processBLS,
			final List<Snippet> snippets) {
		for (PotentialMatch match : potentialMatches) {
			if (match.count == 1) {
				for (LSTriplet triplet : match.matches) {
					String key = match.toString();
					if (processBLS) {
						String bls = triplet.getBLS();
						// if(!key.equals("S")){
						triplet.setBLS(triplet.getBLS().replaceAll(
								"\\b(?<!\\\\)" + key + "\\b",
								"\\\\S{1," + key.length() + "}"));// triplet.getBLS().replaceAll("?:"+key,
																	// "(?:"+key+")");
						List<LSTriplet> regEx = new ArrayList<LSTriplet>();
						regEx.add(triplet);
						leExt.setRegExpressions(regEx);
						// CVScore cvScore = cv.testExtractor(snippets, leExt);
						if (checkForFalsePositives(snippets, leExt))
							triplet.setBLS(bls);
					} else {
						String als = triplet.getALS();
						// if(!key.equals("S")){
						triplet.setALS(triplet.getALS().replaceAll(
								"\\b(?<!\\\\)" + key + "\\b",
								"\\\\S{1," + key.length() + "}"));// triplet.getALS().replaceAll("?:"+key,
																	// "(?:"+key+")");
						List<LSTriplet> regEx = new ArrayList<LSTriplet>();
						regEx.add(triplet);
						leExt.setRegExpressions(regEx);
						// CVScore cvScore = cv.testExtractor(snippets, leExt);
						if (checkForFalsePositives(snippets, leExt))
							triplet.setALS(als);
					}
				}
			}
		}
	}

	public List<LSTriplet> replacePunct(List<LSTriplet> ls3list) {
		String s;
		for (LSTriplet x : ls3list) {
			s = x.getLS();
			s = s.replaceAll("\\p{Punct}", "\\\\p{Punct}");
			x.setLS(s);
			s = x.getBLS();
			s = s.replaceAll("\\p{Punct}", "\\\\p{Punct}");
			x.setBLS(s);
			s = x.getALS();
			s = s.replaceAll("\\p{Punct}", "\\\\p{Punct}");
			x.setALS(s);
		}
		return ls3list;
	}

	// replace digits with '\d+'
	public List<LSTriplet> replaceDigitsLS(List<LSTriplet> ls3list) {
		String s;
		for (LSTriplet x : ls3list) {
			s = x.getLS();
			s = s.replaceAll("\\d+", "\\\\d+");
			x.setLS(s);
		}
		return ls3list;
	}

	// replace digits with '\d+'
	public List<LSTriplet> replaceDigitsBLSALS(List<LSTriplet> ls3list) {
		String s;
		for (LSTriplet x : ls3list) {
			s = x.getBLS();
			s = s.replaceAll("\\d+", "\\\\d+");// &^((?!\\.\\{1,20\\}).)*$
			x.setBLS(s);
			s = x.getALS();
			s = s.replaceAll("\\d+", "\\\\d+");// &^((?!\\.\\{1,20\\}).)*$
			x.setALS(s);
		}
		return ls3list;
	}

	// replace white spaces with 's{1,10}'
	public List<LSTriplet> replaceWhiteSpaces(List<LSTriplet> ls3list) {
		String s;
		for (LSTriplet x : ls3list) {
			s = x.getBLS();
			s = s.replaceAll("\\s+", "\\\\s{1,50}");
			x.setBLS(s);
			s = x.getLS();
			s = s.replaceAll("\\s+", "\\\\s{1,50}");
			x.setLS(s);
			s = x.getALS();
			s = s.replaceAll("\\s+", "\\\\s{1,50}");
			x.setALS(s);
		}
		return ls3list;
	}

	private void trimRegEx(final List<Snippet> snippets, List<LSTriplet> ls3list) {
		List<Boolean> blsPrevTrimSuccess = new ArrayList<>(ls3list.size());
		for (int i = 0; i < ls3list.size(); i++) {
			blsPrevTrimSuccess.add(Boolean.TRUE);
		}
		List<Boolean> alsPrevTrimSuccess = new ArrayList<>(blsPrevTrimSuccess);
		LOG.info("ForkJoinPool parallelism = " + ForkJoinPool.commonPool().getParallelism());
		while (true) {
			List<Integer> indexes = new CopyOnWriteArrayList<>();
			for (int i = 0; i < ls3list.size(); i++) {
				indexes.add(Integer.valueOf(i));
			}
			List<Boolean> processedBLSorALSs = indexes.parallelStream().map((i) -> {
				boolean processedBLSorALS = true;
				String bls = null, als = null;
				LSTriplet triplet = ls3list.get(i);
				if (blsPrevTrimSuccess.get(i)
						&& ((triplet.getBLS().length() >= triplet.getALS()
								.length()) || !alsPrevTrimSuccess.get(i))) {
					processedBLSorALS = false;
					bls = triplet.getBLS();
					if (bls.equals("") || bls == null) {
						blsPrevTrimSuccess.set(i, Boolean.FALSE);
					} else {
						char firstChar = bls.charAt(0);
						String blsWithoutFirstRegex = null;
						if (firstChar != '\\') {
							if (bls.indexOf("\\") != -1)
								blsWithoutFirstRegex = bls.substring(
										bls.indexOf("\\"), bls.length());
							else
								blsWithoutFirstRegex = "";
						} else {
							int index = 1;
							while (index < bls.length()) {
								char temp = bls.charAt(index++);
								if (temp == '+' || temp == '}')
									break;
							}
							if (index == bls.length())
								blsWithoutFirstRegex = "";
							else
								blsWithoutFirstRegex = bls.substring(index,
										bls.length());
						}

						triplet.setBLS(blsWithoutFirstRegex);
						List<LSTriplet> newRegExList = new ArrayList<LSTriplet>();
						newRegExList.add(triplet);
						leExt.setRegExpressions(newRegExList);
						if (checkForFalsePositives(snippets, leExt)) {
							triplet.setBLS(bls);
							blsPrevTrimSuccess.set(i, Boolean.FALSE);
						} else {
							blsPrevTrimSuccess.set(i, Boolean.TRUE);
						}
					}
				}
				if (alsPrevTrimSuccess.get(i).booleanValue()
						&& ((triplet.getBLS().length() <= triplet.getALS()
								.length()) || !blsPrevTrimSuccess.get(i).booleanValue())) {
					processedBLSorALS = false;
					als = triplet.getALS();
					if (als.equals("") || als == null) {
						alsPrevTrimSuccess.set(i, Boolean.FALSE);
					} else {
						String alsWithoutLastRegex = null;
						if (als.lastIndexOf("\\") == -1)
							alsWithoutLastRegex = "";
						else {
							int lastIndex = als.lastIndexOf("\\");
							int index = lastIndex;
							index++;
							while (index < als.length()) {
								char temp = als.charAt(index++);
								if (temp == '+' || temp == '}')
									break;
							}
							if (index == als.length()) {
								if (lastIndex == 0)
									alsWithoutLastRegex = "";
								else
									alsWithoutLastRegex = als.substring(0,
											lastIndex);
							} else
								alsWithoutLastRegex = als.substring(0, index);
						}

						triplet.setALS(alsWithoutLastRegex);
						List<LSTriplet> newRegExList = new ArrayList<LSTriplet>();
						newRegExList.add(triplet);
						leExt.setRegExpressions(newRegExList);
						if (checkForFalsePositives(snippets, leExt)) {
							triplet.setALS(als);
							alsPrevTrimSuccess.set(i, Boolean.FALSE);
						} else {
							alsPrevTrimSuccess.set(i, Boolean.TRUE);
						}
					}
				}
				return processedBLSorALS;
			}).collect(Collectors.toList());
			if (!processedBLSorALSs.contains(Boolean.FALSE)) {
				break;
			}
		}
	}

	// /**
	// * For every group it determines the frequent terms. It then replaces the
	// frequent terms by the regular expression \bfrequent term\b.
	// * It replaces the terms that are not frequent by .*{1,frequent term's
	// length}
	// * @param group The group of LSTriplets on which we are performing the
	// operation.
	// * @param snippetGroups The group of all snippets.
	// */
	// private void processGroupTrimVersion(List<LSTriplet> group,
	// Map<String,List<LSTriplet>> tempGroupMap, boolean processBLS, final
	// List<Snippet> snippets){
	// Map<String, List<LSTriplet>> freqMap = new HashMap<String,
	// List<LSTriplet>>();
	// for(LSTriplet triplet : group)
	// updateMFTMapTrimVersion(triplet, processBLS, freqMap);
	// List<Map.Entry<String, List<LSTriplet>>> entryList = new
	// ArrayList<Map.Entry<String, List<LSTriplet>>>();
	// for(Map.Entry<String, List<LSTriplet>> entry : freqMap.entrySet()){
	// if(entryList.isEmpty())
	// entryList.add(entry);
	// else{
	// int i=0;
	// for(i=0;i<entryList.size();i++){
	// Map.Entry<String, List<LSTriplet>> entryListElem = entryList.get(i);
	// if(entry.getValue().size() < entryListElem.getValue().size())
	// break;
	// }
	// entryList.add(i, entry);
	// }
	// }
	// for(Map.Entry<String, List<LSTriplet>> entry : entryList){
	// List<LSTriplet> value = entry.getValue();
	// String key = entry.getKey();
	// String bls=null,als=null;
	// for(LSTriplet triplet : value){
	// if(processBLS){
	// bls = triplet.getBLS();
	// //if(!key.equals("S")){
	// triplet.setBLS(triplet.getBLS().replaceAll("\\b(?<!\\\\)"+key+"\\b",
	// "\\\\S{1,"+key.length()+"}"));//triplet.getBLS().replaceAll("?:"+key,
	// "(?:"+key+")");
	// List<LSTriplet> regEx = new ArrayList<LSTriplet>();
	// regEx.add(triplet);
	// leExt.setRegExpressions(regEx);
	// //CVScore cvScore = cv.testExtractor(snippets, leExt);
	// if(checkForFalsePositives(snippets, leExt))
	// triplet.setBLS(bls);
	// }else{
	// als = triplet.getALS();
	// //if(!key.equals("S")){
	// triplet.setALS(triplet.getALS().replaceAll("\\b(?<!\\\\)"+key+"\\b",
	// "\\\\S{1,"+key.length()+"}"));//triplet.getALS().replaceAll("?:"+key,
	// "(?:"+key+")");
	// List<LSTriplet> regEx = new ArrayList<LSTriplet>();
	// regEx.add(triplet);
	// leExt.setRegExpressions(regEx);
	// //CVScore cvScore = cv.testExtractor(snippets, leExt);
	// if(checkForFalsePositives(snippets, leExt))
	// triplet.setALS(als);
	// }
	// }
	// }
	// }
	//
	// /**
	// * Finds out all the groups that have sizes greater than 1. Calls
	// processGroup on those groups.
	// * @param tripletGroups A hashmap containing the groups. Key is LS and the
	// value is a list of LSTriplet's.
	// */
	// private void processSnippetGroupsTrimVersion(Map<String,List<LSTriplet>>
	// tripletGroups, final List<Snippet> snippets){
	// java.util.Iterator<List<LSTriplet>> iteratorSnippetGroups =
	// tripletGroups.values().iterator();
	// Map<String, List<LSTriplet>> tempGroupMap = new HashMap<String,
	// List<LSTriplet>>();
	// while(iteratorSnippetGroups.hasNext()){
	// List<LSTriplet> group = iteratorSnippetGroups.next();
	// //if(group.size() > 1){
	// processGroupTrimVersion(group, tempGroupMap, true,snippets);
	// //}
	// }
	//
	// //repeat the above steps for ALS.
	// iteratorSnippetGroups = tripletGroups.values().iterator();
	// tempGroupMap = new HashMap<String, List<LSTriplet>>();
	// while(iteratorSnippetGroups.hasNext()){
	// List<LSTriplet> group = iteratorSnippetGroups.next();
	// //if(group.size() > 1){
	// processGroupTrimVersion(group, tempGroupMap, false,snippets);
	// //}
	// }
	// }

	/**
	 * Creates a frequency map of terms contained inside the BLS/ALS.
	 * 
	 * @param triplet
	 *            The triplet on which the processing is being performed.
	 * @param processingBLS
	 *            Specifies whether the processing is to be performed on BLS/ALS
	 * @param freqMap
	 *            a map containing a term as the key and a list of triplets
	 *            containing that term as the value.
	 */
	private void updateMFTMapTrimVersion(LSTriplet triplet,
			boolean processingBLS, Map<String, List<LSTriplet>> freqMap) {
		String phrase = "";
		if (processingBLS)
			phrase = triplet.getBLS();
		else
			phrase = triplet.getALS();
		String[] termArray = phrase
				.split("\\\\s\\{1,50\\}|\\\\p\\{Punct\\}|\\\\d\\+");
		List<LSTriplet> termContainingTriplets = null;
		for (String term : termArray) {
			if (!term.equals(" ") && !term.equals("")) {
				if (freqMap.containsKey(term))
					termContainingTriplets = freqMap.get(term);
				else
					termContainingTriplets = new ArrayList<LSTriplet>();
				termContainingTriplets.add(triplet);
				freqMap.put(term, termContainingTriplets);
			}
		}
	}

	public List<String> checkPattern(List<LSTriplet> listLS, File vttFile)
			throws IOException {
		List<Pattern> patterns = new ArrayList<Pattern>();
		for (LSTriplet item : listLS) {
			patterns.add(Pattern.compile(item.toStringRegEx()));

		}
		VTTReader vtt = new VTTReader();
		Collection<Snippet> cSp = vtt.extractSnippets(vttFile);
	    int max=0;
	    List<String> ret=null;
		for (Pattern p : patterns) {
			List<String> mat = new ArrayList<String>();
			for (Snippet item : cSp) {

				Matcher matcher = p.matcher(item.getText());
				if (matcher.find())
					mat.add(matcher.group(1));
			}
			if(mat.size()>max){
				ret=mat;
			}
			
		}

		return ret;

	}
	
	
	
	
	
	
	
	
	

	/**
	 * checks if any permutation of the terms matches against any bls or als if
	 * it does it records it,
	 * 
	 * @param prefixList
	 * @param termList
	 * @param ls3List
	 * @param processBLS
	 * @param potentialList
	 */
	private void performPermuation(List<String> prefixList,
			List<String> termList, List<LSTriplet> ls3List, boolean processBLS,
			List<PotentialMatch> potentialList) {
		if (!termList.isEmpty()) {
			for (int i = 0; i < termList.size(); i++) {
				List<String> tempPrefixList = new ArrayList<>();
				if (prefixList != null)
					tempPrefixList.addAll(prefixList);
				String tempElement = termList.get(i);
				tempPrefixList.add(tempElement);
				List<String> tempTermList = new ArrayList<>();
				tempTermList.addAll(termList);
				tempTermList.remove(tempElement);
				PotentialMatch match = findMatch(tempPrefixList, ls3List,
						processBLS);
				if (match.count > 0) {
					potentialList.add(match);
					performPermuation(tempPrefixList, tempTermList, ls3List,
							processBLS, potentialList);
				}
			}
		}
	}

	/**
	 * checks if the permutation calculated earlier matches against any of the
	 * bls or als.
	 * 
	 * @param termList
	 * @param ls3List
	 * @param processBLS
	 * @return
	 */
	private PotentialMatch findMatch(List<String> termList,
			List<LSTriplet> ls3List, boolean processBLS) {
		StringBuilder concatString = new StringBuilder("");
		int count = 0;
		List<LSTriplet> triplets = new ArrayList<>();
		for (int i = 0; i < termList.size(); i++) {
			if (i == termList.size() - 1)
				concatString.append(termList.get(i));
			else
				concatString.append(termList.get(i) + "\\s{1,50}");
		}
		for (LSTriplet triplet : ls3List) {
			String matchAgainst = null;
			if (processBLS)
				matchAgainst = triplet.getBLS();
			else
				matchAgainst = triplet.getALS();
			if (matchAgainst.contains(concatString)) {
				count++;
				triplets.add(triplet);
			}
		}
		PotentialMatch match = new PotentialMatch(termList, count, triplets);
		return match;
	}

	/**
	 * finds out all the terms in all the bls and als. It then checks to see if
	 * any permutation of the terms matches against any bls or als. If matches
	 * it records its in a list of potential matches.
	 * 
	 * @param ls3List
	 *            the original list of triplets
	 * @param potentialListBLS
	 *            records the list of potential matches for bls
	 * @param potentialListALS
	 *            records the list of potential matches for als
	 */
	private void treeReplacementLogic(List<LSTriplet> ls3List,
			List<PotentialMatch> potentialListBLS,
			List<PotentialMatch> potentialListALS) {
		Map<String, List<LSTriplet>> freqMap = new HashMap<String, List<LSTriplet>>();
		for (LSTriplet triplet : ls3List)
			updateMFTMapTrimVersion(triplet, true, freqMap);
		List<String> termList = new ArrayList<>();
		termList.addAll(freqMap.keySet());
		performPermuation(null, termList, ls3List, true, potentialListBLS);

		freqMap = new HashMap<String, List<LSTriplet>>();
		for (LSTriplet triplet : ls3List)
			updateMFTMapTrimVersion(triplet, false, freqMap);
		termList = new ArrayList<>();
		termList.addAll(freqMap.keySet());
		performPermuation(null, termList, ls3List, false, potentialListALS);
	}

	/**
	 * @param pw
	 * @param testing
	 * @param ex
	 * @return
	 */
	public CVScore testExtractor(List<Snippet> testing, LSExtractor ex,
			PrintWriter pw) {
		if (pw != null) {
			pw.println();
		}
		CVScore score = new CVScore();
		for (Snippet snippet : testing) {
			List<MatchedElement> candidates = ex.extract(snippet.getText());
			String predicted = REDExtractor.chooseBestCandidate(candidates);
			List<String> actual = snippet.getLabeledStrings();
			if (pw != null) {
				pw.println("--- Test Snippet:");
				pw.println(snippet.getText());
				pw.println("Predicted: " + predicted + ", Actual: " + actual);
			}
			// Score
			if (predicted == null) {
				if (actual == null || actual.size() == 0) {
					score.setTn(score.getTn() + 1);
				} else {
					score.setFn(score.getFn() + 1);
					pw.println("<##### ERROR - FN: Regexes");
					pw.println(""
						+ (ex == null ? "null"
							: ex.getRegExpressions() == null ? "null"
							: ex.getRegExpressions().toString()));
					pw.println("#####>");
				}
			} else if (actual == null || actual.size() == 0) {
				score.setFp(score.getFp() + 1);
			} else {
				if (snippet.getLabeledStrings().contains(predicted.trim())) {
					score.setTp(score.getTp() + 1);
				} else {
					score.setFp(score.getFp() + 1);
				}
			}
		}
		return score;
	}

	/**
	 * Method is used when trimming regex's. It is used to see if any false
	 * positives are genereated by the new regex.
	 * 
	 * @param testing
	 * @param ex
	 * @param pw
	 * @return
	 */
	private CVScore testExtractor(List<Snippet> testing, LSExtractor ex) {
		return testExtractor(testing, ex, null);
	}

	public boolean checkForFalsePositives(List<Snippet> testing, LSExtractor ex) {
		for (Snippet snippet : testing) {
			List<MatchedElement> candidates = ex.extract(snippet.getText());
			String predicted = chooseBestCandidate(candidates);
			List<String> actual = snippet.getLabeledStrings();

			// Score

			if (predicted == null) {

			} else if (actual == null || actual.size() == 0) {
				// score.setFp(score.getFp() + 1);
				return true;
			} else {
				if (snippet.getLabeledStrings().contains(predicted.trim())) {
					// score.setTp(score.getTp() + 1);
				} else {
					return true;
				}
			}
		}
		return false;
	}

	public static Map<LSTriplet, TripletMatches> findTripletsWithFalsePositives(
			final List<LSTriplet> ls3list, final List<Snippet> snippets,
			final String label) {
		Map<LSTriplet, TripletMatches> tripsWithFP = new HashMap<>();
		for (LSTriplet ls3 : ls3list) {
			List<Snippet> correct = new ArrayList<>();
			List<Snippet> falsePositive = new ArrayList<>();
			Pattern ls3pattern = Pattern.compile(ls3.toStringRegEx());
			for (Snippet snippet : snippets) {
				List<Snippet> lsCorrectMatch = new ArrayList<>();
				List<Snippet> lsFalseMatch = new ArrayList<>();
				for (LabeledSegment ls : snippet.getLabeledSegments()) {
					if (label.equals(ls.getLabel())) {
						Matcher m = ls3pattern.matcher(snippet.getText());
						if (m.find()) {
							String actual = ls.getLabeledString();
							String predicted = m.group(1);
							if ((predicted != null && actual == null)
									|| (predicted != null && !snippet
											.getLabeledStrings().contains(
													predicted.trim()))) {
								lsFalseMatch.add(snippet);
							} else {
								// The regex matched at least one of the
								// snippets labeled segments correctly
								lsCorrectMatch.add(snippet);
								break;
							}
						}
					}
				}
				if (lsCorrectMatch.size() == 0) {
					// The regex did not match any labeled segments correctly
					// count as a false positive.
					correct.addAll(lsCorrectMatch);
					falsePositive.addAll(lsFalseMatch);
				}
			}
			if (falsePositive.size() > 0) {
				tripsWithFP
						.put(ls3, new TripletMatches(correct, falsePositive));
			}
		}
		return tripsWithFP;
	}

	/**
	 * @param candidates
	 * @return
	 */
	public static String chooseBestCandidate(List<MatchedElement> candidates) {
		String category = null;
		if (candidates != null && candidates.size() > 0) {
			if (candidates.size() == 1) {
				category = candidates.get(0).getMatch();
			} else {
				// Multiple candidates, count their frequencies.
				Map<String, Integer> candidate2freq = new HashMap<String, Integer>();
				for (MatchedElement c : candidates) {
					Integer freq = candidate2freq.get(c.getMatch());
					if (freq == null) {
						freq = Integer.valueOf(1);
					} else {
						freq = Integer.valueOf(freq.intValue() + 1);
					}
					candidate2freq.put(c.getMatch(), freq);
				}
				// Sort by frequency
				TreeMap<Integer, List<String>> freq2candidates = new TreeMap<>();
				for (Map.Entry<String, Integer> c2f : candidate2freq.entrySet()) {
					List<String> freqCandidates = freq2candidates.get(c2f
							.getValue());
					if (freqCandidates == null) {
						freqCandidates = new ArrayList<>();
						freq2candidates.put(c2f.getValue(), freqCandidates);
					}
					freqCandidates.add(c2f.getKey());
				}
				List<String> mostFrequentCandidates = freq2candidates
						.lastEntry().getValue();
				// If there is only one candidate in the group with the highest
				// frequency then use it.
				if (mostFrequentCandidates.size() == 1) {
					category = mostFrequentCandidates.get(0);
				} else {
					// Multiple candidates with the highest frequency.
					// Choose the longest one, and if there is a tie then choose
					// the largest one lexicographically.
					Collections.sort(mostFrequentCandidates,
							new Comparator<String>() {
								@Override
								public int compare(String o1, String o2) {
									if (o1 == o2) {
										return 0;
									}
									if (o1 == null) {
										return 1;
									}
									if (o2 == null) {
										return -1;
									}
									if (o1.length() == o2.length()) {
										return o2.compareTo(o1);
									}
									return o2.length() - o1.length();
								}
							});
					category = mostFrequentCandidates.get(0);
				}
			}
		}
		return category;
	}

	public static class TripletMatches {
		private List<Snippet> correct;
		private List<Snippet> falsePositive;

		public TripletMatches(final List<Snippet> correct,
				final List<Snippet> falsePositive) {
			this.correct = correct;
			this.falsePositive = falsePositive;
		}

		public List<Snippet> getCorrect() {
			return correct;
		}

		public void setCorrect(List<Snippet> correct) {
			this.correct = correct;
		}

		public List<Snippet> getFalsePositive() {
			return falsePositive;
		}

		public void setFalsePositive(List<Snippet> falsePositive) {
			this.falsePositive = falsePositive;
		}
	}
}

class PotentialMatch {
	List<String> terms;
	int count;
	List<LSTriplet> matches;

	public PotentialMatch(List<String> terms, int count, List<LSTriplet> matches) {
		this.terms = terms;
		this.count = count;
		this.matches = matches;
	}

	@Override
	public String toString() {
		if (terms == null || terms.isEmpty())
			return " The match is empty";
		StringBuilder temp = new StringBuilder();
		for (int i = 0; i < terms.size(); i++) {
			if (i == terms.size() - 1)
				temp.append(terms.get(i));
			else
				temp.append(terms.get(i) + "\\s{1,50}");
		}
		return temp.toString();
	}
}
