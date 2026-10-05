package sib.swiss.swissprot.sparql.ro;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import dev.hardwood.Hardwood;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver;
import org.eclipse.rdf4j.repository.sparql.federation.SPARQLServiceResolver;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.helpers.AbstractSail;

import sib.swiss.swissprot.sparql.ro.dictionaries.RoBigIntegerDict;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;
import sib.swiss.swissprot.sparql.ro.values.RoBigIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoBooleanLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;
import sib.swiss.swissprot.sparql.ro.values.RoSimpleLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoValue;
import sib.swiss.swissprot.sparql.temporary.dictionaries.SpillContext;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempIntegerDictionary;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempIriDictionary;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempLiteralDictionary;

public class RoStore extends AbstractSail {

	private static final Logger LOGGER = Logger.getLogger(RoStore.class.getName());

	private RoValueFactory vf;
	private final FederatedServiceResolver federatedServiceResolver;
	private RoNamespaces namespaces;
	private final Map<IRI, RoPredicateStore> stores = new HashMap<>();
	private final Map<RoDirectories, File> subDataDirs = new EnumMap<>(
			RoDirectories.class);
	private RoIriDictionary iriDict;
	private RoLiteralDict literalDict;
	private RoBigIntegerDict integerDict;
	protected Hardwood hardwood;
	/**
	 * How much memory the distinct values may take while loading, before they
	 * spill to disk.
	 */
	private long loadMemory = Runtime.getRuntime().maxMemory() / 4;

	public RoStore() {
		this.federatedServiceResolver = new SPARQLServiceResolver();
	}

	@Override
	public boolean isWritable() throws SailException {
		return false;
	}

	@Override
	public RoValueFactory getValueFactory() {
		return vf;
	}

	@Override
	protected void shutDownInternal() throws SailException {
		try {
			closeReaders();
		} catch (IOException e) {
			throw new SailException(e);
		} finally {
			if (hardwood != null) {
				hardwood.close();
				hardwood = null;
			}
		}
	}

	private void closeReaders() throws IOException {
		for (RoPredicateStore store : stores.values()) {
			store.close();
		}
		stores.clear();
		closeDictionaries();
	}

	private void closeDictionaries() throws IOException {
		if (iriDict != null) {
			iriDict.close();
		}
		if (literalDict != null) {
			literalDict.close();
		}
		if (integerDict != null) {
			integerDict.close();
		}
	}

	@Override
	protected SailConnection getConnectionInternal() throws SailException {
		return new RoConnection(this);
	}

	@Override
	protected void initializeInternal() throws SailException {
		final File dataDir = getDataDir();
		if (!dataDir.exists()) {
			if (!dataDir.mkdirs()) {
				throw new SailException("DataDirectory:" + dataDir
						+ " does not exist and can not be created");
			}
		}

		// created by loading when it writes into them
		for (RoDirectories dir : RoDirectories.values()) {
			subDataDirs.put(dir, new File(dataDir, dir.getDirectoryName()));
		}
		namespaces = new RoNamespaces();
		hardwood = Hardwood.create();
		try {
			reinitIriDictionaries();
			reinitPredicateStores();
		} catch (IOException e) {
			throw new SailException(e);
		}
		vf = new RoValueFactory();
	}

	/**
	 * Opens the dictionaries a load wrote into the data directory, empty ones
	 * if there are none.
	 */
	private void reinitIriDictionaries() throws IOException {
		File namespacesDir = subDataDirs.get(RoDirectories.NAMESPACES);
		File namespacesFile = new File(namespacesDir, TempIriDictionary.NAMESPACES_FILE);
		if (namespacesFile.exists()) {
			iriDict = RoIriDictionary.open(namespacesFile,
					new File(subDataDirs.get(RoDirectories.IRI_DICTIONARIES), TempIriDictionary.IRIS_FILE),
					hardwood);
		} else {
			iriDict = new RoIriDictionary(new RoNamespaces());
		}
		literalDict = new RoLiteralDict(ParquetTable.open(hardwood,
				new File(subDataDirs.get(RoDirectories.OTHER_VALUE_DICTIONARIES), RoLiteralDict.PATH_NAME)));
		integerDict = new RoBigIntegerDict(ParquetTable.open(hardwood,
				new File(subDataDirs.get(RoDirectories.NUMBERIC_VALUE_DICTIONARIES), RoBigIntegerDict.PATH_NAME)));
		readPrefixes(new File(namespacesDir, PREFIXES_FILE));
	}

	/**
	 * The prefixes declared in the loaded files, a line of prefix, tab,
	 * namespace each.
	 */
	private static final String PREFIXES_FILE = "prefixes";

	private void readPrefixes(File file) throws IOException {
		if (!file.exists()) {
			return;
		}
		for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
			int tab = line.indexOf('\t');
			if (tab >= 0) {
				namespaces.add(line.substring(0, tab), line.substring(tab + 1));
			}
		}
	}

	private void writePrefixes(File file) throws IOException {
		List<String> lines = new ArrayList<>();
		for (RoNamespace namespace : namespaces) {
			if (namespace.getPrefix() != null) {
				lines.add(namespace.getPrefix() + '\t' + namespace.getName());
			}
		}
		if (lines.isEmpty()) {
			Files.deleteIfExists(file.toPath());
		} else {
			Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
		}
	}

	/**
	 * Removes the directories of the store that a load left empty.
	 */
	private void deleteEmptyDirectories() throws IOException {
		for (File dir : subDataDirs.values()) {
			String[] files = dir.list();
			if (files != null && files.length == 0) {
				Files.delete(dir.toPath());
			}
		}
	}

	public FederatedServiceResolver getFederatedServiceResolver() {
		return federatedServiceResolver;
	}

	public RoNamespaces getNamespaces() {
		return namespaces;
	}

	/**
	 * @param bytes the memory the distinct values of a load may take before
	 * they are spilled to disk, by default a quarter of the maximum heap
	 */
	public void setLoadMemory(long bytes) {
		this.loadMemory = bytes;
	}

	/**
	 * Loads the files in one parse. Values get provisional ids, the triples
	 * are staged with them, and once the dictionaries are sorted and written
	 * the staged triples are stored with their final ids.
	 *
	 * @see docs/loading.md
	 */
	public void load(File... files)
			throws RDFParseException, RDFHandlerException, IOException {
		for (File dir : subDataDirs.values()) {
			Files.createDirectories(dir.toPath());
		}
		final File stagingFile = new File(getDataDir(), "staged-triples");
		try (SpillContext spill = new SpillContext(loadMemory, new File(getDataDir(), "spill"),
				hardwood, true)) {
			final TempLiteralDictionary literals = new TempLiteralDictionary(
					subDataDirs.get(RoDirectories.OTHER_VALUE_DICTIONARIES), hardwood, spill);
			final TempIriDictionary iris = new TempIriDictionary(
					subDataDirs.get(RoDirectories.IRI_DICTIONARIES),
					subDataDirs.get(RoDirectories.NAMESPACES), hardwood, spill);
			final TempIntegerDictionary integers = new TempIntegerDictionary(
					subDataDirs.get(RoDirectories.NUMBERIC_VALUE_DICTIONARIES), hardwood, spill);
			long phase = System.nanoTime();
			try (StagedTriples staged = new StagedTriples(stagingFile)) {
				parse(files, iris, literals, integers, staged);
			}
			phase = logPhase("Parsed and staged the statements", phase);

			closeDictionaries();
			iriDict = iris.load();
			literalDict = literals.load();
			integerDict = integers.load();
			phase = logPhase("Wrote the dictionaries", phase);

			Collection<RoPredicateStore.Builder> builders = storeStaged(stagingFile, iris, literals, integers);
			phase = logPhase("Remapped the staged triples to the triple lists", phase);
			for (RoPredicateStore.Builder builder : builders) {
				addPredicateStore(builder.build());
			}
			logPhase("Sorted and wrote the triple lists", phase);
		} finally {
			if (stagingFile.exists() && !stagingFile.delete()) {
				LOGGER.warning("Could not delete " + stagingFile);
			}
		}
		writePrefixes(new File(subDataDirs.get(RoDirectories.NAMESPACES), PREFIXES_FILE));
		deleteEmptyDirectories();
		reinitPredicateStores();
	}

	private static long logPhase(String phase, long since) {
		long now = System.nanoTime();
		LOGGER.log(java.util.logging.Level.INFO, "{0} in {1} s",
				new Object[]{phase, Math.round((now - since) / 1e8) / 10.0});
		return now;
	}

	private void parse(File[] files, TempIriDictionary iris, TempLiteralDictionary literals,
			TempIntegerDictionary integers, StagedTriples staged) throws IOException {
		AtomicLong bnodeCounter = new AtomicLong();
		for (File file : files) {
			final Optional<RDFFormat> format = Rio.getParserFormatForFileName(file.getName());
			if (format.isEmpty()) {
				LOGGER.warning("Skipping " + file + ", not a known RDF format");
				continue;
			}
			final RDFParser parser = Rio.createParser(format.get(), vf);
			parser.setRDFHandler(new LoadingHandler(getNamespaces(), iris, literals, integers,
					staged, bnodeCounter));
			try (InputStream in = new BufferedInputStream(new FileInputStream(file), 1 << 20)) {
				parser.parse(in, "");
			}
		}
	}

	/**
	 * Reads the staged triples, in the order they were parsed, and adds them
	 * with their final ids to the builder for their predicate.
	 */
	private Collection<RoPredicateStore.Builder> storeStaged(File stagingFile,
			TempIriDictionary iris, TempLiteralDictionary literals,
			TempIntegerDictionary integers) throws IOException {
		final RoDictionaries dictionaries = new RoDictionaries(iriDict, literalDict, integerDict);
		final File predicateLists = subDataDirs.get(RoDirectories.PREDICATE_LISTS);
		final Map<Long, RoPredicateStore.Builder> builders = new HashMap<>();
		// A context is a key of the graph bitmaps: keeping its instance keeps
		// its hash, which costs a dictionary read to compute.
		final Map<Long, RoResource> iriContexts = new HashMap<>();
		final Remap remap = new Remap(iris, literals, integers);
		StagedTriples.read(hardwood, stagingFile, (subjectKind, subject, predicate, objectKind, object,
				contextKind, context) -> {
			RoPredicateStore.Builder builder = builders.get(predicate);
			if (builder == null) {
				RoIri roPredicate = new RoIri(iris.finalId(predicate), iriDict);
				builder = new RoPredicateStore.Builder(
						RoPredicateStore.initDirectory(predicateLists, roPredicate),
						roPredicate, dictionaries, hardwood);
				builders.put(predicate, builder);
			}
			RoResource graph = null;
			if (contextKind == StagedTriples.Kind.IRI) {
				graph = iriContexts.computeIfAbsent(context, c -> (RoResource) remap.value(contextKind, c));
			} else if (contextKind != StagedTriples.Kind.NONE) {
				graph = (RoResource) remap.value(contextKind, context);
			}
			builder.add((RoResource) remap.value(subjectKind, subject),
					remap.value(objectKind, object), graph);
		});
		return builders.values();
	}

	/**
	 * Turns a staged kind and id into the value with its final id.
	 */
	private final class Remap {

		private final TempIriDictionary iris;
		private final TempLiteralDictionary literals;
		private final TempIntegerDictionary integers;

		private Remap(TempIriDictionary iris, TempLiteralDictionary literals,
				TempIntegerDictionary integers) {
			this.iris = iris;
			this.literals = literals;
			this.integers = integers;
		}

		/**
		 * Big integers are rare and repeat: read from the dictionary, with a
		 * cache.
		 */
		private final Map<Long, RoBigIntegerLiteral> bigIntegers = new LinkedHashMap<>(1024, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, RoBigIntegerLiteral> eldest) {
				return size() > 10_000;
			}
		};

		private RoBigIntegerLiteral bigInteger(long finalId) {
			RoBigIntegerLiteral literal = bigIntegers.get(finalId);
			if (literal == null) {
				try {
					literal = integerDict.getFromId(finalId);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
				bigIntegers.put(finalId, literal);
			}
			return literal;
		}

		RoValue value(StagedTriples.Kind kind, long id) {
			return switch (kind) {
				case IRI -> new RoIri(iris.finalId(id), iriDict);
				case BNODE -> new RoBnode(id);
				case LITERAL -> new RoSimpleLiteral(literals.finalId(id), literalDict);
				case INT -> new RoIntegerLiteral(id);
				case BOOLEAN -> new RoBooleanLiteral(id != 0);
				case INTEGER -> new RoBigIntegerLiteral(id);
				case BIG_INTEGER -> bigInteger(integers.finalId(id));
				case NONE -> throw new IllegalArgumentException("No value");
			};
		}
	}

	private void reinitPredicateStores()
			throws FileNotFoundException, IOException {
		for (RoPredicateStore store : stores.values()) {
			store.close();
		}
		stores.clear();
		RoDictionaries dictionaries = new RoDictionaries(iriDict, literalDict,
				integerDict);
		File[] predicateDirs = subDataDirs.get(RoDirectories.PREDICATE_LISTS).listFiles();
		for (File predicateDir : predicateDirs == null ? new File[0] : predicateDirs) {
			final RoPredicateStore roPredicateStore = new RoPredicateStore(
					predicateDir, dictionaries, hardwood);
			RoIri predicate = roPredicateStore.getPredicate();
			stores.put(predicate, roPredicateStore);
		}
	}

	/**
	 * @return the store for this predicate or null if there are no triples
	 * with it.
	 */
	public RoPredicateStore getPredicateStore(IRI predicate) {
		return stores.get(predicate);
	}

	@Override
	protected void connectionClosed(SailConnection connection) {
		// widened to the package for RoConnection
		super.connectionClosed(connection);
	}

	public void addPredicateStore(RoPredicateStore build) {
		stores.put(build.getPredicate(), build);
	}

	RoDictionaries dictionaries() {
		return new RoDictionaries(iriDict, literalDict, integerDict);
	}

	Map<IRI, RoPredicateStore> getPredicateStores() {
		return stores;
	}

}
