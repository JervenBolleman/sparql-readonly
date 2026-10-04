package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.rdf4j.model.IRI;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class RoIriDictionary implements Closeable {

    public static final String IRI_VALUE = "iri_value";
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

    public RoIriNamespaceDictionary addBasicRoIriNamespaceDictionary(
            ParquetTable table, RoNamespace roNamespace)
            throws FileNotFoundException, IOException {
        BasicRoIriNamespaceDictionary temp = new BasicRoIriNamespaceDictionary(table,
                roNamespace, this);
        idToIriDictionaryMap.put(roNamespace.getId(), temp);
        namespaces.add(roNamespace);
        return temp;

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
    }
}
