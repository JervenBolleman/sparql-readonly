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
import org.eclipse.rdf4j.model.util.Values;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoSimpleLiteral;

public class RoLiteralDict extends RoDictionary<RoLiteral, Literal> {

    public static final String PATH_NAME = "literals";
    public static final String LABEL = "label";
    public static final String DATATYPE = "datatype";
    public static final String LANGUAGE = "language";

    public static final FileSchema SCHEMA = FileSchema.builder(PATH_NAME)
            .addColumn(RoDictionary.ID, PhysicalType.INT64, RepetitionType.REQUIRED)
            .addColumn(LABEL, PhysicalType.BYTE_ARRAY, RepetitionType.REQUIRED, new LogicalType.StringType())
            .addColumn(DATATYPE, PhysicalType.BYTE_ARRAY, RepetitionType.REQUIRED, new LogicalType.StringType())
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
                    FilterPredicate.eq(DATATYPE, value.getDatatype().stringValue()),
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
        Optional<String> datatype = readStringAt(id, DATATYPE);
        if (datatype.isEmpty()) {
            Logger.getLogger(RoLiteralDict.class.getName()).log(Level.SEVERE, "No literal at {0}", id);
            return null;
        }
        return Values.iri(datatype.get());
    }

    public RoLiteral get(long id) {
        return new RoSimpleLiteral(id, this);
    }

    public Optional<String> language(long id) {
        return readStringAt(id, LANGUAGE).filter(s -> !s.isEmpty());
    }
}
