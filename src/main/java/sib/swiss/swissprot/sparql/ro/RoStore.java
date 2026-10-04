package sib.swiss.swissprot.sparql.ro;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

		for (RoDirectories dir : RoDirectories.values()) {
			File subDataDir = new File(dataDir, dir.getDirectoryName());
			subDataDirs.put(dir, subDataDir);
			if (!subDataDir.exists()) {
				if (!subDataDir.mkdirs()) {
					throw new SailException("SubDataDirectory:" + subDataDir
							+ " does not exist and can not be created");
				}
			}
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

	private void reinitIriDictionaries() throws IOException {
		iriDict = new RoIriDictionary(getNamespaces());
		final File literalsDir = subDataDirs
				.get(RoDirectories.OTHER_VALUE_DICTIONARIES);

		if (!literalsDir.exists()) {
			literalsDir.mkdir();
		}
		literalDict = new RoLiteralDict(ParquetTable.open(hardwood,
				new File(literalsDir, RoLiteralDict.PATH_NAME)));

		final File integerDir = subDataDirs
				.get(RoDirectories.NUMBERIC_VALUE_DICTIONARIES);
		if (!integerDir.exists()) {
			integerDir.mkdir();
		}
		integerDict = new RoBigIntegerDict(ParquetTable.open(hardwood,
				new File(integerDir, RoBigIntegerDict.PATH_NAME)));

		File bnodesDir = subDataDirs.get(RoDirectories.BNODE_DICTIONARIES);
		if (!bnodesDir.exists()) {
			bnodesDir.mkdir();
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
		final File stagingFile = new File(getDataDir(), "staged-triples");
		try (SpillContext spill = new SpillContext(loadMemory, new File(getDataDir(), "spill"),
				hardwood, true)) {
			final TempLiteralDictionary literals = new TempLiteralDictionary(
					subDataDirs.get(RoDirectories.OTHER_VALUE_DICTIONARIES), hardwood, spill);
			final TempIriDictionary iris = new TempIriDictionary(
					subDataDirs.get(RoDirectories.IRI_DICTIONARIES), hardwood, spill);
			final TempIntegerDictionary integers = new TempIntegerDictionary(
					subDataDirs.get(RoDirectories.NUMBERIC_VALUE_DICTIONARIES), hardwood, spill);
			try (StagedTriples staged = new StagedTriples(stagingFile)) {
				parse(files, iris, literals, integers, staged);
			}

			closeDictionaries();
			iriDict = iris.load();
			literalDict = literals.load();
			integerDict = integers.load();

			for (RoPredicateStore.Builder builder : storeStaged(stagingFile, iris, literals, integers)) {
				addPredicateStore(builder.build());
			}
		} finally {
			if (stagingFile.exists() && !stagingFile.delete()) {
				LOGGER.warning("Could not delete " + stagingFile);
			}
		}
		reinitPredicateStores();
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
		for (File predicateDir : subDataDirs.get(RoDirectories.PREDICATE_LISTS)
				.listFiles()) {
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

	Map<IRI, RoPredicateStore> getPredicateStores() {
		return stores;
	}

}
