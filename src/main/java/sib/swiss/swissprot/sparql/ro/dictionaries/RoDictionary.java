package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.Reader;
import org.apache.orc.RecordReader;
import org.apache.orc.TypeDescription;

import org.eclipse.rdf4j.model.Value;

import sib.swiss.swissprot.sparql.ro.values.RoValue;

public abstract class RoDictionary<T extends RoValue, V extends Value> {
    public static final long SECOND_BYTE_TRUE = 1L << 63;
    protected final Reader reader;
    protected final TypeDescription schema;

    protected RoDictionary(Reader reader) {
        super();
        this.reader = reader;
        this.schema = reader.getSchema();
    }

    protected String readStringAt(long offset) throws IOException {
        RecordReader rows = reader.rows();
        rows.seekToRow(offset);
        VectorizedRowBatch batch = schema.createRowBatch();
        final boolean nextBatch = rows.nextBatch(batch);
        assert nextBatch;
        BytesColumnVector stringVector = (BytesColumnVector) batch.cols[0];
        return stringVector.toString(0);
    }

    public abstract Optional<T> find(V value);
}
