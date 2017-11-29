package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hadoop.hive.ql.exec.vector.DecimalColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.hadoop.hive.ql.io.sarg.PredicateLeaf;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgument;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgumentFactory;
import org.apache.orc.Reader;
import org.apache.orc.RecordReader;

import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.impl.IntegerLiteral;

import sib.swiss.swissprot.sparql.ro.values.RoBigIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;

public class RoBigIntegerDict
        extends RoDictionary<RoBigIntegerLiteral, IntegerLiteral> {

    public static final String INT_LONG_VALUE = "int_long_value";
    public static final String PATH_NAME = "integer_dict";

    public RoBigIntegerDict(Reader reader) {
        super(reader);
    }

    public RoBigIntegerLiteral getFromId(long id) throws IOException {
        if ((SECOND_BYTE_TRUE & id) == SECOND_BYTE_TRUE) {
            long offset = id;
            RecordReader rows = reader.rows();
            rows.seekToRow(offset);
            VectorizedRowBatch batch = schema.createRowBatch(1);
            final boolean nextBatch = rows.nextBatch(batch);
            assert nextBatch;
            DecimalColumnVector longVector = (DecimalColumnVector) batch.cols[0];
            return new RoBigIntegerLiteral(id, longVector.vector[0].getHiveDecimal().bigDecimalValue().toBigInteger());
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

    public RoBigIntegerLiteral fromColumn(VectorizedRowBatch batch, BigInteger value, long id) {
        DecimalColumnVector longVector = (DecimalColumnVector) batch.cols[0];
        for (int i = 0; i < longVector.vector.length; i++) {
            BigInteger pv = longVector.vector[0].getHiveDecimal().bigDecimalValue().toBigInteger();
            if (pv.equals(value)) {
                return new RoBigIntegerLiteral(id + i, value);
            }
        }
        return null;
    }

    private Optional<RoBigIntegerLiteral> realSearch(BigInteger value) {
        try {
            final Reader.Options options = new Reader.Options();
            SearchArgument equals = SearchArgumentFactory.newBuilder().equals(INT_LONG_VALUE, PredicateLeaf.Type.DECIMAL, value.longValue()).build();
            options.searchArgument(equals, new String[]{INT_LONG_VALUE});
            RecordReader rows = reader.rows(options);
            long id = rows.getRowNumber();
            VectorizedRowBatch batch = schema.createRowBatch(1);
            final boolean hasBatch = rows.nextBatch(batch);
            if (hasBatch) {
                final RoBigIntegerLiteral roBigIntegerLiteral = fromColumn(batch, value, id);
                if (roBigIntegerLiteral == null) {
                    return Optional.empty();
                } else {
                    return Optional.of(roBigIntegerLiteral);
                }
            } else {
                return Optional.empty();
            }
        } catch (IOException ex) {
            Logger.getLogger(RoBigIntegerDict.class.getName()).log(Level.SEVERE, null, ex);
            return Optional.empty();
        }
    }
}
