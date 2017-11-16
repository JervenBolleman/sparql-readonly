package sib.swiss.swissprot.sparql.ro;

import org.eclipse.rdf4j.model.Namespace;

public class RoNamespace implements Namespace {

    private static final long serialVersionUID = 1L;
    private final String prefix;
    private final String name;
    private final long id;

    public RoNamespace(String prefix, String namespace, long id) {
        this.prefix = prefix;
        this.name = namespace;
        this.id = id;
    }

    @Override
    public int compareTo(Namespace o) {
        int compare = name.compareTo(o.getName());
        if (compare == 0) {
            compare = prefix.compareTo(getPrefix());
        }
        return compare;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    public long getId() {
        return id;
    }

}
