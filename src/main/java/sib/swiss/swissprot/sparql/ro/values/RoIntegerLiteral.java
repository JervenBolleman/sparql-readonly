package sib.swiss.swissprot.sparql.ro.values;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.impl.IntegerLiteral;
import org.eclipse.rdf4j.model.impl.SimpleLiteral;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;

public class RoIntegerLiteral extends SimpleLiteral implements RoLiteral {

    private static final long serialVersionUID = 1L;
    private final long value;

    public RoIntegerLiteral(long value) {
        this.value = value;
    }

    @Override
    public String stringValue() {
        return Long.toString(value);
    }

    @Override
    public IRI getDatatype() {
        return XMLSchema.INT;
    }

    @Override
    public long getLongId() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof RoIntegerLiteral) {
            return value == ((RoIntegerLiteral) object).value;
        } else if (object instanceof IntegerLiteral) {
            return object.equals(this);
        } else if (object instanceof Literal) {
            final Literal number = (Literal) object;
            if (number.getDatatype().equals(getDatatype())) {
                return number.intValue() == value;
            }

        }
        return false;

    }

    @Override
    public String toString() {
        return '"' + value + "\"^^" + getDatatype();
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
