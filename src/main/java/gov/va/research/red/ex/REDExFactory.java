package gov.va.research.red.ex;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.output.TeeOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.va.research.red.CVResult;
import gov.va.research.red.CVScore;
import gov.va.research.red.CVUtils;
import gov.va.research.red.LabeledSegment;
import gov.va.research.red.MatchedElement;
import gov.va.research.red.Snippet;
import gov.va.research.red.Token;
import gov.va.research.red.TokenType;
import gov.va.research.red.VTTReader;
import gov.va.research.red.VTTSnippetParser;
import gov.va.research.red.ex.SnippetRegEx.TokenFreq;
import gov.va.research.red.regex.JSEPatternAdapter;
import gov.va.research.red.regex.MatcherAdapter;
import gov.va.research.red.regex.PatternAdapter;
import gov.va.research.red.regex.RE2JPatternAdapter;

public class REDExFactory {

	private static final Logger LOG = LoggerFactory
			.getLogger(REDExFactory.class);
	private static final boolean DEBUG = Boolean.valueOf(System.getProperty(
			"debug", String.valueOf(false)));

	public REDExModel train(final Collection<Snippet> snippets,
			final boolean allowOverMatches, final String outputTag,
			final boolean caseInsensitive, final boolean measureSensitivity,
			final List<String> holdouts, final boolean useTier2,
			final boolean generalizeLabeledSegments, Class<? extends PatternAdapter> patternAdapterClass)
			throws IOException {
		if (!caseInsensitive) {
			LOG.warn("caseInsensitive is set to false, which does not work correctly in many cases.");
		}
		// Set up snippet-to-regex map and regex history stacks
		Map<Snippet, Deque<SnippetRegEx>> snippet2regex = new HashMap<>(
				snippets.size());
		List<Deque<SnippetRegEx>> sreStacks = new ArrayList<>(snippets.size());
		for (Snippet snippet : snippets) {
			if (snippet.getPosLabeledSegments() == null
					|| snippet.getPosLabeledSegments().size() == 0) {
				snippet2regex.put(snippet, null);
			} else {
				// Create one snippet regex per positive labeled segment
				ListIterator<LabeledSegment> li = snippet
						.getPosLabeledSegments().listIterator();
				while (li.hasNext()) {
					LabeledSegment pls = li.next();
					if (pls.getLabeledString().trim().length() > 0) {
						// Build a new snippet for each positive labeled segment
						List<LabeledSegment> plslist = new ArrayList<>(1);
						plslist.add(pls);
						Deque<SnippetRegEx> snipStack = new ArrayDeque<>();
						snippet = new Snippet(snippet.getText(), plslist,
								snippet.getNegLabeledSegments());
						snipStack.push(new SnippetRegEx(snippet, caseInsensitive));
						sreStacks.add(snipStack);
						snippet2regex.put(snippet, snipStack);
					} else {
						LOG.warn("Empty labeled segment: " + pls.toString());
						li.remove();
					}
				}
			}
		}
		if (sreStacks == null || sreStacks.isEmpty()) {
			return null;
		}

		NoFalsePositives noFalsePositives = new NoFalsePositives(this);

		// Check for true and false positives. Each regex should have at least one true
		// positive, matching the snippet it originated from. Any false positives
		// indicate inconsistent annotation.
		
		List<WeightedRegEx> singleWeightedRegex = new ArrayList<>(1);
		singleWeightedRegex.add(null);
		List<Collection<WeightedRegEx>> singleTierWeightedRegex = new ArrayList<>(1);
		singleTierWeightedRegex.add(singleWeightedRegex);

		// Don't test for true or false positives here because line separators mess up the match before whitespace is generalized.
		
		// replace the white space with regular expressions.
		replaceWhiteSpace(sreStacks, caseInsensitive);
		for (Deque<SnippetRegEx> sreStack : sreStacks) {
			SnippetRegEx sre = sreStack.peek();
			boolean tps = checkForTruePositives(snippets, new REDExtractor(sre,
				caseInsensitive), allowOverMatches, caseInsensitive, useTier2, patternAdapterClass);
			if (!tps) {
				LOG.warn(outputTag
						+ ": No tps for regex before generalizing, should be at least one: "
						+ sre.toString());
			}
			singleWeightedRegex.set(0, sre);
			boolean fps = (0 == noFalsePositives.score(snippets,
					new REDExModel(singleTierWeightedRegex), allowOverMatches,
					caseInsensitive, useTier2, patternAdapterClass));
			if (fps) {
				LOG.warn("Inconsistent annotataion? : fps for regex before generalizing: "
						+ sre.toString());
			}
		}

		// Check for false positives. Each ls3 should have at least one true
		// positive, matching the snippet it originated from.
		for (Deque<SnippetRegEx> sreStack : sreStacks) {
			SnippetRegEx sre = sreStack.peek();
			boolean tps = checkForTruePositives(snippets, new REDExtractor(sre,
					caseInsensitive), allowOverMatches, caseInsensitive, useTier2, patternAdapterClass);
			if (!tps) {
				LOG.warn(outputTag
						+ ": No tps for regex, should be at least one: "
						+ sre.toString());
			}
			singleWeightedRegex.set(0, sre);
			boolean fps = (0 == noFalsePositives.score(snippets,
					new REDExModel(singleTierWeightedRegex), allowOverMatches,
					caseInsensitive, useTier2, patternAdapterClass));
			if (fps) {
				LOG.warn("Inconsistent annotataion? : fps for regex: "
						+ sre.toString());
			}
		}

		// replace all the digits with their regular expressions.
		replaceDigits(sreStacks, caseInsensitive);
		// replace puncuation
		replacePunct(sreStacks, caseInsensitive);

		// Check for false positives. Each ls3 should have at least one true
		// positive, matching the snippet it originated from.
		for (Deque<SnippetRegEx> sreStack : sreStacks) {
			SnippetRegEx sre = sreStack.peek();
			boolean tps = checkForTruePositives(snippets, new REDExtractor(sre,
					caseInsensitive), allowOverMatches, caseInsensitive, useTier2, patternAdapterClass);
			if (!tps) {
				LOG.warn("No tps for regex, should be at least one: "
						+ sre.toString());
			}
			singleWeightedRegex.set(0, sre);
			boolean fps = (0 == noFalsePositives.score(snippets,
					new REDExModel(singleTierWeightedRegex), allowOverMatches,
					caseInsensitive, useTier2, patternAdapterClass));
			if (fps) {
				LOG.warn("Inconsistent annotataion? : fps for regex: "
						+ sre.toString());
			}
		}

		sreStacks = removeDuplicates(sreStacks);

		// perform tier 1 discovery
		String ot1 = (outputTag == null ? "t1" : outputTag + "_t1");
		List<Deque<SnippetRegEx>> tier1 = abstractIteratively(snippets,
				sreStacks, allowOverMatches, ot1, caseInsensitive, null,
				noFalsePositives, holdouts, generalizeLabeledSegments, useTier2, patternAdapterClass);
		outputSnippet2Regex(snippet2regex, ot1);
		outputRegexHistory(sreStacks, ot1);

		// make a copy of the tier 1 results
		List<Deque<SnippetRegEx>> tier1Copy = new ArrayList<>(tier1.size());
		for (Deque<SnippetRegEx> t1stack : tier1) {
			Deque<SnippetRegEx> t1stackCopy = new ArrayDeque<>(t1stack.size());
			tier1Copy.add(t1stackCopy);
			Iterator<SnippetRegEx> t2sIt = t1stack.descendingIterator();
			while (t2sIt.hasNext()) {
				SnippetRegEx sre = t2sIt.next();
				SnippetRegEx sreCopy = new SnippetRegEx(sre, caseInsensitive);
				t1stackCopy.push(sreCopy);
			}
		}

		List<Collection<WeightedRegEx>> returnList = new ArrayList<>();
		returnList.add(new ArrayList<>(tier1.size()));
		for (Deque<SnippetRegEx> stack : tier1) {
			WeightedRegEx sre = stack.peek();
			boolean add = true;
			for (WeightedRegEx wrxAdded : returnList.get(0)) {
				if (wrxAdded.toString().equals(sre.toString())) {
					add = false;
					break;
				}
			}
			if (add) {
				returnList.get(0).add(sre);
			}
		}

		if (useTier2) {
			// perform tier 2 discovery
			ScoreFunction sf = new TPFPDiff();
			// ScoreFunction sf = new F1Score();
			String ot2 = (outputTag == null ? "t2" : outputTag + "_t2");
			List<Deque<SnippetRegEx>> tier2 = abstractIteratively(snippets,
					tier1Copy, allowOverMatches, ot2, caseInsensitive, sf, sf,
					holdouts, false, useTier2, patternAdapterClass);
			outputSnippet2Regex(snippet2regex, ot2);
			outputRegexHistory(sreStacks, ot2);

			returnList.add(new ArrayList<>(tier2.size()));

			for (Deque<SnippetRegEx> stack : tier2) {
				SnippetRegEx sre = stack.peek();
				boolean add = true;
				for (WeightedRegEx sreAdded : returnList.get(1)) {
					if (sreAdded.toString().equals(sre.toString())) {
						add = false;
						break;
					}
				}
				if (add) {
					returnList.get(1).add(sre);
				}
			}
		}

		if (measureSensitivity) {
			LOG.info(outputTag + ": measuring sensitivity ...");
			measureSensitivity(snippets, returnList, patternAdapterClass);
			LOG.info(outputTag + ": ... done measuring sensitivity");
		}
		return new REDExModel(returnList);
//				
//				new REDExtractor(returnList, "# snippets = " + snippets.size()
//				+ "\nallowOverMatches = " + allowOverMatches, caseInsensitive,
//				useTier2);
	}

	private List<Deque<SnippetRegEx>> abstractIteratively(
			final Collection<Snippet> snippets,
			final List<Deque<SnippetRegEx>> sreStacks,
			final boolean allowOverMatches, final String outputTag,
			final boolean caseInsensitive,
			final ScoreFunction beforeChangeScoreFunction,
			final ScoreFunction afterChangeScoreFunction,
			final List<String> holdouts, final boolean generalizeLS,
			final boolean useTier2, Class<? extends PatternAdapter> patternAdapterClass)
			throws IOException {
		String ot = outputTag == null ? "" : outputTag;
		LOG.info(ot + ": trimming regexes ...");
		trimRegEx(snippets, sreStacks, allowOverMatches, caseInsensitive,
				beforeChangeScoreFunction, afterChangeScoreFunction, holdouts, useTier2, patternAdapterClass);
		LOG.info(ot + ": ... done trimming regexes");
		List<Deque<SnippetRegEx>> newSreStacks = removeDuplicates(sreStacks);

		LOG.info(ot + ": generalizing LF to MF ...");
		newSreStacks = generalizeLFtoMF(snippets, sreStacks, allowOverMatches,
				caseInsensitive, beforeChangeScoreFunction,
				afterChangeScoreFunction, holdouts, useTier2, patternAdapterClass);
		newSreStacks = removeDuplicates(sreStacks);
		LOG.info(ot + ": ... done generalizing LF to MF");

		if (generalizeLS) {
			LOG.info(ot + ": generalizing LSs ...");
			newSreStacks = generalizeLS(snippets, sreStacks, allowOverMatches,
					caseInsensitive, beforeChangeScoreFunction,
					afterChangeScoreFunction, holdouts, useTier2, patternAdapterClass);
			LOG.info(ot + ": ... done generalizing LSs");
		}

		return newSreStacks;
	}

	/**
	 * @param snippets
	 *            Snippets for testing replacements
	 * @param snippetRegExStacks
	 *            Labeled segment triplets representing regexes
	 * @return
	 */
	private List<Deque<SnippetRegEx>> generalizeLFtoMF(
			Collection<Snippet> snippets,
			List<Deque<SnippetRegEx>> snippetRegExStacks,
			boolean allowOverMatches, boolean caseInsensitive,
			ScoreFunction beforeChangeScoreFunction,
			ScoreFunction afterChangeScoreFunction, List<String> holdouts,
			boolean useTier2, Class<? extends PatternAdapter> patternAdapterClass) {
		// build term frequency list
		Map<Token, TokenFreq> tokenFreqs = new HashMap<>();
		for (Deque<SnippetRegEx> snippetRegExStack : snippetRegExStacks) {
			SnippetRegEx sre = snippetRegExStack.peek();
			Collection<TokenFreq> snipTokenFreqs = sre.getTokenFrequencies();
			for (TokenFreq stf : snipTokenFreqs) {
				if (TokenType.WORD.equals(stf.getToken().getType())
						|| TokenType.PUNCTUATION.equals(stf.getToken()
								.getType())) {
					if (!(CVUtils.containsCI(holdouts, stf.getToken()
							.getString()))) {
						TokenFreq tf = tokenFreqs.get(stf.getToken());
						if (tf == null) {
							tokenFreqs.put(stf.getToken(), stf);
						} else {
							tf.setFreq(Integer.valueOf(tf.getFreq().intValue()
									+ stf.getFreq().intValue()));
						}
					}
				}
			}
		}
		List<TokenFreq> tokenFreqList = new ArrayList<>(tokenFreqs.values());
		Collections.sort(tokenFreqList);
		// Attempt to generalize each term, starting with the least frequent
		for (TokenFreq tf : tokenFreqList) {
			Token token = tf.getToken();
			snippetRegExStacks
					.parallelStream()
					.forEach(
							(sreStack) -> {
								boolean replaced = false;
								SnippetRegEx newSre = new SnippetRegEx(sreStack
										.peek(), caseInsensitive);
								for (Segment newUnlabeledSegment : newSre
										.getUnlabeledSegments()) {
									ListIterator<Token> newUlsIt = newUnlabeledSegment
											.getTokens().listIterator();
									List<WeightedRegEx> singleWeightedRegex = new ArrayList<>(1);
									singleWeightedRegex.add(null);
									List<Collection<WeightedRegEx>> singleTierWeightedRegex = new ArrayList<>(1);
									singleTierWeightedRegex.add(singleWeightedRegex);
									while (newUlsIt.hasNext()) {
										SnippetRegEx saveSre = new SnippetRegEx(
												newSre, caseInsensitive);
										Token newUlsToken = newUlsIt.next();
										if (newUlsToken.equals(token)) {
											boolean changed = false;
											if (TokenType.WORD
													.equals(newUlsToken
															.getType())) {
												newUlsIt.set(new Token(
														(caseInsensitive ? "[a-z]"
																: "[A-Za-z]")
																+ "{1,"
																+ ((int) Math
																		.ceil(newUlsToken
																				.getString()
																				.length() * 1.2))
																+ "}?",
														TokenType.REGEX));
												changed = true;
											} else if (TokenType.PUNCTUATION
													.equals(newUlsToken
															.getType())) {
												newUlsIt.set(new Token(
														"\\p{Punct}{1,"
																+ ((int) Math
																		.ceil(newUlsToken
																				.getString()
																				.length() * 1.2))
																+ "}?",
														TokenType.REGEX));
												changed = true;
											}
											if (changed) {
												singleWeightedRegex.set(0, saveSre);
												float beforeScore = (beforeChangeScoreFunction == null ? 1
														: beforeChangeScoreFunction
																.score(snippets,
																		new REDExModel(
																				singleTierWeightedRegex),
																		allowOverMatches,
																		caseInsensitive,
																		useTier2, patternAdapterClass));
												singleWeightedRegex.set(0, newSre);
												float afterScore = (afterChangeScoreFunction == null ? 0
														: afterChangeScoreFunction
																.score(snippets,
																		new REDExModel(singleTierWeightedRegex),
																		allowOverMatches,
																		caseInsensitive,
																		useTier2, patternAdapterClass));
												if (afterScore < beforeScore) {
													// revert
													newSre = saveSre;
												} else {
													replaced = true;
												}
											}
										}
									}
								}
								if (replaced) {
									if (!DEBUG) {
										sreStack.clear();
									}
									sreStack.push(newSre);
								}
							});
		}
		return snippetRegExStacks;
	}

	/**
	 * @param snippetRegExStacks
	 * @throws IOException
	 */
	private void outputRegexHistory(
			List<Deque<SnippetRegEx>> snippetRegExStacks, String outputTag)
			throws IOException {
		try (PrintWriter pw = new PrintWriter("log/regex-history_" + outputTag
				+ ".txt")) {
			pw.println();
			for (Deque<SnippetRegEx> snippetRegExStack : snippetRegExStacks) {
				pw.println("---------- GS ----------");
				for (SnippetRegEx snippetRegEx : snippetRegExStack) {
					pw.println("----- RS -----");
					pw.println(snippetRegEx.toString());
				}
			}
		}
	}

	/**
	 * @param snippetRegExStacks
	 * @throws IOException
	 */
	private void outputSnippet2Regex(
			Map<Snippet, Deque<SnippetRegEx>> snippet2regex, String outputTag)
			throws IOException {
		new File("log").mkdir();
		try (PrintWriter pw = new PrintWriter("log/snippet-regex_" + outputTag
				+ ".txt")) {
			boolean first = true;
			for (Map.Entry<Snippet, Deque<SnippetRegEx>> snip2re : snippet2regex
					.entrySet()) {
				if (first) {
					first = false;
				} else {
					pw.println();
				}
				pw.println("Snippet: " + snip2re.getKey().getText());
				String valStr = null;
				Deque<SnippetRegEx> stack = snip2re.getValue();
				if (stack != null) {
					SnippetRegEx val = stack.peek();
					if (val != null) {
						valStr = val.toString();
					}
				}
				pw.println("Regex  : " + valStr);
			}
		}
	}

	/**
	 * Generalize the LS element of each triplet to work for all LSs in the list
	 * that won't cause false positives.
	 * 
	 * @param snippetRegExStacks
	 * @return A new LSTriplet list, with each LS segment replaced by a
	 *         combination of all LSs in the list that won't cause false
	 *         positives.
	 */
	private List<Deque<SnippetRegEx>> generalizeLS(
			final Collection<Snippet> snippets,
			final List<Deque<SnippetRegEx>> snippetRegExStacks,
			final boolean allowOverMatches, final boolean caseInsensitive,
			ScoreFunction beforeChangeScoreFunction,
			ScoreFunction afterChangeScoreFunction, final List<String> holdouts,
			final boolean useTier2, Class<? extends PatternAdapter> patternAdapterClass) {
		Set<Segment> lsSet = new HashSet<>();
		for (Deque<SnippetRegEx> sreStack : snippetRegExStacks) {
			SnippetRegEx sre = sreStack.peek();
			lsSet.addAll(sre.getLabeledSegments());
		}
		for (Segment s : lsSet) {
			if (s.getTokens().size() == 1
					&& s.getTokens().get(0).getType() == TokenType.WHITESPACE) {
				LOG.error("empty labeled segment");
			}
		}
		boolean first = true;
		List<Token> genLS = new ArrayList<>();
		Token orToken = new Token("|", TokenType.REGEX);
		for (Segment ls : lsSet) {
			if (first) {
				first = false;
			} else {
				genLS.add(orToken);
			}
			genLS.addAll(ls.getTokens());
		}
		List<WeightedRegEx> singleWeightedRegex = new ArrayList<>(1);
		singleWeightedRegex.add(null);
		List<Collection<WeightedRegEx>> singleTierWeightedRegex = new ArrayList<>(1);
		singleTierWeightedRegex.add(singleWeightedRegex);
		for (Deque<SnippetRegEx> ls3stack : snippetRegExStacks) {
			SnippetRegEx beforeSre = ls3stack.peek();
			SnippetRegEx sreCopy = new SnippetRegEx(beforeSre, caseInsensitive);
			sreCopy.setLabeledSegments(new Segment(new ArrayList<>(genLS), true));
			singleWeightedRegex.set(0, beforeSre);
			float beforeScore = (beforeChangeScoreFunction == null ? 1
					: beforeChangeScoreFunction.score(snippets,
							new REDExModel(singleTierWeightedRegex),
							allowOverMatches, caseInsensitive, useTier2, patternAdapterClass));
			singleWeightedRegex.set(0, beforeSre);
			float afterScore = (afterChangeScoreFunction == null ? 0
					: afterChangeScoreFunction.score(snippets,
							new REDExModel(singleTierWeightedRegex),
							allowOverMatches, caseInsensitive, useTier2, patternAdapterClass));
			if (beforeScore <= afterScore) {
				if (!DEBUG) {
					ls3stack.clear();
				}
				ls3stack.push(sreCopy);
			}
		}
		return snippetRegExStacks;
	}

	/**
	 * @param ls3list
	 *            A list of LSTriplet Deques
	 * @return A new list of LSTriplet Deques with no duplicates (ls3list is not
	 *         modified).
	 */
	private List<Deque<SnippetRegEx>> removeDuplicates(
			final List<Deque<SnippetRegEx>> ls3list) {
		List<SnippetRegEx> headList = new ArrayList<>(ls3list.size());
		List<Deque<SnippetRegEx>> nodups = new ArrayList<>(ls3list.size());
		for (Deque<SnippetRegEx> ls3stack : ls3list) {
			SnippetRegEx head = ls3stack.peek();
			if (!headList.contains(head)) {
				headList.add(head);
				nodups.add(ls3stack);
			}
		}
		return nodups;
	}

	private void measureSensitivity(Collection<Snippet> snippets,
			List<Collection<WeightedRegEx>> rankedRegExLists, Class<? extends PatternAdapter> patternAdapterClass) {
		for (Collection<? extends WeightedRegEx> regexs : rankedRegExLists) {
			for (WeightedRegEx regEx : regexs) {
				int count = sensitivityCount(regEx, snippets, patternAdapterClass);
				double sensitivity = ((double) count)
						/ ((double) snippets.size());
				regEx.setWeight(sensitivity);
			}
		}
	}

	private int sensitivityCount(WeightedRegEx regEx,
			Collection<Snippet> snippets, Class<? extends PatternAdapter> patternAdapterClass) {
		int count = 0;
		for (Snippet snippt : snippets) {
			MatcherAdapter matcher = regEx.getPattern(patternAdapterClass).matcher(
					snippt.getText());
			while (matcher.find()) {
				count++;
			}
		}
		return count;
	}

	private List<Deque<SnippetRegEx>> replaceDigits(
			List<Deque<SnippetRegEx>> snippetRegExStacks, boolean caseInsentitive) {
		snippetRegExStacks.parallelStream().forEach((sreStack) -> {
			SnippetRegEx sre = sreStack.peek();
			SnippetRegEx newSre = new SnippetRegEx(sre, caseInsentitive);
			boolean changed = newSre.replaceDigits();
			if (changed) {
				if (!DEBUG) {
					sreStack.clear();
				}
				sreStack.push(newSre);
			}
		});
		return snippetRegExStacks;
	}

	private List<Deque<SnippetRegEx>> replacePunct(
			List<Deque<SnippetRegEx>> snippetRegExStacks, boolean caseInsensitive) {
		snippetRegExStacks.parallelStream().forEach((sreStack) -> {
			SnippetRegEx sre = sreStack.peek();
			SnippetRegEx newSre = new SnippetRegEx(sre, caseInsensitive);
			boolean changed = newSre.replacePunct();
			if (changed) {
				if (!DEBUG) {
					sreStack.clear();
				}
				sreStack.push(newSre);
			}
		});
		return snippetRegExStacks;
	}

	protected List<Deque<SnippetRegEx>> replaceWhiteSpace(
			List<Deque<SnippetRegEx>> snippetRegExStacks, boolean caseInsensitive) {
		snippetRegExStacks.parallelStream().forEach((ls3stack) -> {
			SnippetRegEx sre = ls3stack.peek();
			SnippetRegEx newSre = new SnippetRegEx(sre, caseInsensitive);
			boolean changed = newSre.replaceWhiteSpace();
			if (changed) {
				if (!DEBUG) {
					ls3stack.clear();
				}
				ls3stack.push(newSre);
			}
		});
		return snippetRegExStacks;
	}

	/**
	 * check if we can remove the first regex from bls. Keep on repeating the
	 * process till we can't remove any regex's from the bls's.
	 * 
	 * @param snippets
	 * @param snippetRegExStacks
	 */
	private void trimRegEx(final Collection<Snippet> snippets,
			List<Deque<SnippetRegEx>> snippetRegExStacks,
			boolean allowOverMatches, boolean caseInsensitive,
			ScoreFunction beforeChangeScoreFunction,
			ScoreFunction afterChangeScoreFunction, List<String> holdouts,
			boolean useTier2, Class<? extends PatternAdapter> patternAdapterClass) {
		// trim from the front and back, repeat while progress is being made
		snippetRegExStacks
				.parallelStream()
				.forEach(sreStack -> {
					boolean beginningProgress = false;
					boolean endProgress = false;
					List<WeightedRegEx> singleWeightedRegex = new ArrayList<>(1);
					singleWeightedRegex.add(null);
					List<Collection<WeightedRegEx>> singleTierWeightedRegex = new ArrayList<>(1);
					singleTierWeightedRegex.add(singleWeightedRegex);
					do {
						beginningProgress = false;
						endProgress = false;
						SnippetRegEx beforeSre = sreStack.peek();
						SnippetRegEx sreTrim = new SnippetRegEx(beforeSre, caseInsensitive);
						// Trim from the front or the back, whichever is longer
						// or is not terminated by a holdout word
						Token headToken = sreTrim.getBeginningToken();
						boolean headEligible = headToken != null
								&& (!(TokenType.WORD.equals(headToken.getType()) && CVUtils
										.containsCI(holdouts,
												headToken.getString())));
						if (headEligible
								&& sreTrim.getFirstSegmentLength() >= sreTrim
										.getLastSegmentLength()) {
							Token removed = sreTrim.trimFromBeginning();
							if (removed != null) {
								singleWeightedRegex.set(0, beforeSre);
								float beforeScore = (beforeChangeScoreFunction == null ? 1
										: beforeChangeScoreFunction.score(
												snippets, new REDExModel(singleTierWeightedRegex),
												allowOverMatches,
												caseInsensitive, useTier2, patternAdapterClass));
								singleWeightedRegex.set(0, sreTrim);
								float afterScore = (afterChangeScoreFunction == null ? 0
										: afterChangeScoreFunction.score(
												snippets, new REDExModel(singleTierWeightedRegex),
												allowOverMatches,
												caseInsensitive, useTier2, patternAdapterClass));
								if (afterScore < beforeScore) {
									sreTrim.addToBeginning(removed);
									beginningProgress = false;
								} else {
									beginningProgress = true;
								}
							}
						} else {
							Token tailToken = sreTrim.getEndToken();
							boolean tailEligible = tailToken != null
									&& (!(TokenType.WORD.equals(tailToken
											.getType()) && CVUtils.containsCI(
											holdouts, tailToken.getString())));
							if (tailEligible
									&& sreTrim.getFirstSegmentLength() <= sreTrim
											.getLastSegmentLength()) {
								Token removed = sreTrim.trimFromEnd();
								if (removed != null) {
									singleWeightedRegex.set(0, beforeSre);
									float beforeScore = (beforeChangeScoreFunction == null ? 1
											: beforeChangeScoreFunction.score(
													snippets, new REDExModel(
															singleTierWeightedRegex),
													allowOverMatches,
													caseInsensitive, useTier2, patternAdapterClass));
									singleWeightedRegex.set(0, sreTrim);
									float afterScore = (afterChangeScoreFunction == null ? 0
											: afterChangeScoreFunction.score(
													snippets, new REDExModel(
															singleTierWeightedRegex),
													allowOverMatches,
													caseInsensitive, useTier2, patternAdapterClass));
									if (afterScore < beforeScore) {
										sreTrim.addToEnd(removed);
										endProgress = false;
									} else {
										endProgress = true;
									}
								}
							}
						}
						if (beginningProgress || endProgress) {
							if (!DEBUG) {
								sreStack.clear();
							}
							sreStack.push(sreTrim);
						}
					} while (beginningProgress || endProgress);
				});
	}

	enum RESULT {
		TP, TN, FP, FN
	};

	/**
	 * @param testing
	 *            A collection of snippets to use for testing.
	 * @param ex
	 *            The extractor to be tested.
	 * @param allowOverMatches
	 *            If <code>false</code> then predicated and actual values must
	 *            match exactly to be counted as a true positive. If
	 *            <code>true</code> then if the predicted and actual values
	 *            overlap but do not match exactly, it is still counted as a
	 *            true positive.
	 * @param caseInsensitive
	 *            If <code>true</code> then comparisons will be done in a
	 *            case-insensitive manner.
	 * @param pw
	 *            A PrintWriter for recording output. May be <code>null</code>.
	 * @param useTier2
	 *            if <code>false</code> then tier 2 regular expressions will not
	 *            be used, only tier 1.
	 * @param patternAdapterClass The adapter class to use for the chosen regular expression engine.
	 * @return The cross-validation score.
	 */
	public CVScore test(Collection<Snippet> testing, REDExModel ex,
			boolean allowOverMatches, boolean caseInsensitive, PrintWriter pw, boolean useTier2,
			Class<? extends PatternAdapter> patternAdapterClass) {
		PrintWriter tempLocalPW = null;
		StringWriter sw = null;
		if (pw != null) {
			sw = new StringWriter();
			tempLocalPW = new PrintWriter(sw);
		}
		final PrintWriter localPW = tempLocalPW;
		CVScore score = testing
				.parallelStream()
				.map((snippet) -> {
					return testREDExOnSnippet(ex, allowOverMatches,
							caseInsensitive, localPW, snippet, useTier2, patternAdapterClass);
				}).reduce(new CVScore(), (s, r) -> {
					s.add(r);
					return s;
				}, (r1, r2) -> {
					if (r1 != r2) {
						r1.add(r2);
					}
					return r1;
				});
		if (pw != null && localPW != null && sw != null) {
			localPW.close();
			pw.println();
			pw.append(sw.toString());
			try {
				sw.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return score;
	}

	CVScore testREDExOnSnippet(REDExModel ex, boolean allowOverMatches,
			boolean caseInsensitive, final PrintWriter localPW, Snippet snippet, boolean useTier2, Class<? extends PatternAdapter> patternAdapterClass) {
		Set<MatchedElement> predictions = REDExtractor.extract(ex.getRegexTiers(), snippet.getText(), useTier2, patternAdapterClass);
		List<String> actual = snippet.getPosLabeledStrings();

		// if case insensitive, convert all predictions to lower case
		if (caseInsensitive) {
			for (MatchedElement me : predictions) {
				me.setMatch(me.getMatch().toLowerCase());
			}
		}
		Set<Integer> candPosMatchIndexes = new HashSet<>();
		Set<Integer> plsMatchIndexes = new HashSet<>();
		Set<Integer> candNegMatchIndexes = new HashSet<>();
		Set<Integer> nlsMatchIndexes = new HashSet<>();
		List<MatchedElement> candidateList = new ArrayList<>(predictions);
		for (int c = 0; c < candidateList.size(); c++) {
			MatchedElement candidate = candidateList.get(c);
			if (allowOverMatches) {
				for (int p = 0; p < snippet.getPosLabeledSegments().size(); p++) {
					LabeledSegment pls = snippet.getPosLabeledSegments().get(p);
					if (caseInsensitive) {
						pls.setLabeledString(pls.getLabeledString()
								.toLowerCase());
					}
					if (rangesOverlap(pls.getStart(),
							pls.getStart() + pls.getLength(),
							candidate.getStartPos(), candidate.getEndPos())
							&& stringsOverlap(pls.getLabeledString(),
									candidate.getMatch())) {
						candPosMatchIndexes.add(c);
						plsMatchIndexes.add(p);
					}
				}
				for (int n = 0; n < snippet.getNegLabeledSegments().size(); n++) {
					LabeledSegment nls = snippet.getNegLabeledSegments().get(n);
					if (caseInsensitive) {
						nls.setLabeledString(nls.getLabeledString()
								.toLowerCase());
					}
					if (rangesOverlap(nls.getStart(),
							nls.getStart() + nls.getLength(),
							candidate.getStartPos(), candidate.getEndPos())
							&& stringsOverlap(nls.getLabeledString(),
									candidate.getMatch())) {
						candNegMatchIndexes.add(c);
						nlsMatchIndexes.add(n);
					}
				}
			} else {
				for (int p = 0; p < snippet.getPosLabeledSegments().size(); p++) {
					LabeledSegment ls = snippet.getPosLabeledSegments().get(p);
					if (ls.getStart() == candidate.getStartPos()
							&& ls.getStart() + ls.getLength() == candidate
									.getEndPos()
							&& ls.getLabeledString().equals(
									candidate.getMatch())) {
						if (caseInsensitive) {
							if (ls.getLabeledString().equalsIgnoreCase(
									candidate.getMatch())) {
								candPosMatchIndexes.add(c);
								plsMatchIndexes.add(p);
							}
						} else {
							if (ls.getLabeledString().equals(
									candidate.getMatch())) {
								candPosMatchIndexes.add(c);
								plsMatchIndexes.add(p);
							}
						}
					}
				}
				for (int n = 0; n < snippet.getNegLabeledSegments().size(); n++) {
					LabeledSegment nls = snippet.getNegLabeledSegments().get(n);
					if (nls.getStart() == candidate.getStartPos()
							&& nls.getStart() + nls.getLength() == candidate
									.getEndPos()
							&& nls.getLabeledString().equals(
									candidate.getMatch())) {
						if (caseInsensitive) {
							if (nls.getLabeledString().equalsIgnoreCase(
									candidate.getMatch())) {
								candNegMatchIndexes.add(c);
								nlsMatchIndexes.add(n);
							}
						} else {
							if (nls.getLabeledString().equals(
									candidate.getMatch())) {
								candNegMatchIndexes.add(c);
								nlsMatchIndexes.add(n);
							}
						}
					}
				}
			}
		}
		// Score
		int tp = 0;
		int fp = 0;
		int tn = 0;
		int fn = 0;
		// See if each positive labeled segment was matched
		for (int p = 0; p < snippet.getPosLabeledSegments().size(); p++) {
			if (plsMatchIndexes.contains(p)) {
				tp++;
			} else {
				fn++;
			}
		}
		// See if any negative labeled segments were matched
		for (int n = 0; n < snippet.getNegLabeledSegments().size(); n++) {
			if (nlsMatchIndexes.contains(n)) {
				fp++;
			} else {
				tn++;
			}
		}
		// Account for any candidates that were incorrect
		for (int c = 0; c < candidateList.size(); c++) {
			if (!(candPosMatchIndexes.contains(c) || candNegMatchIndexes
					.contains(c))) {
				fp++;
			}
		}
		// If there were no positive annotation, and there were no predictions,
		// then count it as a true negative
		if (predictions.size() == 0
				&& snippet.getPosLabeledSegments().size() == 0) {
			tn++;
		}

		if (localPW != null && fp > 0) {
			StringBuilder sbP = new StringBuilder();
			StringBuilder sbRe = new StringBuilder();
			for (int n = 0; n < snippet.getNegLabeledSegments().size(); n++) {
				boolean first = true;
				if (candNegMatchIndexes.contains(n)) {
					MatchedElement cand = candidateList.get(n);
					if (first) {
						first = false;
						sbP.append("[" + cand.getMatch());
					} else {
						sbP.append(", " + cand.getMatch());
					}
					sbRe.append(cand.getMatchingRegexs()).append("\n");
				}
			}
			sbP.append("]");
			localPW.println("##### FALSE POSITIVE #####"
					+ "\n--- Test Snippet:" + "\n" + snippet.getText()
					+ "\n>>> Predicted: " + sbP.toString() + ", Actual: "
					+ actual + "\nPredicting Regexes:" + "\n" + sbRe.toString());
		}
		return new CVScore(tp, tn, fp, fn);
	}

	private boolean rangesOverlap(int start1, int end1, int start2, int end2) {
		return (start1 == start2 && end1 == end2)
				|| ((start1 >= start2 && start1 <= end2) || (end1 >= start2 && end1 <= end2));
	}

	private boolean stringsOverlap(final String str1, final String str2) {
		return str1.contains(str2) || str2.contains(str1);
	}

	private boolean checkForTruePositives(Collection<Snippet> testing,
			REDExtractor ex, boolean allowOverMatches, boolean caseInsensitive, boolean useTier2, Class<? extends PatternAdapter> patternAdapterClass) {
		return testing
				.parallelStream()
				.map((snippet) -> {
					Set<MatchedElement> candidates = REDExtractor.extract(ex.getRankedSnippetRegExs(), snippet
							.getText(), useTier2, patternAdapterClass);
					List<String> actual = snippet.getPosLabeledStrings();

					if (candidates == null || candidates.size() == 0) {
						return Boolean.FALSE;
					} else if (actual == null || actual.size() == 0) {
						return Boolean.FALSE;
					} else {
						if (caseInsensitive) {
							for (MatchedElement me : candidates) {
								me.setMatch(me.getMatch().toLowerCase());
							}
						}
						List<MatchedElement> candidateList = new ArrayList<>(
								candidates);
						for (int c = 0; c < candidateList.size(); c++) {
							MatchedElement candidate = candidateList.get(c);
							if (allowOverMatches) {
								for (int p = 0; p < snippet
										.getPosLabeledSegments().size(); p++) {
									LabeledSegment ls = snippet
											.getPosLabeledSegments().get(p);
									if (caseInsensitive) {
										ls.setLabeledString(ls
												.getLabeledString()
												.toLowerCase());
									}
									if (rangesOverlap(ls.getStart(),
											ls.getStart() + ls.getLength(),
											candidate.getStartPos(),
											candidate.getEndPos())
											&& stringsOverlap(
													ls.getLabeledString(),
													candidate.getMatch())) {
										return Boolean.TRUE;
									}
								}
							} else {
								for (int p = 0; p < snippet
										.getPosLabeledSegments().size(); p++) {
									LabeledSegment ls = snippet
											.getPosLabeledSegments().get(p);
									if (ls.getStart() == candidate
											.getStartPos()
											&& ls.getStart() + ls.getLength() == candidate
													.getEndPos()
											&& ls.getLabeledString().equals(
													candidate.getMatch())) {
										if (caseInsensitive) {
											if (ls.getLabeledString()
													.equalsIgnoreCase(
															candidate
																	.getMatch())) {
												return Boolean.TRUE;
											}
										} else {
											if (ls.getLabeledString().equals(
													candidate.getMatch())) {
												return Boolean.TRUE;
											}
										}
									}
								}
							}
						}
						return Boolean.FALSE;
					}
				}).anyMatch((tp) -> {
					return tp;
				});
	}

	/**
	 * @param candidates
	 *            The candidate matches choose from.
	 * @return The candidates, sorted high to low by confidence.
	 */
	// private static List<MatchedElement>
	// chooseBestCandidates(List<MatchedElement> candidates) {
	// Collections.sort(candidates, Collections.reverseOrder());
	// return candidates;
	// }

	interface ScoreFunction {
		float score(Collection<Snippet> testing, REDExModel ex,
				boolean allowOverMatches, boolean caseInsensitive, boolean useTier2, Class<? extends PatternAdapter> patternAdapterClass);
	}

	static class NoFalsePositives implements ScoreFunction {
		private final REDExFactory rexFactory;

		public NoFalsePositives(REDExFactory factory) {
			this.rexFactory = factory;
		}

		/**
		 * Returns 1 if there are no false positives (the test condition
		 * passed). Returns 0 if there were at least 1 false positive (test
		 * condition failed).
		 */
		@Override
		public float score(Collection<Snippet> testing, REDExModel ex,
				boolean allowOverMatches, boolean caseInsensitive, boolean useTier2, Class<? extends PatternAdapter> patternAdapterClass) {
			boolean anyFalsePositives = testing
					.parallelStream()
					.map((snippet) -> {
						CVScore cvs = rexFactory.testREDExOnSnippet(ex,
								allowOverMatches, caseInsensitive, null,
								snippet, useTier2, patternAdapterClass);
						if (cvs.getFp() > 0) {
							LOG.debug("FP on snippet: " + snippet.toString());
						}
						return Boolean.valueOf(cvs.getFp() > 0);
					}).anyMatch((fp) -> {
						return fp;
					});
			return anyFalsePositives ? 0f : 1f;
		}
	}

	private class TPFPDiff implements ScoreFunction {
		/**
		 * Returns tp - fp.
		 */
		@Override
		public float score(Collection<Snippet> testing, REDExModel ex,
				boolean allowOverMatches, boolean caseInsensitive, boolean useTier2, Class<? extends PatternAdapter> patternAdapterClass) {
			CVScore score = test(testing, ex, allowOverMatches,
					caseInsensitive, null, useTier2, patternAdapterClass);
			return (float) (score.getTp() - score.getFp());
		}
	}

	@SuppressWarnings("unused")
	private class F1Score implements ScoreFunction {
		/**
		 * Returns the f1 score.
		 */
		@Override
		public float score(Collection<Snippet> testing, REDExModel ex,
				boolean allowOverMatches, boolean caseInsensitive, boolean useTier2, Class<? extends PatternAdapter> patternAdapterClass) {
			CVScore cvs = test(testing, ex, allowOverMatches, caseInsensitive,
					null, useTier2, patternAdapterClass);
			float f1 = ((float) (2 * cvs.getTp()))
					/ ((2 * cvs.getTp()) + cvs.getFp() + cvs.getFn());
			return f1;
		}
	}

	public REDExModel buildModel(final Collection<Snippet> snippets,
			final Collection<String> labels, final boolean allowOverMatches,
			String outputTag, Path outputModelPath, boolean caseInsensitive,
			List<String> holdouts, boolean useTier2, final boolean generalizeLabeledSegments, final boolean debug, Class<? extends PatternAdapter> patternAdapterClass)
			throws IOException {
		REDExModel model = train(snippets, allowOverMatches, outputTag,
				caseInsensitive, true, holdouts, useTier2, generalizeLabeledSegments, patternAdapterClass);
		REDExModel.dump(model, outputModelPath);
		return model;
	}

	public static void main(String[] args) throws ConfigurationException,
			IOException, URISyntaxException {
		if (args.length != 3) {
			System.out
					.println("Arguments: [buildmodel|crossvalidate] [pctproc=<percent of processors to use>] <properties file>");
			return;
		}
		String op = args[0];
		int pctproc = Integer.parseInt(args[1].split("=")[1]);
		int processors = Runtime.getRuntime().availableProcessors();
		int useProcessors = (int) Math.ceil(((float)pctproc/100f) * ((float)processors));
		System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "" + useProcessors);
		System.out.println("Using " + pctproc + "% of the available processors. Available = " + processors + ", using " + useProcessors);
		String propFilename = args[2];
		Configuration conf = new PropertiesConfiguration(propFilename);
		List<Object> vttfileObjs = conf.getList("vtt.file");
		List<File> vttfiles = new ArrayList<>(vttfileObjs.size());
		for (Object vf : vttfileObjs) {
			File f = new File((String) vf);
			if (f.exists()) {
				vttfiles.add(new File((String) vf));
			} else {
				throw new FileNotFoundException((String) vf);
			}
		}
		List<Object> labelObjs = conf.getList("label");
		List<String> labels = new ArrayList<>(labelObjs.size());
		for (Object label : labelObjs) {
			labels.add((String) label);
		}
		int folds = conf.getInt("folds");
		Boolean allowOvermatches = conf.getBoolean("allow.overmatches",
				Boolean.TRUE);
		Boolean caseInsensitive = conf.getBoolean("case.insensitive",
				Boolean.TRUE);
		Boolean stopAfterFirstFold = conf.getBoolean(
				"stop.after.first.fold", Boolean.FALSE);
		Boolean shuffle = conf.getBoolean("shuffle", Boolean.TRUE);
		int limit = conf.getInt("snippet.limit", -1);
		String modelOutputFile = conf.getString("model.output.file");
		List<Object> holdoutObjs = conf.getList("holdout");
		List<String> holdouts = null;
		if (holdoutObjs == null) {
			holdouts = new ArrayList<>(0);
		} else {
			holdouts = new ArrayList<>(holdoutObjs.size());
			for (Object hoo : holdoutObjs) {
				holdouts.add(hoo.toString());
			}
		}
		Boolean useTier2 = conf.getBoolean("use.tier2", Boolean.TRUE);
		Boolean generalizeCaptureGroups = conf.getBoolean("generalize.capture.groups", true);
		Boolean useRE2J = conf.getBoolean("use.re2j", Boolean.FALSE);
		Class<? extends PatternAdapter> patternAdapterClass = null;
		if (useRE2J) {
			patternAdapterClass = RE2JPatternAdapter.class;
		} else {
			patternAdapterClass = JSEPatternAdapter.class;
		}
		
		// Set up log file
		String logFile = conf.getString("log.file");
		PrintStream oldSystemOut = null;
		if (logFile != null && logFile.trim().length() > 0) {
			PrintStream ps = new PrintStream(logFile);
			oldSystemOut = System.out;
			PrintStream newOut = new PrintStream(new TeeOutputStream(System.out, ps));
			System.setOut(newOut);
		}

		if ("crossvalidate".equalsIgnoreCase(op)) {
			REDExCrossValidator rexcv = new REDExCrossValidator(folds, allowOvermatches, caseInsensitive, holdouts, useTier2, generalizeCaptureGroups, stopAfterFirstFold, shuffle, limit, patternAdapterClass);
			List<CVResult> results = rexcv.crossValidate(vttfiles, labels, new VTTSnippetParser());

			// Display results
			int i = 0;
			for (CVResult s : results) {
				if (s != null) {
					LOG.info("\n--- Run " + (i++) + " ---\n"
							+ s.getScore().getEvaluation());
				}
			}
			CVResult aggregate = CVResult.aggregate(results);
			LOG.info("\n--- Aggregate ---\n"
					+ aggregate.getScore().getEvaluation());
			LOG.info("# Regexes Discovered: "
					+ aggregate.getRegExes().size());
			String regexOutputFile = conf.getString("regex.output.file");
			if (regexOutputFile != null) {
				try (FileWriter fw = new FileWriter(regexOutputFile)) {
					try (PrintWriter pw = new PrintWriter(fw)) {
						for (WeightedRegEx regex : aggregate.getRegExes()) {
							pw.println(regex.getRegEx());
						}
					}
				}
			}
			LOG.debug("Done cross validating");
		}
		if ("buildmodel".equalsIgnoreCase(op) || (modelOutputFile != null && modelOutputFile.trim().length() > 0)) {
			VTTReader vttr = new VTTReader();
			// get snippets
			List<Snippet> snippets = new ArrayList<>();
			for (File vttFile : vttfiles) {
				Collection<Snippet> fileSnippets = vttr.findSnippets(
						vttFile, labels, new VTTSnippetParser());
				snippets.addAll(fileSnippets);
			}
			LOG.info("Building model using " + snippets.size()
					+ " snippets from " + vttfiles + " files.\n"
					+ "\nallow.overmatches: " + allowOvermatches
					+ "\ncase.insensitive: " + caseInsensitive
					+ "\nshuffle: " + shuffle + "\nsnippet.limit: " + limit
					+ "\nmodel.output.file: " + modelOutputFile);

			// randomize the order of the snippets
			if (shuffle) {
				Collections.shuffle(snippets);
			}

			// limit the number of snippets
			if (limit > 0 && limit < snippets.size()) {
				List<Snippet> limited = new ArrayList<>(limit);
				for (int i = 0; i < limit; i++) {
					limited.add(snippets.get(i));
				}
				snippets = limited;
			}

			LOG.info("training ...");
			REDExModel rex = new REDExFactory().train(snippets,
					allowOvermatches, "m", caseInsensitive, true, holdouts,
					useTier2, generalizeCaptureGroups, patternAdapterClass);
			LOG.info("... done training.");
			LOG.info("Writing model file ...");
			Path modelFilePath = FileSystems.getDefault().getPath("",
					modelOutputFile);
			if (Files.exists(modelFilePath)) {
				Path oldModel = Paths.get(modelFilePath.toString() + "-" + System.currentTimeMillis());
				Files.move(modelFilePath, oldModel);
				LOG.info("Output model file already exists. Renaming old file to : "
						+ oldModel);
			}
			REDExModel.dump(rex, modelFilePath);
			LOG.info("... wrote model file to " + modelOutputFile);
		} else {
			System.out
					.println("Operation "
							+ op
							+ " not recognized. Must be buildmodel or crossvalidate.");
		}
		ForkJoinPool.commonPool().shutdown();
		if (oldSystemOut != null) {
			System.setOut(oldSystemOut);
		}
	}
}
