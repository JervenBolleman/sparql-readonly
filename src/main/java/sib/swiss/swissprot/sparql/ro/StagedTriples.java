package sib.swiss.swissprot.sparql.ro;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import dev.hardwood.Hardwood;
import dev.hardwood.metadata.PhysicalType;
import dev.hardwood.metadata.RepetitionType;
import dev.hardwood.reader.RowReader;
import dev.hardwood.schema.FileSchema;

/**
 * The triples of a load, as they were parsed, with provisional ids for the
 * values that need a dictionary. Written in one pass over the RDF, then read
 * once to store the triples with their final ids.
 *
 * <p>
 * Every position has a kind, saying how to read its id.
 */
final class StagedTriples implements Closeable {

    /**
     * How the id of a position is to be read.
     */
    enum Kind {
        /** No context, the default graph. */
        NONE,
        /** Provisional id in the IRI dictionary. */
        IRI,
        /** Final blank node id. */
        BNODE,
        /** Provisional id in the literal dictionary. */
        LITERAL,
        /** The value of an xsd:int. */
        INT,
        /** 1 for true, 0 for false. */
        BOOLEAN,
        /** The value of an xsd:integer that is its own id. */
        INTEGER,
        /** Provisional id in the integer dictionary. */
        BIG_INTEGER;

        private static final Kind[] VALUES = values();

        static Kind of(int ordinal) {
            return VALUES[ordinal];
        }
    }

    static final String SUBJECT_KIND = "s_kind";
    static final String SUBJECT = "s";
    static final String PREDICATE = "p";
    static final String OBJECT_KIND = "o_kind";
    static final String OBJECT = "o";
    static final String CONTEXT_KIND = "c_kind";
    static final String CONTEXT = "c";

    private static final FileSchema SCHEMA = FileSchema.builder("staged_triples")
            .addColumn(SUBJECT_KIND, PhysicalType.INT32, RepetitionType.REQUIRED)
            .addColumn(SUBJECT, PhysicalType.INT64, RepetitionType.REQUIRED)
            .addColumn(PREDICATE, PhysicalType.INT64, RepetitionType.REQUIRED)
            .addColumn(OBJECT_KIND, PhysicalType.INT32, RepetitionType.REQUIRED)
            .addColumn(OBJECT, PhysicalType.INT64, RepetitionType.REQUIRED)
            .addColumn(CONTEXT_KIND, PhysicalType.INT32, RepetitionType.REQUIRED)
            .addColumn(CONTEXT, PhysicalType.INT64, RepetitionType.REQUIRED)
            .build();

    private static final int BATCH_SIZE = 64 * 1024;

    private final ParquetTable.Writer writer;
    private final int[] subjectKinds = new int[BATCH_SIZE];
    private final long[] subjects = new long[BATCH_SIZE];
    private final long[] predicates = new long[BATCH_SIZE];
    private final int[] objectKinds = new int[BATCH_SIZE];
    private final long[] objects = new long[BATCH_SIZE];
    private final int[] contextKinds = new int[BATCH_SIZE];
    private final long[] contexts = new long[BATCH_SIZE];
    private int size;
    private long count;

    StagedTriples(File file) throws IOException {
        this.writer = new ParquetTable.Writer(file, SCHEMA, ParquetTable.LIST_CONFIG);
    }

    void add(Kind subjectKind, long subject, long predicate, Kind objectKind,
            long object, Kind contextKind, long context) throws IOException {
        if (size == BATCH_SIZE) {
            flush();
        }
        subjectKinds[size] = subjectKind.ordinal();
        subjects[size] = subject;
        predicates[size] = predicate;
        objectKinds[size] = objectKind.ordinal();
        objects[size] = object;
        contextKinds[size] = contextKind.ordinal();
        contexts[size] = context;
        size++;
        count++;
    }

    long count() {
        return count;
    }

    private void flush() throws IOException {
        if (size == 0) {
            return;
        }
        final int n = size;
        writer.writeBatch(b -> b.ints(SUBJECT_KIND, Arrays.copyOf(subjectKinds, n))
                .longs(SUBJECT, Arrays.copyOf(subjects, n))
                .longs(PREDICATE, Arrays.copyOf(predicates, n))
                .ints(OBJECT_KIND, Arrays.copyOf(objectKinds, n))
                .longs(OBJECT, Arrays.copyOf(objects, n))
                .ints(CONTEXT_KIND, Arrays.copyOf(contextKinds, n))
                .longs(CONTEXT, Arrays.copyOf(contexts, n)));
        size = 0;
    }

    /**
     * Finishes writing, the triples can then be
     * {@link #read(Hardwood, File, Consumer) read}.
     */
    @Override
    public void close() throws IOException {
        flush();
        writer.close();
    }

    /**
     * Reads the staged triples back in the order they were added.
     */
    static void read(Hardwood hardwood, File file, Consumer consumer) throws IOException {
        try (ParquetTable table = ParquetTable.open(hardwood, file)) {
            Optional<RowReader> rows = table.rows();
            if (rows.isEmpty()) {
                return;
            }
            try (RowReader row = rows.get()) {
                while (row.hasNext()) {
                    row.next();
                    consumer.accept(
                            Kind.of(row.getInt(SUBJECT_KIND)), row.getLong(SUBJECT),
                            row.getLong(PREDICATE),
                            Kind.of(row.getInt(OBJECT_KIND)), row.getLong(OBJECT),
                            Kind.of(row.getInt(CONTEXT_KIND)), row.getLong(CONTEXT));
                }
            }
        }
    }

    @FunctionalInterface
    interface Consumer {

        void accept(Kind subjectKind, long subject, long predicate, Kind objectKind,
                long object, Kind contextKind, long context) throws IOException;
    }
}
