package sib.swiss.swissprot.sparql.ro;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.hardwood.Hardwood;
import dev.hardwood.InputFile;
import dev.hardwood.OutputFile;
import dev.hardwood.reader.ColumnReaders;
import dev.hardwood.reader.FilterPredicate;
import dev.hardwood.reader.ParquetFileReader;
import dev.hardwood.reader.RowReader;
import dev.hardwood.schema.ColumnProjection;
import dev.hardwood.schema.ColumnSchema;
import dev.hardwood.schema.FileSchema;
import dev.hardwood.writer.ColumnBatch;
import dev.hardwood.writer.ParquetFileWriter;
import dev.hardwood.writer.WriterConfig;

/**
 * A read only Parquet file. A missing or zero length file is treated as a
 * table without rows, so that empty triple lists and dictionaries do not need
 * a file on disk.
 */
public final class ParquetTable implements Closeable {

    /**
     * Small enough to keep the allocation per lookup low, large enough to not
     * hand over every non matching row on its own.
     */
    private static final int LOOKUP_BATCH_SIZE = 4096;

    /**
     * For files read by point lookups on sorted columns. hardwood writes no
     * page index, so statistics can only skip whole row groups: small ones
     * let a lookup decode only the row group that can hold the value.
     */
    public static final WriterConfig LOOKUP_CONFIG = WriterConfig.builder()
            .rowGroupTargetRows(LOOKUP_BATCH_SIZE)
            .build();

    /**
     * For files written in bulk while many others are open, such as the
     * triple lists of every predicate: hardwood buffers a row group per
     * writer, by default up to 128 MB.
     */
    public static final WriterConfig LIST_CONFIG = WriterConfig.builder()
            .rowGroupBufferTargetBytes(4L << 20)
            .rowGroupTargetRows(64 * 1024)
            .build();

    /**
     * For the triple lists and wide tables, read both by scans and by
     * lookups of one subject or object. hardwood writes no page index, so a
     * lookup decodes the whole row group that can hold its value: small row
     * groups keep that short.
     */
    public static final WriterConfig TRIPLES_CONFIG = WriterConfig.builder()
            .rowGroupBufferTargetBytes(4L << 20)
            .rowGroupTargetRows(Integer.getInteger("sparql.ro.tripleRowGroupRows", 4096))
            .build();

    private final ParquetFileReader reader;

    private ParquetTable(ParquetFileReader reader) {
        this.reader = reader;
    }

    public static ParquetTable open(Hardwood hardwood, File file) throws IOException {
        if (!file.exists() || file.length() == 0) {
            return new ParquetTable(null);
        }
        return new ParquetTable(hardwood.open(InputFile.of(file.toPath())));
    }

    public long numberOfRows() {
        if (reader == null) {
            return 0;
        }
        return reader.getFileMetaData().numRows();
    }

    public boolean isEmpty() {
        return numberOfRows() == 0;
    }

    /**
     * @return the names of the columns, in schema order; none for a table
     * without a file
     */
    public List<String> columnNames() {
        if (reader == null) {
            return List.of();
        }
        return reader.getFileSchema().getColumns().stream().map(ColumnSchema::name).toList();
    }

    /**
     * @return a reader over all rows, the caller must close it. Empty if this
     * table has no rows.
     */
    public Optional<RowReader> rows() {
        if (isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(reader.rowReader());
    }

    /**
     * @return a reader over the rows matching the filter exactly, which the
     * caller must close. Empty if this table has no rows.
     */
    public Optional<RowReader> rows(FilterPredicate filter) {
        if (isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(reader.buildRowReader().filter(filter).build());
    }

    /**
     * @return readers over all rows of the columns, in batches of at most
     * {@code batchSize} rows, which the caller must close. Empty if this table
     * has no rows.
     */
    public Optional<ColumnReaders> columns(int batchSize, String... columns) {
        if (isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(reader.buildColumnReaders(ColumnProjection.columns(columns))
                .batchSize(batchSize)
                .build());
    }

    /**
     * @return readers over the rows matching the filter exactly, in batches
     * of at most {@code batchSize} rows, which the caller must close. Empty if
     * this table has no rows.
     */
    public Optional<ColumnReaders> columns(FilterPredicate filter, int batchSize, String... columns) {
        if (isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(reader.buildColumnReaders(ColumnProjection.columns(columns))
                .filter(filter)
                .batchSize(batchSize)
                .build());
    }

    /**
     * Point lookup of the first row matching the filter. Unlike a
     * {@link RowReader}, which sizes its batches for scanning, this uses small
     * batches.
     *
     * @param filter the row to find, applied exactly
     * @param value reads the wanted value from the batch whose first row is
     * the match
     * @param columns the columns to read
     * @return the value read or empty if no row matches
     */
    public <T> Optional<T> first(FilterPredicate filter,
            Function<ColumnReaders, T> value, String... columns) {
        if (isEmpty()) {
            return Optional.empty();
        }
        try (ColumnReaders batch = reader
                .buildColumnReaders(ColumnProjection.columns(columns))
                .filter(filter)
                .batchSize(LOOKUP_BATCH_SIZE)
                .build()) {
            // A filtered batch can be empty, the matching row may be in a later one
            while (batch.nextBatch()) {
                if (batch.getRecordCount() > 0) {
                    return Optional.ofNullable(value.apply(batch));
                }
            }
            return Optional.empty();
        }
    }

    @Override
    public void close() throws IOException {
        if (reader != null) {
            reader.close();
        }
    }

    /**
     * Writes a Parquet file one column batch at a time.
     */
    public static final class Writer implements Closeable {

        private final ParquetFileWriter writer;

        public Writer(File file, FileSchema schema) throws IOException {
            this(file, schema, WriterConfig.defaults());
        }

        public Writer(File file, FileSchema schema, WriterConfig config) throws IOException {
            this.writer = ParquetFileWriter.create(OutputFile.of(file.toPath()), schema, config);
        }

        public void writeBatch(Consumer<ColumnBatch> filler) throws IOException {
            writer.columnWriter().writeBatch(filler);
        }

        @Override
        public void close() throws IOException {
            writer.close();
        }
    }
}
