package sib.swiss.swissprot.sparql.ro;

import java.io.File;
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
		namespaces = new RoNamespaces(subDataDirs.get(RoDirectories.NAMESPACES)
				.toPath());

	}

	public FederatedServiceResolver getFederatedServiceResolver() {
		return federatedServiceResolver;
	}

	public RoNamespaces getNamespaces() {
		return namespaces;
	}

	public void load(File... files) throws RDFParseException,
			RDFHandlerException, IOException {

		final TempBNodeDictionary tempBNodeDictionary = new TempBNodeDictionary(
				subDataDirs.get(RoDirectories.BNODE_DICTIONARIES));
		final TempLiteralDictionary tempLitalDictionary = new TempLiteralDictionary(
				subDataDirs.get(RoDirectories.OTHER_VALUE_DICTIONARIES));
		final TempIriDictionary tempIriDictionary = new TempIriDictionary(
				subDataDirs.get(RoDirectories.IRI_DICTIONARIES));

		for (File file : files) {
			final Optional<RDFFormat> op = Rio.getParserFormatForFileName(file
					.getName());
			if (op.isPresent()) {
				final RDFFormat format = op.get();
				final RDFParser parser = Rio.createParser(format, vf);

				parser.setRDFHandler(new LoaderHandler(this,
						tempBNodeDictionary, tempIriDictionary,
						tempLitalDictionary));
				parser.parse(new FileReader(file), "");
			}
		}
		bnodeDict = tempBNodeDictionary.load();
		iriDict = tempIriDictionary.load();
		for (File file : files) {

		}
	}

	public RoPredicateStore getPredicateStore(IRI predicate) {
		RoPredicateStore store = stores.get(predicate);
		if (predicate == null) {
			store = new RoPredicateStore(
					subDataDirs.get(RoDirectories.PREDICATE_LISTS), predicate);
			stores.put(predicate, store);
		}
		return store;
	}

}
