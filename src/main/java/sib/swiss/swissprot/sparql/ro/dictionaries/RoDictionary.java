package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.util.Optional;

import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.Reader;
import org.apache.orc.RecordReader;
import org.apache.orc.TypeDescription;
import org.eclipse.rdf4j.model.Value;

import sib.swiss.swissprot.sparql.ro.values.RoValue;

public abstract class RoDictionary<T extends RoValue, V extends Value> {

	public static final long SECOND_BYTE_TRUE = 1L << 63;
	public static final String ID = "id_col";
	public static final int VALUE_COL = 1;
	public static final int ID_COL = 0;
	protected final Reader reader;
	protected final TypeDescription schema;

	protected RoDictionary(Reader reader) {
		super();
		this.reader = reader;
		this.schema = reader.getSchema();
	}

	protected String readStringAt(long offset) throws IOException {
		try (RecordReader rows = reader.rows()) {
			rows.seekToRow(offset);
			VectorizedRowBatch batch = schema.createRowBatch();
			final boolean nextBatch = rows.nextBatch(batch);
			assert nextBatch;
			BytesColumnVector stringVector = (BytesColumnVector) batch.cols[VALUE_COL];
			return stringVector.toString(0);
		}
	}

	public abstract Optional<T> find(V value);
}
