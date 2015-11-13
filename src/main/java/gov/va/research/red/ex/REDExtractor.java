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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	private final List<String> holdouts;
	
	public REDExtractor(Collection<SnippetRegEx> sres, boolean caseInsensitive, List<String> holdouts) {
		this.rankedSnippetRegExs = new ArrayList<>(1);
		this.rankedSnippetRegExs.add(sres);
		this.caseInsensitive = caseInsensitive;
		this.holdouts = holdouts;
		this.useTier2 = false;
	}
	
	public REDExtractor(List<Collection<SnippetRegEx>> rankedSres, String metadata, boolean caseInsensitive, List<String> holdouts, boolean useTier2) {
		this.rankedSnippetRegExs = rankedSres;
		this.metadata = metadata;
		this.caseInsensitive = caseInsensitive;
		this.holdouts = holdouts;
		this.useTier2 = useTier2;
	}

	public REDExtractor(SnippetRegEx snippetRegEx, boolean caseInsensitive, List<String> holdouts) {
		this.rankedSnippetRegExs = new ArrayList<>(1);
		this.rankedSnippetRegExs.add(Arrays.asList(new SnippetRegEx[] { snippetRegEx }));
		this.caseInsensitive = caseInsensitive;
		this.holdouts = holdouts;
		this.useTier2 = false;
	}

	@Override
	public List<MatchedElement> extract(String target) {
		if(target == null || target.length() == 0) {
			return null;
		}
		ConcurrentHashMap<MatchedElement, Double> returnMap = null;
		boolean firstSnippetRE = true; 
		for (Collection<SnippetRegEx> snippetREs : this.rankedSnippetRegExs) {
			if((useTier2 || firstSnippetRE) && snippetREs != null && !snippetREs.isEmpty()) {
				returnMap = snippetREs.parallelStream().map((sre) -> {
					MatchFinder mf = new MatchFinder(sre, target, caseInsensitive);
					Set<MatchedElement> mes = mf.call();
					return mes;
				}).reduce(new ConcurrentHashMap<MatchedElement, Double>(), (s1, s2) -> {
					for (MatchedElement me : s2) {
						Double confidence = Double.valueOf(me.getConfidence());
						me.setConfidence(0d);
						Double conf = s1.get(me);
						if (conf == null) {
							s1.put(me,  confidence);
						} else {
							s1.put(me, conf + confidence);
						}
					}
					return s1;
				}, (s1, s2) -> {
					for (Map.Entry<MatchedElement, Double> mee : s2.entrySet()) {
						MatchedElement me = mee.getKey();
						Double confidence = mee.getValue();
						Double conf = s1.get(me);
						if (conf == null) {
							s1.put(me,  confidence);
						} else {
							s1.put(me, conf + confidence);
						}
					}
					return s1;
				});
			}
			firstSnippetRE = false;
			if (returnMap != null && !returnMap.isEmpty()) {
				break;
			}
		}
		if(returnMap == null || returnMap.isEmpty()) {
			return null;
		}
		ConcurrentLinkedQueue<MatchedElement> returnList = returnMap.entrySet().parallelStream().reduce(new ConcurrentLinkedQueue<>(), (l, e) -> {
			e.getKey().setConfidence(e.getValue());
			l.add(e.getKey());
			return l;
		}, (l1, l2) -> {
			if (l1 != l2) {
				l1.addAll(l2);
			}
			return l1;
		});
		return new ArrayList<>(returnList);
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
			//LOG.debug("Pattern: " + sre.toString());
			Pattern p = sre.getPattern(caseInsensitive);
			Matcher matcher = p.matcher(target);
			if(matcher.find()) {
				if (matcher.groupCount() < 1) {
					LOG.error("No capturing group match.\nTarget = " + target + "\nPattern = " + sre.getPattern(caseInsensitive));
				} else {
					String candidateLS = matcher.group(1);
					if(candidateLS != null && !candidateLS.equals("")){
						int startPos = target.indexOf(candidateLS);
						int endPos = startPos + candidateLS.length();
						matchedElements.add(new MatchedElement(startPos, endPos, candidateLS, sre.toString(), sre.getSensitivity()));
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
	 * @throws IOException
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
	 * @throws IOException
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
	 * @throws IOException
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
						List<MatchedElement> mes = rex.extract(contents);
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

