package sib.swiss.swissprot.sparql.ro.values;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.base.CoreDatatype;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.impl.IntegerLiteral;
import org.eclipse.rdf4j.model.impl.SimpleLiteral;
import org.eclipse.rdf4j.model.vocabulary.XSD;

public class RoIntegerLiteral extends SimpleLiteral implements RoLiteral {

    private static final long serialVersionUID = 1L;
    private final long value;

    public RoIntegerLiteral(long value) {
        // the inherited value methods read the label
        super(Long.toString(value), XSD.INT, CoreDatatype.XSD.INT);
        this.value = value;
    }

    @Override
    public String stringValue() {
        return Long.toString(value);
    }

    @Override
    public String getLabel() {
        return stringValue();
    }

    @Override
    public IRI getDatatype() {
        return XSD.INT;
    }

    @Override
    public CoreDatatype getCoreDatatype() {
        return CoreDatatype.from(getDatatype());
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
        return "\"" + value + "\"^^<" + getDatatype() + ">";
    }

    @Override
    public int hashCode() {
        return stringValue().hashCode();
    }
}
