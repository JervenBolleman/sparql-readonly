package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import dev.hardwood.Hardwood;
import dev.hardwood.metadata.LogicalType;
import dev.hardwood.metadata.PhysicalType;
import dev.hardwood.metadata.RepetitionType;
import dev.hardwood.reader.RowReader;
import dev.hardwood.schema.FileSchema;
import org.eclipse.rdf4j.model.IRI;
import org.roaringbitmap.buffer.ImmutableRoaringBitmap;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class RoIriDictionary implements Closeable {

    public static final String NAMESPACE_ID = "id";
    public static final String NAMESPACE_IRI = "namespace";
    public static final String NUMBER_PREFIX = "number_prefix";
    public static final String NAME_LENGTH = "name_length";
    public static final String NUMBERS = "numbers";
    public static final String NAMES_START = "names_start";
    public static final String NAMES_END = "names_end";

    /**
     * One row per namespace, in id order. A namespace whose local names are
     * a prefix and a number has the prefix, the length of the names and a
     * serialized RoaringBitmap of the numbers. Another has the offsets in
     * the file of local names where its names start and end.
     */
    public static final FileSchema NAMESPACES_SCHEMA = FileSchema.builder("iri_namespaces")
            .addColumn(NAMESPACE_ID, PhysicalType.INT64, RepetitionType.REQUIRED)
            .addColumn(NAMESPACE_IRI, PhysicalType.BYTE_ARRAY, RepetitionType.REQUIRED, new LogicalType.StringType())
            .addColumn(NUMBER_PREFIX, PhysicalType.BYTE_ARRAY, RepetitionType.OPTIONAL, new LogicalType.StringType())
            .addColumn(NAME_LENGTH, PhysicalType.INT32, RepetitionType.OPTIONAL)
            .addColumn(NUMBERS, PhysicalType.BYTE_ARRAY, RepetitionType.OPTIONAL)
            .addColumn(NAMES_START, PhysicalType.INT64, RepetitionType.OPTIONAL)
            .addColumn(NAMES_END, PhysicalType.INT64, RepetitionType.OPTIONAL)
            .build();

    private LinearValues iris;
    private HashIndex irisIndex;
    private static final int BITS_IN_A_LONG = Long.BYTES*8;
    private final Map<Long, RoIriNamespaceDictionary> idToIriDictionaryMap;
    private final RoNamespaces namespaces;

    public Optional<String> getLocalNameFromId(long id) throws IOException {
        long namespaceId = (id >>> (BITS_IN_A_LONG-RoNamespaces.BITS_FOR_NAMESPACES));
        RoIriNamespaceDictionary namespace = idToIriDictionaryMap
                .get(namespaceId);
        return namespace.getLocalNameFromId(id);
    }

    public RoIriDictionary(
            Map<Long, RoIriNamespaceDictionary> idToIriDictionary,
            RoNamespaces namespaces) {
        super();
        this.idToIriDictionaryMap = idToIriDictionary;
        this.namespaces = namespaces;
    }

    public RoIriDictionary(RoNamespaces namespaces) {
        super();
        this.idToIriDictionaryMap = new HashMap<>();
        this.namespaces = namespaces;
    }

    /**
     * The namespace an IRI is stored under. An IRI with an empty local name,
     * one ending in its namespace separator, is stored one level up, with the
     * last path segment as local name.
     */
    public static String storedNamespace(IRI iri) {
        String namespace = iri.getNamespace();
        if (iri.getLocalName().isEmpty()) {
            namespace = namespace.substring(0, namespace.lastIndexOf("/", namespace.length() - 2) + 1);
        }
        return namespace;
    }

    /**
     * The local name an IRI is stored with, see {@link #storedNamespace(IRI)}.
     */
    public static String storedLocalName(IRI iri) {
        return iri.stringValue().substring(storedNamespace(iri).length());
    }

    public Optional<RoIri> find(IRI iri) {
        Optional<RoNamespace> has = namespaces.find(storedNamespace(iri));
        if (!has.isPresent()) {
            return Optional.empty();
        }
        final RoNamespace key = has.get();
        if (this.idToIriDictionaryMap.containsKey(key.getId())) {
            return this.idToIriDictionaryMap.get(key.getId()).find(iri);
        } else {
            return Optional.empty();
        }
    }

    public RoNamespaces getNamespaces() {

        return namespaces;

    }

    public RoIriNamespaceDictionary addPredixFollowedByBNumberDictionary(
            File offsetsFile, File lengthString, RoNamespace roNamespace)
            throws IOException {
        RoIriPrefixFollowedByNumberDictionary temp = new RoIriPrefixFollowedByNumberDictionary(
                offsetsFile, lengthString, roNamespace, this);
        idToIriDictionaryMap.put(roNamespace.getId(), temp);
        namespaces.add(roNamespace);
        return temp;
    }

    /**
     * Reads the dictionary a load wrote.
     *
     * @param namespacesFile the table of namespaces, see
     * {@link #NAMESPACES_SCHEMA}
     * @param irisFile the local names, see {@link LinearValues}
     * @param indexFile the {@link HashIndex} of the local names
     */
    public static RoIriDictionary open(File namespacesFile, File irisFile, File indexFile, Hardwood hardwood)
            throws IOException {
        RoNamespaces namespaces = new RoNamespaces();
        RoIriDictionary dictionary = new RoIriDictionary(namespaces);
        dictionary.iris = LinearValues.open(irisFile);
        dictionary.irisIndex = HashIndex.open(indexFile);
        try (ParquetTable table = ParquetTable.open(hardwood, namespacesFile)) {
            Optional<RowReader> rows = table.rows();
            if (rows.isPresent()) {
                try (RowReader row = rows.get()) {
                    while (row.hasNext()) {
                        row.next();
                        RoNamespace namespace = namespaces.add(null, row.getString(NAMESPACE_IRI));
                        if (namespace.getId() != row.getLong(NAMESPACE_ID)) {
                            throw new IOException("Namespaces out of order in " + namespacesFile);
                        }
                        if (row.isNull(NUMBER_PREFIX)) {
                            dictionary.idToIriDictionaryMap.put(namespace.getId(),
                                    new BasicRoIriNamespaceDictionary(dictionary.iris, dictionary.irisIndex, namespace,
                                            row.getLong(NAMES_START), row.getLong(NAMES_END), dictionary));
                        } else {
                            ImmutableRoaringBitmap numbers = new ImmutableRoaringBitmap(
                                    ByteBuffer.wrap(row.getBinary(NUMBERS)));
                            dictionary.idToIriDictionaryMap.put(namespace.getId(),
                                    new RoIriPrefixFollowedByNumberDictionary(namespace, row.getString(NUMBER_PREFIX),
                                            row.getInt(NAME_LENGTH), numbers, dictionary));
                        }
                    }
                }
            }
        }
        return dictionary;
    }

    public Stream<IRI> values() {
        return idToIriDictionaryMap.values().stream()
                .flatMap(RoIriNamespaceDictionary::values);

    }

    @Override
    public void close() throws IOException {
        for (RoIriNamespaceDictionary dictionary : idToIriDictionaryMap.values()) {
            dictionary.close();
        }
        if (iris != null) {
            iris.close();
            irisIndex.close();
        }
    }
}
