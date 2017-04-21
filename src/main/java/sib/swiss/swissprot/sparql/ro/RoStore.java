package sib.swiss.swissprot.sparql.ro;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver;
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolverImpl;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.helpers.AbstractSail;

import sib.swiss.swissprot.sparql.ro.dictionaries.RoBnodeDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempBNodeDictionary;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempIriDictionary;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempLiteralDictionary;

public class RoStore extends AbstractSail {
	private RoValueFactory vf;
	private FederatedServiceResolver federatedServiceResolver = new FederatedServiceResolverImpl();
	private RoNamespaces namespaces;
	private Map<IRI, RoPredicateStore> stores;
	private final Map<RoDirectories, File> subDataDirs = new EnumMap<>(
			RoDirectories.class);
	private RoIriDictionary iriDict;
	private RoBnodeDictionary bnodeDict;
	private RoLiteralDict literalDict;

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
		try {
			reinitIriDictionaries();
			reinitPredicateStores();
		} catch (IOException e) {
			throw new SailException(e);
		}
	}

	private void reinitIriDictionaries() {
		iriDict = RoIriDictionary.load(getNamespaces());
		literalDict = RoLiteralDict.load();
		bnodeDict = RoBnodeDictionary.load();
	}

	public FederatedServiceResolver getFederatedServiceResolver() {
		return federatedServiceResolver;
	}

	public RoNamespaces getNamespaces() {
		return namespaces;
	}

	public void load(File... files)
			throws RDFParseException, RDFHandlerException, IOException {

		final TempBNodeDictionary tempBNodeDictionary = new TempBNodeDictionary(
				subDataDirs.get(RoDirectories.BNODE_DICTIONARIES));
		final TempLiteralDictionary tempLitalDictionary = new TempLiteralDictionary(
				subDataDirs.get(RoDirectories.OTHER_VALUE_DICTIONARIES));
		final TempIriDictionary tempIriDictionary = new TempIriDictionary(
				subDataDirs.get(RoDirectories.IRI_DICTIONARIES));

		for (File file : files) {
			final Optional<RDFFormat> op = Rio
					.getParserFormatForFileName(file.getName());
			if (op.isPresent()) {
				final RDFFormat format = op.get();
				final RDFParser parser = Rio.createParser(format, vf);

				parser.setRDFHandler(
						new DictionaryBuildingHandler(this, tempBNodeDictionary,
								tempIriDictionary, tempLitalDictionary));
				parser.parse(new FileReader(file), "");
			}
		}
		bnodeDict = tempBNodeDictionary.load();
		iriDict = tempIriDictionary.load();
		literalDict = tempLitalDictionary.load();
		for (File file : files) {
			final Optional<RDFFormat> op = Rio
					.getParserFormatForFileName(file.getName());
			if (op.isPresent()) {
				final RDFFormat format = op.get();
				final RDFParser parser = Rio.createParser(format, vf);

				final PredicateListBuildingHandler handler = new PredicateListBuildingHandler(
						this, bnodeDict, iriDict, literalDict, namespaces,
						subDataDirs.get(RoDirectories.PREDICATE_LISTS));
				parser.setRDFHandler(handler);
				parser.parse(new FileReader(file), "");
			}
		}
	}

	private void reinitPredicateStores()
			throws FileNotFoundException, IOException {
		for (File predicateDir : subDataDirs.get(RoDirectories.PREDICATE_LISTS)
				.listFiles()) {
			final RoPredicateStore roPredicateStore = new RoPredicateStore(
					predicateDir, iriDict);
			RoIri predicate = roPredicateStore.getPredicate();
			stores.put(predicate, roPredicateStore);
		}
	}

	public RoPredicateStore getPredicateStore(IRI predicate)
			throws FileNotFoundException, IOException {
		RoPredicateStore store = stores.get(predicate);
		if (predicate == null) {
			store = new RoPredicateStore(RoPredicateStore.initDirectory(
					subDataDirs.get(RoDirectories.PREDICATE_LISTS), predicate),
					iriDict);
			stores.put(predicate, store);
		}
		return store;
	}

	public void addPredicateStore(RoPredicateStore build) {
		stores.put(build.getPredicate(), build);
	}

}
