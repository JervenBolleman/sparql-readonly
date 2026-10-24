package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.function.Function;

import dev.hardwood.reader.ColumnReaders;
import dev.hardwood.reader.FilterPredicate;
import org.eclipse.rdf4j.model.Value;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.values.RoValue;

/**
 * A dictionary is a Parquet file with an id column, holding the row number,
 * and a string value column.
 */
public abstract class RoDictionary<T extends RoValue, V extends Value> implements Closeable {

	public static final long SECOND_BYTE_TRUE = 1L << 63;
	public static final String ID = "id_col";
	protected final ParquetTable table;
	private final String valueColumn;

	protected RoDictionary(ParquetTable table, String valueColumn) {
		super();
		this.table = table;
		this.valueColumn = valueColumn;
	}

	protected String readStringAt(long offset) throws IOException {
		return readStringAt(offset, valueColumn)
				.orElseThrow(() -> new IOException("No value at " + offset));
	}

	protected Optional<String> readStringAt(long offset, String column) {
		return first(FilterPredicate.eq(ID, offset), c -> c.getColumnReader(column).getStrings()[0], column);
	}

	protected Optional<Long> readLongAt(long offset, String column) {
		return first(FilterPredicate.eq(ID, offset), c -> c.getColumnReader(column).getLongs()[0], column);
	}

	/**
	 * @return the id of the first row matching the filter
	 */
	protected Optional<Long> findId(FilterPredicate filter) {
		return first(filter, c -> c.getColumnReader(ID).getLongs()[0], ID);
	}

	private <R> Optional<R> first(FilterPredicate filter, Function<ColumnReaders, R> value, String column) {
		try {
			return table.first(filter, value, column);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public abstract Optional<T> find(V value);

	@Override
	public void close() throws IOException {
		table.close();
	}
}
