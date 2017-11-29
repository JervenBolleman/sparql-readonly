package sib.swiss.swissprot.sparql.ro;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.eclipse.rdf4j.model.IRI;

public class RoNamespaces implements Iterable<RoNamespace> {

    private final LinkedHashSet<RoNamespace> namespaces = new LinkedHashSet<>();

    public int size() {
        return namespaces.size();
    }

    public boolean isEmpty() {
        return namespaces.isEmpty();
    }

    public Optional<RoNamespace> find(RoNamespace key) {

        for (RoNamespace n : namespaces) {
            if (n.equals(key)) {
                return Optional.of(n);
            }
        }
        return Optional.empty();
    }

    public Optional<RoNamespace> find(IRI key) {
        final String namespace = key.getNamespace();
        for (RoNamespace n : namespaces) {
            if (n.getName().equals(namespace)) {
                return Optional.of(n);
            }
        }
        return Optional.empty();
    }

    public void add(RoNamespace value) {
        namespaces.add(value);
    }

    @Override
    public boolean equals(Object o) {
        return namespaces.equals(o);
    }

    @Override
    public int hashCode() {
        return namespaces.hashCode();
    }

    public RoNamespaces() {
    }

    public Collection<RoNamespace> getNamespaces() {

        return namespaces;
    }

    public RoNamespace getFromId(long id) {

        int intId = (int) (id >> 32);
        return namespaces.stream()
                .skip(intId)
                .findFirst()
                .get();

    }

    static RoNamespace fromSavedString(String s, int i) {
        int firstColon = s.indexOf(':');
        String prefix = s.substring(0, firstColon);
        String namespace = s.substring(firstColon + 1);
        return new RoNamespace(prefix, namespace, i);
    }

    public synchronized RoNamespace add(String prefix, String namespace) {
        int id = namespaces.size();
        final RoNamespace roNamespace = new RoNamespace(prefix, namespace, id);
        namespaces.add(roNamespace);
        return roNamespace;
    }

    @Override
    public Iterator<RoNamespace> iterator() {
        return namespaces.iterator();
    }

    public Stream<RoNamespace> stream() {
        return namespaces.stream();
    }
}
