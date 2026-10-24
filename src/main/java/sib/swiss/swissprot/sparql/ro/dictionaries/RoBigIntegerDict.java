package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;

import dev.hardwood.metadata.LogicalType;
import dev.hardwood.metadata.PhysicalType;
import dev.hardwood.metadata.RepetitionType;
import dev.hardwood.reader.FilterPredicate;
import dev.hardwood.schema.FileSchema;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.impl.IntegerLiteral;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.values.RoBigIntegerLiteral;

/**
 * Integers that do not fit in the positive range of a long are stored in this
 * dictionary, in their decimal string form. Their ids have the high bit set,
 * all other integers are their own id.
 */
public class RoBigIntegerDict
        extends RoDictionary<RoBigIntegerLiteral, IntegerLiteral> {

    public static final String INT_LONG_VALUE = "int_long_value";
    public static final String PATH_NAME = "integer_dict";

    public static final FileSchema SCHEMA = FileSchema.builder(PATH_NAME)
            .addColumn(RoDictionary.ID, PhysicalType.INT64, RepetitionType.REQUIRED)
            .addColumn(INT_LONG_VALUE, PhysicalType.BYTE_ARRAY, RepetitionType.REQUIRED, new LogicalType.StringType())
            .build();

    public RoBigIntegerDict(ParquetTable table) {
        super(table, INT_LONG_VALUE);
    }

    public RoBigIntegerLiteral getFromId(long id) throws IOException {
        if ((SECOND_BYTE_TRUE & id) == SECOND_BYTE_TRUE) {
            return new RoBigIntegerLiteral(id, new BigInteger(readStringAt(id & ~SECOND_BYTE_TRUE)));
        } else {
            return new RoBigIntegerLiteral(id);
        }
    }

    @Override
    public Optional<RoBigIntegerLiteral> find(IntegerLiteral value) {
        BigInteger integerValue = value.integerValue();
        return find(integerValue);
    }

    public Optional<RoBigIntegerLiteral> find(Literal value) {
        BigInteger integerValue = value.integerValue();
        return find(integerValue);
    }

    public Optional<RoBigIntegerLiteral> find(int value) {
        BigInteger integerValue = BigInteger.valueOf(value);
        return find(integerValue);
    }

    public Optional<RoBigIntegerLiteral> find(BigInteger integerValue) {
        try {
            long intValueExact = integerValue.longValueExact();
            if ((SECOND_BYTE_TRUE & intValueExact) == SECOND_BYTE_TRUE) {
                return realSearch(integerValue);
            } else {
                return Optional.of(new RoBigIntegerLiteral(intValueExact));
            }
        } catch (ArithmeticException e) {
            return realSearch(integerValue);
        }
    }

    private Optional<RoBigIntegerLiteral> realSearch(BigInteger value) {
        return findId(FilterPredicate.eq(INT_LONG_VALUE, value.toString()))
                .map(id -> new RoBigIntegerLiteral(id | SECOND_BYTE_TRUE, value));
    }
}
