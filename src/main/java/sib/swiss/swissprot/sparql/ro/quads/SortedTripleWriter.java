package sib.swiss.swissprot.sparql.ro.quads;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

import dev.hardwood.Hardwood;
import dev.hardwood.metadata.PhysicalType;
import dev.hardwood.metadata.RepetitionType;
import dev.hardwood.reader.ColumnReaders;
import dev.hardwood.schema.FileSchema;

import sib.swiss.swissprot.sparql.ro.ParquetTable;

/**
 * Writes a triple list sorted by subject, then object, so that statistics let
 * a lookup of a subject skip all but the row groups that hold it.
 *
 * <p>
 * Triples are buffered in memory, up to {@link #RUN_TRIPLES}. A full buffer
 * is sorted and written as a run; finishing merges the runs, at most
 * {@link #MERGE_FAN_IN} at a time, into the list. A list that never fills
 * its buffer is sorted in memory and written once. Exact duplicates, same
 * subject, object and context, are written once.
 */
final class SortedTripleWriter {

    static final int RUN_TRIPLES = 1 << 20;
    static final int MERGE_FAN_IN = 64;
    private static final int FIRST_CAPACITY = 1024;
    private static final int READ_BATCH_SIZE = 1024;
    private static final int WRITE_BATCH_SIZE = 64 * 1024;
    /**
     * The context of a triple in the default graph.
     */
    static final int NO_CONTEXT = -1;

    private static final String CONTEXT = "c";
    private static final FileSchema RUN_SCHEMA = FileSchema.builder("triple_run")
            .addColumn(RoResourceRoValueList.SUBJECT, PhysicalType.INT64, RepetitionType.REQUIRED)
            .addColumn(RoResourceRoValueList.OBJECT, PhysicalType.INT64, RepetitionType.REQUIRED)
            .addColumn(CONTEXT, PhysicalType.INT32, RepetitionType.REQUIRED)
            .build();

    /**
     * Told the row of every triple that is in a context.
     */
    @FunctionalInterface
    interface ContextRows {

        void inContext(int context, long row);
    }

    private final File file;
    private final Hardwood hardwood;
    private final int runTriples;
    private long[] subjects;
    private long[] objects;
    private int[] contexts;
    private int size;
    private final List<File> runs = new ArrayList<>();

    SortedTripleWriter(File file, Hardwood hardwood) {
        this(file, hardwood, RUN_TRIPLES);
    }

    /**
     * @param runTriples the triples buffered before a run is written
     */
    SortedTripleWriter(File file, Hardwood hardwood, int runTriples) {
        this.file = file;
        this.hardwood = hardwood;
        this.runTriples = runTriples;
    }

    /**
     * @param context an index the caller gives its contexts, or
     * {@link #NO_CONTEXT}
     */
    void add(long subject, long object, int context) throws IOException {
        if (subjects == null) {
            int capacity = Math.min(FIRST_CAPACITY, runTriples);
            subjects = new long[capacity];
            objects = new long[capacity];
            contexts = new int[capacity];
        } else if (size == subjects.length) {
            if (size == runTriples) {
                spill();
            } else {
                int capacity = Math.min(runTriples, size * 2);
                subjects = Arrays.copyOf(subjects, capacity);
                objects = Arrays.copyOf(objects, capacity);
                contexts = Arrays.copyOf(contexts, capacity);
            }
        }
        subjects[size] = subject;
        objects[size] = object;
        contexts[size] = context;
        size++;
    }

    private void spill() throws IOException {
        sortBuffer();
        File run = new File(file.getParentFile(), file.getName() + ".run-" + runs.size());
        try (Output out = new Output(run, RUN_SCHEMA, true)) {
            for (int i = 0; i < size; i++) {
                out.add(subjects[i], objects[i], contexts[i]);
            }
        }
        runs.add(run);
        size = 0;
    }

    /**
     * Writes the list. Without triples it has no file, an earlier one is
     * deleted.
     *
     * @return the number of triples written
     */
    long finish(ContextRows contextRows) throws IOException {
        if (runs.isEmpty() && size == 0) {
            if (file.exists() && !file.delete()) {
                throw new IOException("Could not delete " + file);
            }
            return 0;
        }
        long written;
        if (runs.isEmpty()) {
            sortBuffer();
            try (Output out = new Output(file, RoResourceRoValueList.SCHEMA, false)) {
                for (int i = 0; i < size; i++) {
                    out.addUnique(subjects[i], objects[i], contexts[i], contextRows);
                }
                written = out.rows;
            }
        } else {
            if (size > 0) {
                spill();
            }
            subjects = null;
            objects = null;
            contexts = null;
            while (runs.size() > MERGE_FAN_IN) {
                List<File> inputs = new ArrayList<>(runs.subList(0, MERGE_FAN_IN));
                runs.subList(0, MERGE_FAN_IN).clear();
                File merged = new File(file.getParentFile(), file.getName() + ".run-m" + runs.size());
                try (Output out = new Output(merged, RUN_SCHEMA, true)) {
                    merge(inputs, out, null);
                }
                runs.add(merged);
            }
            try (Output out = new Output(file, RoResourceRoValueList.SCHEMA, false)) {
                merge(runs, out, contextRows);
                written = out.rows;
            }
            runs.clear();
        }
        subjects = null;
        objects = null;
        contexts = null;
        size = 0;
        return written;
    }

    /**
     * Merges the runs into out and deletes them. With contextRows the
     * duplicates are dropped and the rows of contexts reported.
     */
    private void merge(List<File> inputs, Output out, ContextRows contextRows) throws IOException {
        List<RunCursor> cursors = new ArrayList<>();
        try {
            PriorityQueue<RunCursor> queue = new PriorityQueue<>(inputs.size(), SortedTripleWriter::compare);
            for (File input : inputs) {
                RunCursor cursor = new RunCursor(input);
                cursors.add(cursor);
                if (cursor.advance()) {
                    queue.add(cursor);
                }
            }
            while (!queue.isEmpty()) {
                RunCursor cursor = queue.poll();
                if (contextRows == null) {
                    out.add(cursor.subject, cursor.object, cursor.context);
                } else {
                    out.addUnique(cursor.subject, cursor.object, cursor.context, contextRows);
                }
                if (cursor.advance()) {
                    queue.add(cursor);
                }
            }
        } finally {
            for (RunCursor cursor : cursors) {
                cursor.close();
            }
        }
        for (File input : inputs) {
            input.delete();
        }
    }

    private static int compare(RunCursor a, RunCursor b) {
        int c = Long.compare(a.subject, b.subject);
        if (c == 0) {
            c = Long.compare(a.object, b.object);
        }
        return c == 0 ? Integer.compare(a.context, b.context) : c;
    }

    private void sortBuffer() {
        quickSort(0, size - 1);
    }

    private int compareAt(int i, int j) {
        int c = Long.compare(subjects[i], subjects[j]);
        if (c == 0) {
            c = Long.compare(objects[i], objects[j]);
        }
        return c == 0 ? Integer.compare(contexts[i], contexts[j]) : c;
    }

    private void quickSort(int low, int high) {
        while (low < high) {
            if (high - low < 16) {
                insertionSort(low, high);
                return;
            }
            int middle = (low + high) >>> 1;
            // median of three as pivot, moved to high
            if (compareAt(middle, low) < 0) {
                swap(middle, low);
            }
            if (compareAt(high, low) < 0) {
                swap(high, low);
            }
            if (compareAt(high, middle) < 0) {
                swap(high, middle);
            }
            swap(middle, high);
            int store = low;
            for (int i = low; i < high; i++) {
                if (compareAt(i, high) < 0) {
                    swap(i, store++);
                }
            }
            swap(store, high);
            // recurse into the smaller side to bound the stack
            if (store - low < high - store) {
                quickSort(low, store - 1);
                low = store + 1;
            } else {
                quickSort(store + 1, high);
                high = store - 1;
            }
        }
    }

    private void insertionSort(int low, int high) {
        for (int i = low + 1; i <= high; i++) {
            for (int j = i; j > low && compareAt(j, j - 1) < 0; j--) {
                swap(j, j - 1);
            }
        }
    }

    private void swap(int i, int j) {
        long s = subjects[i];
        subjects[i] = subjects[j];
        subjects[j] = s;
        long o = objects[i];
        objects[i] = objects[j];
        objects[j] = o;
        int c = contexts[i];
        contexts[i] = contexts[j];
        contexts[j] = c;
    }

    /**
     * Writes sorted triples, to a run with their context or to the list.
     */
    private static final class Output implements Closeable {

        private final ParquetTable.Writer writer;
        private final boolean withContext;
        private final long[] subjects = new long[WRITE_BATCH_SIZE];
        private final long[] objects = new long[WRITE_BATCH_SIZE];
        private final int[] contexts = new int[WRITE_BATCH_SIZE];
        private int size;
        private long rows;
        private boolean any;
        private long lastSubject;
        private long lastObject;
        private int lastContext;

        private Output(File file, FileSchema schema, boolean withContext) throws IOException {
            this.writer = new ParquetTable.Writer(file, schema, ParquetTable.LIST_CONFIG);
            this.withContext = withContext;
        }

        void add(long subject, long object, int context) throws IOException {
            if (size == WRITE_BATCH_SIZE) {
                flush();
            }
            subjects[size] = subject;
            objects[size] = object;
            contexts[size] = context;
            size++;
        }

        /**
         * Adds the triple unless it is the same as the last one, and reports
         * its row if it is in a context.
         */
        void addUnique(long subject, long object, int context, ContextRows contextRows) throws IOException {
            if (any && subject == lastSubject && object == lastObject && context == lastContext) {
                return;
            }
            any = true;
            lastSubject = subject;
            lastObject = object;
            lastContext = context;
            if (context != NO_CONTEXT) {
                contextRows.inContext(context, rows);
            }
            add(subject, object, context);
            rows++;
        }

        private void flush() throws IOException {
            if (size == 0) {
                return;
            }
            final long[] s = Arrays.copyOf(subjects, size);
            final long[] o = Arrays.copyOf(objects, size);
            if (withContext) {
                final int[] c = Arrays.copyOf(contexts, size);
                writer.writeBatch(b -> b.longs(RoResourceRoValueList.SUBJECT, s)
                        .longs(RoResourceRoValueList.OBJECT, o).ints(CONTEXT, c));
            } else {
                writer.writeBatch(b -> b.longs(RoResourceRoValueList.SUBJECT, s)
                        .longs(RoResourceRoValueList.OBJECT, o));
            }
            size = 0;
        }

        @Override
        public void close() throws IOException {
            flush();
            writer.close();
        }
    }

    private final class RunCursor implements Closeable {

        private final ParquetTable table;
        private final ColumnReaders batches;
        private long[] batchSubjects;
        private long[] batchObjects;
        private int[] batchContexts;
        private int next;
        long subject;
        long object;
        int context;

        private RunCursor(File run) throws IOException {
            this.table = ParquetTable.open(hardwood, run);
            this.batches = table.columns(READ_BATCH_SIZE, RoResourceRoValueList.SUBJECT,
                    RoResourceRoValueList.OBJECT, CONTEXT).orElse(null);
        }

        boolean advance() {
            if (batches == null) {
                return false;
            }
            while (batchSubjects == null || next == batchSubjects.length) {
                if (!batches.nextBatch()) {
                    return false;
                }
                batchSubjects = batches.getColumnReader(RoResourceRoValueList.SUBJECT).getLongs();
                batchObjects = batches.getColumnReader(RoResourceRoValueList.OBJECT).getLongs();
                batchContexts = batches.getColumnReader(CONTEXT).getInts();
                next = 0;
            }
            subject = batchSubjects[next];
            object = batchObjects[next];
            context = batchContexts[next];
            next++;
            return true;
        }

        @Override
        public void close() throws IOException {
            if (batches != null) {
                batches.close();
            }
            table.close();
        }
    }
}
