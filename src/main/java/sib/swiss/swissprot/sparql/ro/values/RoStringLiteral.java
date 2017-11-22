package sib.swiss.swissprot.sparql.ro.values;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.impl.SimpleLiteral;

import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;

public class RoStringLiteral extends SimpleLiteral implements RoLiteral {

    private static final long serialVersionUID = 1L;
    private final long id;
    private final RoLiteralDict dict;

    public RoStringLiteral(long id, RoLiteralDict dict) {
        this.id = id;
        this.dict = dict;
    }

    @Override
    public String stringValue() {
        return dict.stringValue(id);
    }

    @Override
    public IRI getDatatype() {
        return dict.dataType(id);
    }

    @Override
    public long getLongId() {
        return id;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof RoStringLiteral) {
            return id == ((RoStringLiteral) object).id;
        } else if (object instanceof Literal) {
            return object.equals(this);
        } else {
            return super.equals(object);
        }
    }
}
