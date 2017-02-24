package gov.va.research.red.ex;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import org.python.google.common.collect.Lists;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class REDExModel implements RegexTiers {
	private String metadata;
	private List<Collection<WeightedRegEx>> regexTiers;

	/**
	 * Default Constructor
	 */
	public REDExModel() {
		this.regexTiers = new ArrayList<>(2);
		this.regexTiers.add(new ArrayList<>());
		this.regexTiers.add(new ArrayList<>());
	}

	/**
	 * Copy Constructor
	 * @param redexModel Model to use to construct this new REDExModel.
	 */
	public REDExModel(REDExModel redexModel) {
		this(redexModel.regexTiers);
	}

	/**
	 * Constructor from tiers of regular expressions.
	 * @param regexTiers Tiered weighted regular expressions to use in the new REDExModel.
	 */
	public REDExModel(List<Collection<WeightedRegEx>> regexTiers) {
		this.regexTiers = new ArrayList<>(regexTiers.size());
		for (Collection<WeightedRegEx> tier : regexTiers) {
			this.regexTiers.add(Lists.newArrayList(tier));
		}
	}
	
	public String getMetadata() {
		return metadata;
	}

	public void setMetadata(String metadata) {
		this.metadata = metadata;
	}

	@Override
	public List<Collection<WeightedRegEx>> getRegexTiers() {
		return Collections.unmodifiableList(regexTiers);
	}
	
	/**
	 * Dumps (serializes) the REDExModel to a file.
	 * 
	 * @param redexModel
	 *            The REDExModel to dump.
	 * @param path
	 *            The path of the file to receive the dumped REDExtractor.
	 * @throws IOException
	 *             if the output file cannot be written.
	 */
	public static void dump(REDExModel redexModel, Path path) throws IOException {
		Gson gson = buildGson();
		String json = gson.toJson(redexModel);
		Files.write(path, json.getBytes(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
	}
	
	/**
	 * Dumps (serializes) the REDExModel to a file.
	 * 
	 * @param redexModel
	 *            The REDExModel to dump.
	 * @param writer
	 *            The writer to receive the dumped REDExtractor.
	 * @throws IOException
	 *             if the output file cannot be written.
	 */
	public static void dump(REDExModel redexModel, Writer writer) throws IOException {
		Gson gson = buildGson();
		String json = gson.toJson(redexModel);
		writer.write(json);
	}
	
	/**
	 * Loads (deserializes) a REDExModel from a file.
	 * 
	 * @param path
	 *            The path of the file containing the dumped REDExModel.
	 * @return a REDExModel represented in the file.
	 * @throws IOException
	 *             if the input file cannot be read.
	 */
	public static REDExModel load(Path path) throws IOException {
		Gson gson = buildGson();
		String json = new String(Files.readAllBytes(path));
		REDExModel redexModel = gson.fromJson(json, REDExModel.class);
		return redexModel;
	}

	/**
	 * Loads (deserializes) a REDExModel from a reader.
	 * 
	 * @param reader
	 *            The reader for the dumped REDExModel.
	 * @return a REDExModel represented by the reader.
	 * @throws IOException
	 *             if the input file cannot be read.
	 */
	public static REDExModel load(Reader reader) throws IOException {
		try (Scanner s = new Scanner(reader)) {
			s.useDelimiter("\\Z");
			String json = s.next();
			Gson gson = buildGson();
			REDExModel redexModel = gson.fromJson(json, REDExModel.class);
			return redexModel;
		}
	}

	static Gson buildGson() {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter(WeightedRegEx.class, new JsonSerializer<WeightedRegEx>() {
			@Override
			public JsonElement serialize(WeightedRegEx src, Type typeOfSrc, JsonSerializationContext context) {
				// Convert any non-WeightedRegexImpl's
				WeightedRegExImpl wrxi = null;
				if (src instanceof WeightedRegExImpl) {
					wrxi = ((WeightedRegExImpl)src);
				} else {
					wrxi = new WeightedRegExImpl(src.getRegEx(), src.getWeight());
				}
				return context.serialize(wrxi, WeightedRegExImpl.class);
			}			
		});
		gsonBuilder.registerTypeAdapter(WeightedRegEx.class, new JsonDeserializer<WeightedRegEx>() {
			@Override
			public WeightedRegEx deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
					throws JsonParseException {
				WeightedRegEx wrx = context.deserialize(json, WeightedRegExImpl.class);
				return wrx;
			}
		});
		return gsonBuilder.create();
	}
}
