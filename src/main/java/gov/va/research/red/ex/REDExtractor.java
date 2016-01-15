package gov.va.research.red.ex;

import gov.va.research.red.MatchedElement;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

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
	
	private List<Collection<SnippetRegEx>> rankedSnippetRegExs;
	private String metadata;
	private final boolean caseInsensitive;
	private final boolean useTier2;
	
	public REDExtractor(Collection<SnippetRegEx> sres, boolean caseInsensitive) {
		this.rankedSnippetRegExs = new ArrayList<>(1);
		this.rankedSnippetRegExs.add(sres);
		this.caseInsensitive = caseInsensitive;
		this.useTier2 = false;
	}
	
	public REDExtractor(List<Collection<SnippetRegEx>> rankedSres, String metadata, boolean caseInsensitive, boolean useTier2) {
		this.rankedSnippetRegExs = rankedSres;
		this.metadata = metadata;
		this.caseInsensitive = caseInsensitive;
		this.useTier2 = useTier2;
	}

	public REDExtractor(SnippetRegEx snippetRegEx, boolean caseInsensitive) {
		this.rankedSnippetRegExs = new ArrayList<>(1);
		this.rankedSnippetRegExs.add(Arrays.asList(new SnippetRegEx[] { snippetRegEx }));
		this.caseInsensitive = caseInsensitive;
		this.useTier2 = false;
	}

	@Override
	public Set<MatchedElement> extract(String target) {
		if(target == null || target.length() == 0) {
			return null;
		}
		ConcurrentMap<MatchedElement.MatchPos, MatchedElement.MatchData> returnMap = null;
		boolean tier1 = true; 
		for (Collection<SnippetRegEx> snippetREs : this.rankedSnippetRegExs) {
			if((useTier2 || tier1) && snippetREs != null && !snippetREs.isEmpty()) {
				returnMap = snippetREs.parallelStream().flatMap((sre) -> {
					MatchFinder mf = new MatchFinder(sre, target, caseInsensitive);
					Set<MatchedElement> mes = mf.call();
					return mes.parallelStream();
				}).collect(Collectors.toConcurrentMap(
						// MatchedElement.MatchData::getMatchPos
						new Function<MatchedElement,MatchedElement.MatchPos>() {
							public MatchedElement.MatchPos apply(MatchedElement me) {
								return me.getMatchPos();
							};
						},
						// MatchedElement.MatchData::getMatchData
						new Function<MatchedElement,MatchedElement.MatchData>() {
							public MatchedElement.MatchData apply(MatchedElement me) {
								return me.getMatchData();
							};
						},
						new java.util.function.BinaryOperator<MatchedElement.MatchData>() {
							public MatchedElement.MatchData apply(MatchedElement.MatchData md1, MatchedElement.MatchData md2) {
								md1.combine(md2);
								return md1;
							};
						}
					)
				);
			}
			// returnMap now contains all matches for the current tier
			tier1 = false;
			if (returnMap != null && !returnMap.isEmpty()) {
				break;
			}
		}
		if(returnMap == null || returnMap.isEmpty()) {
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

	public List<String> getRegularExpressions() {
		List<String> regexs = new ArrayList<>(this.rankedSnippetRegExs.size());
		for (Collection<SnippetRegEx> sres : this.rankedSnippetRegExs) {
			for (SnippetRegEx sre : sres) {
				regexs.add(sre.toString());
			}
		}
		return regexs;
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
			if(matcher.find()) {
				if (matcher.groupCount() < 1) {
					LOG.error("No capturing group match.\nTarget = " + target + "\nPattern = " + sre.getPattern(caseInsensitive));
				} else {
					String candidateLS = matcher.group(1);
					if(candidateLS != null && !(candidateLS.length() == 0)){
						int startPos = matcher.start(1);
						int endPos = matcher.end(1);
						Set<String> matchingRegexes = new HashSet<>(1);
						matchingRegexes.add(sre.toString());
						matchedElements.add(new MatchedElement(startPos, endPos, candidateLS, matchingRegexes, sre.getSensitivity()));
					}
				}
			}
			return matchedElements;
		}
	}

	/**
	 * Dumps (serializes) the REDExtractor to a file.
	 * @param rex The REDExtractor to dump.
	 * @param path The path of the file to receive the dumped REDExtractor.
	 * @throws IOException if the output file cannot be written.
	 */
	public static void dump(REDExtractor rex, Path path) throws IOException {
		Gson gson = new Gson();
		String json = gson.toJson(rex);
		Files.write(path, json.getBytes(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
	}
	
	/**
	 * Loads (deserializes) a REDExtractor from a file.
	 * @param path The path of the file containing the dumped REDExtractor.
	 * @return a REDExtractor represented in the file.
	 * @throws IOException if the input file cannot be read.
	 */
	public static REDExtractor load(Path path) throws IOException {
		Gson gson = new Gson();
		String json = new String(Files.readAllBytes(path));
		REDExtractor rex = gson.fromJson(json, REDExtractor.class);
		return rex;
	}

	/**
	 * Loads (deserializes) a REDExtractor from a reader.
	 * @param reader The reader for the dumped REDExtractor.
	 * @return a REDExtractor represented by the reader.
	 * @throws IOException if the input file cannot be read.
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
	 * @param args program arguments: &lt;REDEx model file&gt; &lt;file dir&gt; [file glob | file ] ...
	 * @throws IOException if any of the files cannot be accessed.
	 * @throws XMLStreamException if a problem occurs with the output xml file.
	 */
	public static void main(String[] args) throws IOException, XMLStreamException {
		if (args.length < 2) {
			System.err.println("Usage: REDExtractor <REDEx model file> <file dir> [file glob | file ] ...");
		} else {
			Path model = FileSystems.getDefault().getPath(args[0]);
			if (!Files.exists(model)) {
				System.err.println("REDEx model file not found: " + args[0]);
			} else {
				Path fileDir = FileSystems.getDefault().getPath(args[1]);
				if (!Files.exists(fileDir)) {
					System.err.println("file directory not found: " + args[1]);
				} else {
					List<Path> files = new ArrayList<>(args.length);
					for (int i = 2; i < args.length; i++) {
						Files.newDirectoryStream(fileDir, args[i]).forEach(new Consumer<Path>() {
							@Override
							public void accept(Path t) {
								files.add(t);
							}
						});
					}
					REDExtractor rex = REDExtractor.load(model);
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
						Set<MatchedElement> mes = rex.extract(contents);
						for (MatchedElement me : mes) {
							BioCAnnotation biocAnn = new BioCAnnotation();
							biocAnn.setID(String.valueOf(annId++));
							biocAnn.setLocation(me.getStartPos(), me.getEndPos() - me.getStartPos());
							biocAnn.setText(me.getMatch());
							biocPass.addAnnotation(biocAnn);
						}
					}
					BioCFactory factory = BioCFactory.newFactory(BioCFactory.STANDARD);
					try (Writer w = new OutputStreamWriter(System.out)) {
						BioCCollectionWriter collWriter = factory.createBioCCollectionWriter(w);
						try {
							collWriter.writeCollection(biocColl);
						} finally {
							if (collWriter != null) {
								collWriter.close();
							}
						}
					}
				}
			}
		}
	}
}

