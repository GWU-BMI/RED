package gov.va.research.red.ex;

import gov.va.research.red.CSVReader;
import gov.va.research.red.MatchedElement;
import gov.va.research.red.SnippetData;
import gov.va.vinci.krb.KrbConnectionFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.security.auth.login.LoginException;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.python.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bioc.BioCAnnotation;
import bioc.BioCCollection;
import bioc.BioCDocument;
import bioc.BioCPassage;
import bioc.io.BioCCollectionWriter;
import bioc.io.BioCFactory;

import com.google.gson.Gson;

public class REDExtractor implements Extractor {
	private static transient final Logger LOG = LoggerFactory.getLogger(REDExtractor.class);
	private static final String LS = System.getProperty("line.separator");
	private static final Set<MatchedElement> EMPTY_SET = Collections.unmodifiableSet(new HashSet<>());
	private static final int TIMEOUT = 5;
	private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MINUTES;
	private static final float DEFAULT_FRACTION_OF_PROCESSORS = 0.7f;
	private static final int PROCESSORS = Runtime.getRuntime().availableProcessors();
	private static final int USE_PROCESSORS = (int) Math.ceil(DEFAULT_FRACTION_OF_PROCESSORS * ((float)PROCESSORS));
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(USE_PROCESSORS, new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			Thread t = Executors.defaultThreadFactory().newThread(r);
			t.setDaemon(true);
			return t;
		}
	});


	private List<Collection<SnippetRegEx>> rankedSnippetRegExs;
	private String metadata;
	private boolean caseInsensitive;
	private boolean useTier2;

	@Override
	public String toString() {
		return super.toString() + " [metadata=" + metadata + "]";
	}

	public REDExtractor(Collection<SnippetRegEx> sres, boolean caseInsensitive) {
		List<Collection<SnippetRegEx>> rankedSnippetRegExs = new ArrayList<>(1);
		rankedSnippetRegExs.add(sres);
		init(rankedSnippetRegExs, null, caseInsensitive, false);
	}

	public REDExtractor(SnippetRegEx snippetRegEx, boolean caseInsensitive) {
		List<Collection<SnippetRegEx>> rankedSnippetRegExs = new ArrayList<>(1);
		rankedSnippetRegExs.add(Arrays.asList(new SnippetRegEx[] { snippetRegEx }));
		init(rankedSnippetRegExs, null, caseInsensitive, false);
	}
	
	public REDExtractor(List<Collection<SnippetRegEx>> rankedSres, String metadata, boolean caseInsensitive,
			boolean useTier2) {
		init(rankedSres, metadata, caseInsensitive, useTier2);
	}
	
	public void init(List<Collection<SnippetRegEx>> rankedSres, String metadata, boolean caseInsensitive,
			boolean useTier2) {
		this.rankedSnippetRegExs = rankedSres;
		this.metadata = metadata;
		this.caseInsensitive = caseInsensitive;
		this.useTier2 = useTier2;
	}

	@Override
	public Set<MatchedElement> extract(String target) {
		if (target == null || target.length() == 0) {
			return null;
		}
		ConcurrentMap<MatchedElement.MatchPos, MatchedElement.MatchData> returnMap = null;
		boolean tier1 = true;
		ConcurrentSkipListSet<SnippetRegEx> reject = new ConcurrentSkipListSet<>();
		
		for (Collection<SnippetRegEx> snippetREs : this.rankedSnippetRegExs) {
			if ((useTier2 || tier1) && snippetREs != null && !snippetREs.isEmpty()) {
				returnMap = snippetREs.parallelStream().flatMap((sre) -> {
					MatchFinder mf = new MatchFinder(sre, target, caseInsensitive);
					Future<Set<MatchedElement>> future = EXECUTOR.submit(mf);
					Set<MatchedElement> mes = EMPTY_SET;
					try {
						mes = future.get(TIMEOUT, TIMEOUT_UNIT);
					} catch (TimeoutException e) {
						LOG.warn("MatchFinder timed out after " + TIMEOUT + " " + TIMEOUT_UNIT + " on snippet regular expression '" + sre + "' applied to target '" + target + "'");
						reject.add(sre);
					} catch (InterruptedException | ExecutionException e) {
						try (	StringWriter sw = new StringWriter();
								PrintWriter pw = new PrintWriter(sw)) {
							e.printStackTrace(pw);
							pw.flush();
							sw.flush();
							LOG.error(sw.toString());
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					} finally {
						future.cancel(true);
					}
					return mes.parallelStream();
				}).collect(Collectors.toConcurrentMap(
						// MatchedElement.MatchData::getMatchPos
						new Function<MatchedElement, MatchedElement.MatchPos>() {
							public MatchedElement.MatchPos apply(MatchedElement me) {
								return me.getMatchPos();
							};
						},
						// MatchedElement.MatchData::getMatchData
						new Function<MatchedElement, MatchedElement.MatchData>() {
							public MatchedElement.MatchData apply(MatchedElement me) {
								return me.getMatchData();
							};
						}, new java.util.function.BinaryOperator<MatchedElement.MatchData>() {
							public MatchedElement.MatchData apply(MatchedElement.MatchData md1,
									MatchedElement.MatchData md2) {
								md1.combine(md2);
								return md1;
							};
						}));
			}
			// returnMap now contains all matches for the current tier
			tier1 = false;
			if (returnMap != null && !returnMap.isEmpty()) {
				break;
			}
		}

		// Remove regexes that caused timeouts
		this.rankedSnippetRegExs.removeAll(reject);
		if (returnMap == null || returnMap.isEmpty()) {
			return new HashSet<>(0);
		}

		Set<MatchedElement> returnSet = new HashSet<>(returnMap.size());
		for (Map.Entry<MatchedElement.MatchPos, MatchedElement.MatchData> e : returnMap.entrySet()) {
			returnSet.add(new MatchedElement(e.getKey(), e.getValue()));
		}

		return returnSet;
	}

	private class MatchedElementPosComparator implements Comparator<MatchedElement> {

		@Override
		public int compare(MatchedElement arg0, MatchedElement arg1) {
			if (arg0 == null) {
				if (arg1 == null) {
					return 0;
				} else {
					return -1;
				}
			}
			if (arg0.getStartPos() != arg1.getStartPos()) {
				return arg0.getStartPos() - arg1.getStartPos();
			}
			if (arg0.getEndPos() != arg1.getEndPos()) {
				return arg1.getEndPos() - arg0.getEndPos();
			}
			return 0;
		}

	}

	public List<Collection<SnippetRegEx>> getRankedSnippetRegExs() {
		return this.rankedSnippetRegExs;
	}

	public void setRankedSnippetRegExs(List<Collection<SnippetRegEx>> rankedSnippetRegExs) {
		this.rankedSnippetRegExs = rankedSnippetRegExs;
	}

	public String getMetadata() {
		return metadata;
	}

	public void setMetadata(String metadata) {
		this.metadata = metadata;
	}

	public List<List<String>> getRegularExpressions() {
		List<List<String>> tierRegexs = new ArrayList<>(this.rankedSnippetRegExs.size());
		for (Collection<SnippetRegEx> sres : this.rankedSnippetRegExs) {
			List<String> regexs = new ArrayList<>(sres.size());
			tierRegexs.add(regexs);
			for (SnippetRegEx sre : sres) {
				regexs.add(sre.toString());
			}
		}
		return tierRegexs;
	}

	private class MatchFinder implements Callable<Set<MatchedElement>> {
		SnippetRegEx sre;
		String target;
		boolean caseInsensitive;

		public MatchFinder(SnippetRegEx sre, String target, boolean caseInsensitive) {
			this.sre = sre;
			this.target = target;
			this.caseInsensitive = caseInsensitive;
		}

		@Override
		public Set<MatchedElement> call() {
			Set<MatchedElement> matchedElements = new HashSet<>();
			Pattern p = sre.getPattern(caseInsensitive);
			Matcher matcher = p.matcher(target);
			if (matcher.find()) {
				if (matcher.groupCount() < 1) {
					// LOG.debug("No capturing group match.\nTarget = " + target
					// + "\nPattern = " + sre.getPattern(caseInsensitive));
				} else {
					String candidateLS = matcher.group(1);
					if (candidateLS != null && !(candidateLS.length() == 0)) {
						int startPos = matcher.start(1);
						int endPos = matcher.end(1);
						Set<String> matchingRegexes = new HashSet<>(1);
						matchingRegexes.add(sre.toString());
						matchedElements.add(new MatchedElement(startPos, endPos, candidateLS, matchingRegexes,
								sre.getSensitivity()));
					}
				}
			}
			return matchedElements;
		}
	}

	/**
	 * Dumps (serializes) the REDExtractor to a file.
	 * 
	 * @param rex
	 *            The REDExtractor to dump.
	 * @param path
	 *            The path of the file to receive the dumped REDExtractor.
	 * @throws IOException
	 *             if the output file cannot be written.
	 */
	public static void dump(REDExtractor rex, Path path) throws IOException {
		Gson gson = new Gson();
		String json = gson.toJson(rex);
		Files.write(path, json.getBytes(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
	}

	/**
	 * Loads (deserializes) a REDExtractor from a file.
	 * 
	 * @param path
	 *            The path of the file containing the dumped REDExtractor.
	 * @return a REDExtractor represented in the file.
	 * @throws IOException
	 *             if the input file cannot be read.
	 */
	public static REDExtractor load(Path path) throws IOException {
		Gson gson = new Gson();
		String json = new String(Files.readAllBytes(path));
		REDExtractor rex = gson.fromJson(json, REDExtractor.class);
		return rex;
	}

	/**
	 * Loads (deserializes) a REDExtractor from a reader.
	 * 
	 * @param reader
	 *            The reader for the dumped REDExtractor.
	 * @return a REDExtractor represented by the reader.
	 * @throws IOException
	 *             if the input file cannot be read.
	 */
	public static REDExtractor load(Reader reader) throws IOException {
		try (Scanner s = new Scanner(reader)) {
			s.useDelimiter("\\Z");
			String json = s.next();
			Gson gson = new Gson();
			REDExtractor rex = gson.fromJson(json, REDExtractor.class);
			return rex;
		}
	}

	/**
	 * Main entry point for standalone execution of a REDExtractor
	 * 
	 * @param args see buildOption()
	 *             -d,--file-directory &lt;arg&gt;
	 *             -f,--file(s) &lt;arg&gt;
	 *             -j,--jdbc-url &lt;arg&gt;
	 *             -m,--model-file &lt;arg&gt;
	 *             -o,--output-file &lt;arg&gt;
	 *             -p,--precision-bias
	 *             -q,--db-query &lt;arg&gt;
	 * @throws IOException
	 *             if any of the files cannot be accessed.
	 * @throws XMLStreamException
	 *             if a problem occurs with the output xml file.
	 * @throws ParseException 
	 */
	public static void main(String[] args) throws IOException, XMLStreamException, ParseException {
		Options options = buildOptions();
		CommandLineParser parser = new DefaultParser();
		CommandLine cl = null;
		try {
			cl = parser.parse(options, args);
		} catch (ParseException e) {
			LOG.error(e.getMessage());
			HelpFormatter hf = new HelpFormatter();
			hf.printHelp("REDExtractor", options);
			return;
		}

		String[] modelFiles = cl.getOptionValues("m");
		Path[] models = new Path[modelFiles.length];
		for (int i = 0; i < modelFiles.length; i++) {
			models[i] = FileSystems.getDefault().getPath(modelFiles[i]);
		}
		Path outputFile = FileSystems.getDefault().getPath(cl.getOptionValue("o"));
		boolean useTier2 = !cl.hasOption("p");

		String fileDirStr = cl.getOptionValue("d");
		String jdbcURL = cl.getOptionValue("j");

		if ((fileDirStr == null && jdbcURL == null) || (fileDirStr != null && jdbcURL != null)) {
			LOG.error("Exactly one of the options 'd' or 'j' must be specified");
			HelpFormatter hf = new HelpFormatter();
			hf.printHelp("REDExtractor", options);
			return;
		}
		if (fileDirStr != null) {
			String[] fileStrs = cl.getOptionValues("f");
			extractFromFiles(models, outputFile, fileDirStr, fileStrs, useTier2);
		} else {
			String query = cl.getOptionValue('q');
			extractFromDB(models, jdbcURL, query, outputFile, useTier2);
		}
	}

	static void extractFromDB(Path[] models, String jdbcURLStr, String query, Path outputFile, boolean useTier2) throws IOException, XMLStreamException {
		for (Path model : models) {
			if (!Files.exists(model)) {
				throw new RuntimeException("Model file '" + model + "' was not found");
			}
		}
//		URL loginConfURL = REDExtractor.class.getClassLoader().getResource("login.conf");
//		URL krb5IniURL = REDExtractor.class.getClassLoader().getResource("krb5.ini");
		KrbConnectionFactory kcf = new KrbConnectionFactory("login.conf", "krb5.ini", "ADContext",
				"com.microsoft.sqlserver.jdbc.SQLServerDriver", jdbcURLStr, true);
		Map<String,String> docId2Text = new HashMap<>();
		try (Connection conn = kcf.getConnection();
				PreparedStatement ps = conn.prepareStatement(query);
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				String docId = rs.getString(1);
				String docText = rs.getString(2);
				docId2Text.put(docId, docText);
			}
		} catch (LoginException | IOException | SQLException e) {
			throw new RuntimeException(e);
		}
		List<REDExtractor> redexs = new ArrayList<>(models.length);
		for (Path model : models) {
			REDExtractor redex = REDExtractor.load(model);
			redex.setUseTier2(useTier2);
			redexs.add(redex);
		}
		Map<String, Collection<MatchedElement>> docMatches = docId2Text.entrySet().parallelStream().map((entry) -> {
			DocMatches dm = new DocMatches(entry.getKey(), new HashSet<MatchedElement>());
			for (REDExtractor redex : redexs) {
				Set<MatchedElement> mes = redex.extract(entry.getValue());
				if (mes != null && mes.size() > 0) {
					dm.getMatchedElements().addAll(mes);
				}
			}
			return dm;
		}).collect(Collectors.toMap((dm) -> dm.getDocumentId(), (dm) -> dm.getMatchedElements()));
		// allow memory to be reclaimed
		docId2Text = null;
		
		writeBioC(outputFile, docMatches);
	}

	/**
	 * @param outputFile
	 * @param docMatches
	 * @throws XMLStreamException
	 * @throws IOException
	 */
	static void writeBioC(Path outputFile, Map<String, Collection<MatchedElement>> docMatches) throws XMLStreamException, IOException {
		BioCCollection biocColl = new BioCCollection();
		biocColl.setDate(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'").format(new Date()));
		int annId = 0;
		for (Map.Entry<String, Collection<MatchedElement>> dm : docMatches.entrySet()) {
			BioCDocument biocDoc = new BioCDocument();
			biocColl.addDocument(biocDoc);
			biocDoc.setID(dm.getKey());
			BioCPassage biocPass = new BioCPassage();
			biocDoc.addPassage(biocPass);
			for (MatchedElement me : dm.getValue()) {
				BioCAnnotation biocAnn = new BioCAnnotation();
				biocAnn.setID(String.valueOf(annId++));
				biocAnn.setLocation(me.getStartPos(), me.getEndPos() - me.getStartPos());
				biocAnn.setText(me.getMatch());
				biocPass.addAnnotation(biocAnn);
			}
		}
		BioCFactory factory = BioCFactory.newFactory(BioCFactory.STANDARD);
		try (Writer w = new StringWriter()) {
			BioCCollectionWriter collWriter = factory.createBioCCollectionWriter(w);
			try {
				collWriter.writeCollection(biocColl);
			} finally {
				if (collWriter != null) {
					collWriter.close();
				}
			}
			w.flush();
			if (outputFile == null) {
				System.out.println(w.toString());
			} else {
				Files.write(outputFile, w.toString().getBytes());
			}
		}
	}

	private static class DocMatches {
		private String documentId;
		private Collection<MatchedElement> matchedElements;
		/**
		 * @param documentId
		 * @param matchedElements
		 */
		public DocMatches(String documentId, Collection<MatchedElement> matchedElements) {
			super();
			this.documentId = documentId;
			this.matchedElements = matchedElements;
		}
		public String getDocumentId() {
			return documentId;
		}
		public void setDocumentId(String documentId) {
			this.documentId = documentId;
		}
		public Collection<MatchedElement> getMatchedElements() {
			return matchedElements;
		}
		public void setMatchedElements(Collection<MatchedElement> matchedElements) {
			this.matchedElements = matchedElements;
		}
	}

	/**
	 * @param args
	 * @param cl
	 * @param model
	 * @param outputFile
	 * @param fileDirStr
	 * @throws IOException
	 * @throws XMLStreamException
	 */
	static void extractFromFiles(Path[] models, Path outputFile, String fileDirStr, String[] fileStrs, boolean useTier2)
			throws IOException, XMLStreamException {
		Path fileDir = FileSystems.getDefault().getPath(fileDirStr);
		for (Path model : models) {
			if (!Files.exists(model)) {
				throw new IllegalArgumentException("REDEx model file not found: " + model);
			}			
		}
		if (!Files.exists(fileDir)) {
			throw new IllegalArgumentException("file directory not found: " + fileDir);
		} else {
			if (fileStrs == null) {
				fileStrs = new String[] { "*" };
			}
			List<Path> files = new ArrayList<>(fileStrs.length);
			for (int i = 0; i < fileStrs.length; i++) {
				if (fileStrs[i].startsWith("\"") && fileStrs[i].endsWith("\"")) {
					fileStrs[i] = fileStrs[i].substring(1, fileStrs[i].length() - 1);
				}
				Files.newDirectoryStream(fileDir, fileStrs[i]).forEach(new Consumer<Path>() {
					@Override
					public void accept(Path t) {
						files.add(t);
					}
				});
			}
			if (files == null || files.size() == 0) {
				throw new IllegalArgumentException("No input files");
			}
			System.err.println("REDExtractor running using:" + LS + "\tmodel file: " + Arrays.asList(models) + LS
					+ "\tinput files: " + files.toString() + LS + "\toutput: "
					+ (outputFile == null ? "<stdout>" : outputFile));
			List<REDExtractor> redexs = new ArrayList<>(models.length);
			for (Path model : models) {
				REDExtractor redex = REDExtractor.load(model);
				redex.setUseTier2(useTier2);
				if (redex.getMetadata() == null) {
					redex.setMetadata(model.getFileName().toString());
				} else {
					redex.setMetadata(redex.getMetadata() + " [ filename = " + model.getFileName().toFile() + "]");
				}
				redexs.add(redex);
			}
			BioCCollection biocColl = new BioCCollection();
			biocColl.setDate(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'").format(new Date()));
			int annId = 0;
			for (Path file : files) {
				String contents = new String(Files.readAllBytes(file));
				BioCDocument biocDoc = new BioCDocument();
				biocColl.addDocument(biocDoc);
				biocDoc.setID(file.toString());
				BioCPassage biocPass = new BioCPassage();
				biocDoc.addPassage(biocPass);
				if (file.toString().toLowerCase().endsWith(".csv")) {
					Collection<SnippetData> sdColl = CSVReader.readSnippetData(contents, true);
					for (SnippetData sd : sdColl) {
						for (REDExtractor rex : redexs) {
							Set<MatchedElement> mes = rex.extract(sd.getSnippetText());
							for (MatchedElement me : mes) {
								BioCAnnotation biocAnn = new BioCAnnotation();
								biocAnn.setID(String.valueOf(annId++));
								biocAnn.setLocation(sd.getOffset() + me.getStartPos(),
										me.getEndPos() - me.getStartPos());
								biocAnn.setText(me.getMatch());
								biocAnn.getInfons().put("Patient ID", sd.getPatientID());
								biocAnn.getInfons().put("Document ID", sd.getDocumentID());
								biocAnn.getInfons().put("Snippet Number", sd.getSnippetNumber());
								biocPass.addAnnotation(biocAnn);
							}
						}
					}
				} else {
					for (REDExtractor rex : redexs) {
						Set<MatchedElement> mes = rex.extract(contents);
						for (MatchedElement me : mes) {
							BioCAnnotation biocAnn = new BioCAnnotation();
							biocAnn.setID(String.valueOf(annId++));
							biocAnn.setLocation(me.getStartPos(), me.getEndPos() - me.getStartPos());
							biocAnn.setText(me.getMatch());
							biocPass.addAnnotation(biocAnn);
						}
					}
				}
			}
			BioCFactory factory = BioCFactory.newFactory(BioCFactory.STANDARD);
			try (Writer w = new StringWriter()) {
				BioCCollectionWriter collWriter = factory.createBioCCollectionWriter(w);
				try {
					collWriter.writeCollection(biocColl);
				} finally {
					if (collWriter != null) {
						collWriter.close();
					}
				}
				w.flush();
				if (outputFile == null) {
					System.out.println(w.toString());
				} else {
					Files.write(outputFile, w.toString().getBytes());
				}
			}
		}
	}

	/**
	 * @return
	 */
	private static Options buildOptions() {
		Option model = new Option("m", "model-file", true, "REDEx model file. May be specified multiple times in order to use multiple models, in which case order is important. Priority decreases for each model listed. For example, if two models are given, the second one will only be used if there is no result from the first model.");
		model.setRequired(true);
		model.setArgs(Option.UNLIMITED_VALUES);
		Option outFile = new Option("o", "output-file", true, "File where output will be written");
		outFile.setRequired(true);

		// for file processing
		Option fileDir = new Option("d", "file-directory", true, "Directory containing files to process.");
		Option file = new Option("f", "file(s)", true,
				"File(s) to process. Wildcards can be used, and multiple files or file patterns can be specified if separated by a '|' character");
		file.setValueSeparator('|');
		file.setArgs(Option.UNLIMITED_VALUES);

		// for database processing
		Option jdbcURL = new Option("j", "jdbc-url", true,
				"JDBC connection URL to connect to a database containing records to be processed");
		Option query = new Option("q", "db-query", true,
				"Database query to execute in order to retrieve records to be processed. The query must return 2 values per row: Document ID and Document Text (in that order).");

		// Tier 2 biases for recall, so if useTier2 is true then we are biasing for recall. Otherwise we will bias for precision.
		// Note that the model may have been trained without a tier 2, in which case this setting will not have an effect either way.
		Option precisionBias = new Option("p", "precision-bias", false, "Bias toward precision. The REDEx model uses two tiers."
				+ " The first tier is biased for precision and the second is biased for recall."
				+ " If this option is set then tier 2 will not be used and the result will be biased for precision."
				+ " If this option is not set then tier 2 will be used, resulting in a bias for recall."
				+ " Note that the model may have been trained without a tier 2, in which case this option has no effect.");
		
		Option fractionOfProcessors = new Option("z", "fraction-of-processors", true, "Floating point number specifying the Fraction of processors to use. Defaults to " + DEFAULT_FRACTION_OF_PROCESSORS);
		fractionOfProcessors.setType(Float.class);
		
		Options options = new Options();
		options.addOption(model);
		options.addOption(outFile);
		options.addOption(fileDir);
		options.addOption(file);
		options.addOption(jdbcURL);
		options.addOption(query);
		options.addOption(precisionBias);
		options.addOption(fractionOfProcessors);
		return options;
	}

	public boolean isCaseInsensitive() {
		return caseInsensitive;
	}

	public void setCaseInsensitive(boolean caseInsensitive) {
		this.caseInsensitive = caseInsensitive;
	}

	public boolean isUseTier2() {
		return useTier2;
	}

	public void setUseTier2(boolean useTier2) {
		this.useTier2 = useTier2;
	}
}
