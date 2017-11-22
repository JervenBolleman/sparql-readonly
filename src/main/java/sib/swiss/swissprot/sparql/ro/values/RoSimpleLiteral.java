package sib.swiss.swissprot.sparql.ro.values;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import javax.xml.datatype.XMLGregorianCalendar;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.datatypes.XMLDatatypeUtil;
import org.eclipse.rdf4j.model.util.Literals;

import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;

public class RoSimpleLiteral implements RoLiteral {

    private static final long serialVersionUID = 1L;
    private final RoLiteralDict dict;
    private final long id;

    public RoSimpleLiteral(long id, RoLiteralDict roLiteralDict) {
        this.id = id;
        this.dict = roLiteralDict;
    }

    @Override
    public long getLongId() {
        return id;
    }

    @Override
    public String stringValue() {
        return dict.stringValue(id);
    }

    @Override
    public String getLabel() {
        return dict.stringValue(id);
    }

    /**
     *
     * @return the label of the literal.
     */
    @Override
    public String toString() {
        final String label = getLabel();
        StringBuilder sb = new StringBuilder(label.length() * 2);

        sb.append('"');
        sb.append(label);
        sb.append('"');

        if (Literals.isLanguageLiteral(this)) {
            sb.append('@');
            sb.append(getLanguage());
        } else {
            sb.append("^^<");
            sb.append(getDatatype().toString());
            sb.append(">");
        }

        return sb.toString();
    }

    @Override
    public Optional<String> getLanguage() {

        return dict.language(id);
    }

    @Override
    public IRI getDatatype() {
        return dict.dataType(id);
    }

    @Override
    public boolean booleanValue() {
        return XMLDatatypeUtil.parseBoolean(getLabel());
    }

    @Override
    public byte byteValue() {
        return XMLDatatypeUtil.parseByte(getLabel());
    }

    @Override
    public short shortValue() {
        return XMLDatatypeUtil.parseShort(getLabel());
    }

    @Override
    public int intValue() {
        return XMLDatatypeUtil.parseInt(getLabel());
    }

    @Override
    public long longValue() {
        return XMLDatatypeUtil.parseLong(getLabel());
    }

    @Override
    public float floatValue() {
        return XMLDatatypeUtil.parseFloat(getLabel());
    }

    @Override
    public double doubleValue() {
        return XMLDatatypeUtil.parseDouble(getLabel());
    }

    @Override
    public BigInteger integerValue() {
        return XMLDatatypeUtil.parseInteger(getLabel());
    }

    @Override
    public BigDecimal decimalValue() {
        return XMLDatatypeUtil.parseDecimal(getLabel());
    }

    @Override
    public XMLGregorianCalendar calendarValue() {
        return XMLDatatypeUtil.parseCalendar(getLabel());
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof RoSimpleLiteral) {
            return id == ((RoSimpleLiteral) object).id;
        } else if (object instanceof Literal) {
            return object.equals(this);
        } else {
            return super.equals(object);
        }
    }
}
