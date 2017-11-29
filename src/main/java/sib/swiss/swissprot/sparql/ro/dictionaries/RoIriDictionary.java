package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.orc.Reader;

import org.eclipse.rdf4j.model.IRI;

import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class RoIriDictionary {

    public static final String IRI_VALUE = "iri_value";
    private final Map<Long, RoIriNamespaceDictionary> idToIriDictionaryMap;
    private final RoNamespaces namespaces;

    public Optional<String> getLocalNameFromId(long id) throws IOException {
        long namespaceId = (id >>> 32);
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

    public Optional<RoIri> find(IRI iri) {
        Optional<RoNamespace> has = namespaces.find(iri);
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
            Reader reader, RoNamespace roNamespace)
            throws FileNotFoundException, IOException {
        BasicRoIriNamespaceDictionary temp = new BasicRoIriNamespaceDictionary(reader,
                roNamespace, this);
        idToIriDictionaryMap.put(roNamespace.getId(), temp);
        namespaces.add(roNamespace);
        return temp;

    }

    public Stream<IRI> values() {
        return idToIriDictionaryMap.values().stream()
                .flatMap(RoIriNamespaceDictionary::values);

    }

}
