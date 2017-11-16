package sib.swiss.swissprot.sparql.ro.values;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleLiteral;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;

public class RoBooleanLiteral extends SimpleLiteral implements RoLiteral {

    private static final long serialVersionUID = 1L;
    private final boolean value;

    public RoBooleanLiteral(boolean value) {
        this.value = value;
    }

    @Override
    public String stringValue() {
        return Boolean.toString(value);
    }

    @Override
    public IRI getDatatype() {
        return XMLSchema.BOOLEAN;
    }

    @Override
    public long getLongId() {
        if (value) {
            return 1;
        } else {
            return 0;
        }
    }
}
