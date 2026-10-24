package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import dev.hardwood.schema.FileSchema;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoDictionary;

/**
 * Writes a dictionary of an id and a string value per row, the id being the
 * row number.
 */
final class IdValueWriter implements Closeable {

    private static final int BATCH_SIZE = 64 * 1024;

    private final ParquetTable.Writer writer;
    private final String valueColumn;
    private final long[] ids = new long[BATCH_SIZE];
    private final byte[][] values = new byte[BATCH_SIZE][];
    private int size;
    private long next;

    IdValueWriter(File file, FileSchema schema, String valueColumn) throws IOException {
        this.writer = new ParquetTable.Writer(file, schema, ParquetTable.LOOKUP_CONFIG);
        this.valueColumn = valueColumn;
    }

    /**
     * @return the id of the value
     */
    long add(String value) throws IOException {
        if (size == BATCH_SIZE) {
            flush();
        }
        ids[size] = next;
        values[size] = value.getBytes(StandardCharsets.UTF_8);
        size++;
        return next++;
    }

    private void flush() throws IOException {
        if (size == 0) {
            return;
        }
        final long[] i = Arrays.copyOf(ids, size);
        final byte[][] v = Arrays.copyOf(values, size);
        writer.writeBatch(b -> b.longs(RoDictionary.ID, i).bytes(valueColumn, v));
        size = 0;
    }

    @Override
    public void close() throws IOException {
        flush();
        writer.close();
    }
}
