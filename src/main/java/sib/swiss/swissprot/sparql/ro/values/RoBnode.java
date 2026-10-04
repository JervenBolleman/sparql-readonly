package sib.swiss.swissprot.sparql.ro.values;

import org.eclipse.rdf4j.model.BNode;

public class RoBnode implements BNode, RoResource {

    private static final long serialVersionUID = 1L;

    private final long id;

    public RoBnode(long id) {
        super();
        this.id = id;
    }

    @Override
    public String stringValue() {
        return "#_" + getLongId();
    }

    @Override
    public String getID() {
        return String.valueOf(id);
    }

    @Override
    public long getLongId() {
        return id;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object instanceof RoBnode) {
            return id == ((RoBnode) object).id;
        } else if (object instanceof BNode) {
            return getID().equals(((BNode) object).getID());
        }
        return false;
    }

    /**
     * As {@link BNode#hashCode()} requires, the hash of the id.
     */
    @Override
    public int hashCode() {
        return getID().hashCode();
    }

    @Override
    public String toString() {
        return "_:" + getID();
    }

    public static RoBnode fromLongId(long id) {
        return new RoBnode(id);
    }
}
