package sib.swiss.swissprot.sparql.ro;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.orc.OrcFile;

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

import sib.swiss.swissprot.sparql.ro.dictionaries.RoBigIntegerDict;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempIntegerDictionary;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempIriDictionary;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempLiteralDictionary;

public class RoStore extends AbstractSail {

    private RoValueFactory vf;
    private final FederatedServiceResolver federatedServiceResolver;
    private RoNamespaces namespaces;
    private final Map<IRI, RoPredicateStore> stores = new HashMap<>();
    private final Map<RoDirectories, File> subDataDirs = new EnumMap<>(
            RoDirectories.class);
    private RoIriDictionary iriDict;
    private RoLiteralDict literalDict;
    private RoBigIntegerDict integerDict;
    private final Configuration conf;

    public RoStore() {
        this.federatedServiceResolver = new FederatedServiceResolverImpl();
        this.conf = new Configuration();
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
        vf = new RoValueFactory();
    }

    private void reinitIriDictionaries() throws IOException {
        iriDict = new RoIriDictionary(getNamespaces());
        final File literalsDir = subDataDirs.get(RoDirectories.OTHER_VALUE_DICTIONARIES);

        if (!literalsDir.exists()) {
            literalsDir.mkdir();
        }
        File literalFile = new File(literalsDir, RoLiteralDict.PATH_NAME);
        if (!literalFile.exists()) {
            literalFile.createNewFile();
        }
        Path literals = new Path(literalsDir.getAbsolutePath(), RoLiteralDict.PATH_NAME);
        literalDict = new RoLiteralDict(OrcFile.createReader(literals, OrcFile.readerOptions(conf)));

        final File integerDir = subDataDirs.get(RoDirectories.NUMBERIC_VALUE_DICTIONARIES);
        if (!integerDir.exists()) {
            integerDir.mkdir();
        }
        File integerFile = new File(integerDir, RoBigIntegerDict.PATH_NAME);
        if (!integerFile.exists()) {
            integerFile.createNewFile();
        }
        Path integer = new Path(integerDir.getAbsolutePath(), RoBigIntegerDict.PATH_NAME);
        integerDict = new RoBigIntegerDict(OrcFile.createReader(integer, OrcFile.readerOptions(conf)));

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

    public void load(File... files)
            throws RDFParseException, RDFHandlerException, IOException {

        final TempLiteralDictionary tempLitalDictionary = new TempLiteralDictionary(
                subDataDirs.get(RoDirectories.OTHER_VALUE_DICTIONARIES));
        final TempIriDictionary tempIriDictionary = new TempIriDictionary(
                subDataDirs.get(RoDirectories.IRI_DICTIONARIES));
        final TempIntegerDictionary tempIntegerDictionary = new TempIntegerDictionary(
                subDataDirs.get(RoDirectories.NUMBERIC_VALUE_DICTIONARIES));
        for (File file : files) {
            final Optional<RDFFormat> op = Rio
                    .getParserFormatForFileName(file.getName());
            if (op.isPresent()) {
                final RDFFormat format = op.get();
                final RDFParser parser = Rio.createParser(format, vf);

                parser.setRDFHandler(
                        new DictionaryBuildingHandler(this,
                                tempIriDictionary, tempLitalDictionary, tempIntegerDictionary));
                parser.parse(new FileReader(file), "");
            }
        }
        AtomicLong bnodeCounter = new AtomicLong();
        iriDict = tempIriDictionary.load();
        literalDict = tempLitalDictionary.load();
        integerDict = tempIntegerDictionary.load();
        final PredicateListBuildingHandler handler = new PredicateListBuildingHandler(
                this, new RoDictionaries(iriDict, literalDict, integerDict),
                subDataDirs.get(RoDirectories.PREDICATE_LISTS), bnodeCounter);
        for (File file : files) {
            final Optional<RDFFormat> op = Rio
                    .getParserFormatForFileName(file.getName());
            if (op.isPresent()) {
                final RDFFormat format = op.get();
                final RDFParser parser = Rio.createParser(format, vf);

                parser.setRDFHandler(handler);
                parser.parse(new FileReader(file), "");
            }
        }
        handler.build();
        reinitPredicateStores();
    }

    private void reinitPredicateStores()
            throws FileNotFoundException, IOException {
        stores.clear();
        RoDictionaries dictionaries = new RoDictionaries(iriDict, literalDict, integerDict);
        for (File predicateDir : subDataDirs.get(RoDirectories.PREDICATE_LISTS)
                .listFiles()) {
            final RoPredicateStore roPredicateStore = new RoPredicateStore(
                    predicateDir, dictionaries);
            RoIri predicate = roPredicateStore.getPredicate();
            stores.put(predicate, roPredicateStore);
        }
    }

    public RoPredicateStore getPredicateStore(IRI predicate)
            throws FileNotFoundException, IOException {
        RoPredicateStore store = stores.get(predicate);
        if (store == null) {
            store = new RoPredicateStore(RoPredicateStore.initDirectory(
                    subDataDirs.get(RoDirectories.PREDICATE_LISTS), predicate),
                    new RoDictionaries(iriDict, literalDict, integerDict));
            stores.put(predicate, store);
        }
        return store;
    }

    public void addPredicateStore(RoPredicateStore build) {
        stores.put(build.getPredicate(), build);
    }

    Map<IRI, RoPredicateStore> getPredicateStores() {
        return stores;
    }

}
