package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.hardwood.metadata.LogicalType;
import dev.hardwood.metadata.PhysicalType;
import dev.hardwood.metadata.RepetitionType;
import dev.hardwood.reader.FilterPredicate;
import dev.hardwood.schema.FileSchema;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoSimpleLiteral;

public class RoLiteralDict extends RoDictionary<RoLiteral, Literal> {

    public static final String PATH_NAME = "literals";
    public static final String LABEL = "label";
    public static final String DATATYPE_ID = "datatype_id";
    public static final String LANGUAGE = "language";

    private static final IRI[] DATA_TYPES = new IRI[]{XSD.ANYURI,
        XSD.BASE64BINARY, XSD.BOOLEAN, XSD.BYTE,
        XSD.DATE, XSD.DATETIME, XSD.DAYTIMEDURATION,
        XSD.DECIMAL, XSD.DOUBLE, XSD.DURATION,
        XSD.ENTITIES, XSD.ENTITY, XSD.FLOAT,
        XSD.GDAY, XSD.GMONTH, XSD.GMONTHDAY,
        XSD.GYEAR, XSD.GYEARMONTH, XSD.HEXBINARY,
        XSD.ID, XSD.IDREF, XSD.IDREFS, XSD.INT,
        XSD.INTEGER, XSD.LANGUAGE, XSD.LONG,
        XSD.NAME, XSD.NCNAME, XSD.NEGATIVE_INTEGER,
        XSD.NMTOKEN, XSD.NMTOKENS,
        XSD.NON_NEGATIVE_INTEGER, XSD.NON_POSITIVE_INTEGER,
        XSD.NORMALIZEDSTRING, XSD.NOTATION,
        XSD.POSITIVE_INTEGER, XSD.QNAME, XSD.SHORT,
        XSD.STRING, XSD.TIME, XSD.TOKEN,
        XSD.UNSIGNED_BYTE, XSD.UNSIGNED_INT,
        XSD.UNSIGNED_LONG, XSD.UNSIGNED_SHORT,
        XSD.YEARMONTHDURATION};

    public static final FileSchema SCHEMA = FileSchema.builder(PATH_NAME)
            .addColumn(RoDictionary.ID, PhysicalType.INT64, RepetitionType.REQUIRED)
            .addColumn(LABEL, PhysicalType.BYTE_ARRAY, RepetitionType.REQUIRED, new LogicalType.StringType())
            .addColumn(DATATYPE_ID, PhysicalType.INT64, RepetitionType.REQUIRED)
            .addColumn(LANGUAGE, PhysicalType.BYTE_ARRAY, RepetitionType.OPTIONAL, new LogicalType.StringType())
            .build();

    public RoLiteralDict(ParquetTable table) {
        super(table, LABEL);
    }

    @Override
    public Optional<RoLiteral> find(Literal value) {
        final String label = value.stringValue();
        final Optional<String> language = value.getLanguage();
        final FilterPredicate filter;
        if (language.isPresent()) {
            filter = FilterPredicate.and(FilterPredicate.eq(LABEL, label),
                    FilterPredicate.eq(LANGUAGE, language.get()));
        } else {
            filter = FilterPredicate.and(FilterPredicate.eq(LABEL, label),
                    FilterPredicate.eq(DATATYPE_ID, (long) datatypeCode(value.getDatatype())),
                    FilterPredicate.isNull(LANGUAGE));
        }
        return findId(filter).map(id -> new RoSimpleLiteral(id, this));
    }

    public String stringValue(long id) {
        try {
            return readStringAt(id);
        } catch (IOException ex) {
            Logger.getLogger(RoLiteralDict.class.getName()).log(Level.SEVERE, null, ex);
            return "";
        }
    }

    public IRI dataType(long id) {
        if (language(id).isPresent()) {
            return RDF.LANGSTRING;
        }
        Optional<Long> datatype = readLongAt(id, DATATYPE_ID);
        if (datatype.isEmpty()) {
            Logger.getLogger(RoLiteralDict.class.getName()).log(Level.SEVERE, "No literal at {0}", id);
            return null;
        }
        return DATA_TYPES[datatype.get().intValue()];
    }

    public RoLiteral get(long id) {
        return new RoSimpleLiteral(id, this);
    }

    public Optional<String> language(long id) {
        return readStringAt(id, LANGUAGE).filter(s -> !s.isEmpty());
    }

    public static int datatypeCode(IRI datatype) {
        for (int i = 0; i < DATA_TYPES.length; i++) {
            if (DATA_TYPES[i].equals(datatype)) {
                return i;
            }
        }
        return -1;
    }
}
