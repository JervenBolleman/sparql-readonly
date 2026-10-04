package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import dev.hardwood.metadata.LogicalType;
import dev.hardwood.metadata.PhysicalType;
import dev.hardwood.metadata.RepetitionType;
import dev.hardwood.reader.ColumnReaders;
import dev.hardwood.schema.FileSchema;

import sib.swiss.swissprot.sparql.ro.ParquetTable;

/**
 * The distinct values of one dictionary group (an IRI namespace, a literal
 * datatype or language, the big integers) seen while parsing. Each value gets
 * a provisional id, the order in which it was first seen, so that triples can
 * be stored before the dictionary is sorted. {@link #finish} then gives every
 * provisional id its final one.
 *
 * <p>
 * A provisional id over all groups is {@code group index << 32 | index in
 * the group}.
 *
 * <p>
 * When the {@link SpillContext} runs out of memory the values held in memory
 * are written, sorted, as a run, and forgotten: a value seen again later gets a
 * second provisional id. The runs are merged, in the background once there
 * are {@link #MERGE_FAN_IN} of them and for the last time when finishing, where
 * equal values meet and all their provisional ids get the same final id.
 *
 * @param <T> the type of value
 */
final class ProvisionalGroup<T> {

    static final int MERGE_FAN_IN = 8;

    private static final String VALUE = "value";
    private static final String PROVISIONAL = "provisional";
    private static final FileSchema RUN_SCHEMA = FileSchema.builder("run")
            .addColumn(VALUE, PhysicalType.BYTE_ARRAY, RepetitionType.REQUIRED, new LogicalType.StringType())
            .addColumn(PROVISIONAL, PhysicalType.INT64, RepetitionType.REQUIRED)
            .build();
    private static final int BATCH_SIZE = 64 * 1024;
    /**
     * Rows read at a time from a run, small: many runs are read at once.
     */
    private static final int READ_BATCH_SIZE = 1024;

    /**
     * How values are ordered, stored in a run, and how much memory they take.
     */
    interface Codec<T> extends Comparator<T> {

        String encode(T value);

        T decode(String value);

        /**
         * The memory a value takes in the map of values to provisional ids,
         * the map entry included.
         */
        long estimateBytes(T value);
    }

    static final Codec<String> STRINGS = new Codec<>() {
        @Override
        public int compare(String a, String b) {
            return a.compareTo(b);
        }

        @Override
        public String encode(String value) {
            return value;
        }

        @Override
        public String decode(String value) {
            return value;
        }

        @Override
        public long estimateBytes(String value) {
            // entry, Integer, String object and its array
            return 32 + 16 + 24 + 16 + value.length();
        }
    };

    static final Codec<BigInteger> BIG_INTEGERS = new Codec<>() {
        @Override
        public int compare(BigInteger a, BigInteger b) {
            return a.compareTo(b);
        }

        @Override
        public String encode(BigInteger value) {
            return value.toString();
        }

        @Override
        public BigInteger decode(String value) {
            return new BigInteger(value);
        }

        @Override
        public long estimateBytes(BigInteger value) {
            return 32 + 16 + 40 + value.bitLength() / 8;
        }
    };

    /**
     * Receives the distinct values of a group in order, and decides their
     * final ids.
     */
    @FunctionalInterface
    interface DistinctValues<T> {

        /**
         * @return the final id of the value
         */
        long accept(T value) throws IOException;
    }

    private final int index;
    private final Codec<T> codec;
    private final SpillContext context;
    private final Consumer<T> onNewValue;
    private Map<T, Integer> inMemory = new HashMap<>();
    private long bytesInMemory;
    private int provisionalIds;
    // shared with the merge thread, guarded by this
    private final List<File> runs = new ArrayList<>();
    private final List<Future<?>> merges = new ArrayList<>();
    private long[] finalIds;

    /**
     * @param onNewValue sees every value when it enters memory, a value that
     * was spilled can be seen again
     */
    ProvisionalGroup(int index, Codec<T> codec, SpillContext context, Consumer<T> onNewValue) {
        this.index = index;
        this.codec = codec;
        this.context = context;
        this.onNewValue = onNewValue;
        context.register(this);
    }

    ProvisionalGroup(int index, Codec<T> codec, SpillContext context) {
        this(index, codec, context, null);
    }

    static int group(long provisionalId) {
        return (int) (provisionalId >>> 32);
    }

    static int indexInGroup(long provisionalId) {
        return (int) provisionalId;
    }

    int index() {
        return index;
    }

    /**
     * @return the number of provisional ids handed out, at least the number
     * of distinct values
     */
    int provisionalIds() {
        return provisionalIds;
    }

    long bytesInMemory() {
        return bytesInMemory;
    }

    /**
     * @return the provisional id of the value
     */
    long add(T value) throws IOException {
        Integer id = inMemory.get(value);
        if (id == null) {
            if (provisionalIds == Integer.MAX_VALUE) {
                throw new IOException("More than " + Integer.MAX_VALUE + " values in dictionary group " + index);
            }
            id = provisionalIds++;
            inMemory.put(value, id);
            if (onNewValue != null) {
                onNewValue.accept(value);
            }
            long bytes = codec.estimateBytes(value);
            bytesInMemory += bytes;
            context.added(bytes);
        }
        return ((long) index << 32) | id;
    }

    /**
     * Writes the values in memory as a sorted run and forgets them.
     */
    void spill() throws IOException {
        if (inMemory.isEmpty()) {
            return;
        }
        File run = context.newRunFile();
        try (RunWriter writer = new RunWriter(run)) {
            Iterator<Entry<T>> sorted = sortedInMemory();
            while (sorted.hasNext()) {
                Entry<T> entry = sorted.next();
                writer.add(codec.encode(entry.value), entry.provisional);
            }
        }
        inMemory = new HashMap<>();
        context.released(bytesInMemory);
        bytesInMemory = 0;
        synchronized (this) {
            runs.add(run);
        }
        mergeInBackground();
    }

    /**
     * Merges {@link #MERGE_FAN_IN} runs in the background, when there are
     * that many and there is a background merger.
     */
    private synchronized void mergeInBackground() {
        while (runs.size() >= MERGE_FAN_IN) {
            List<File> inputs = new ArrayList<>(runs.subList(0, MERGE_FAN_IN));
            runs.subList(0, MERGE_FAN_IN).clear();
            Future<?> merge = context.submitMerge(() -> {
                try {
                    File merged = mergeRuns(inputs);
                    synchronized (this) {
                        runs.add(merged);
                    }
                    mergeInBackground();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            if (merge == null) {
                // no background merger, the runs are merged when finishing
                runs.addAll(0, inputs);
                return;
            }
            merges.add(merge);
        }
    }

    private File mergeRuns(List<File> inputs) throws IOException {
        File merged = context.newRunFile();
        List<Cursor<T>> cursors = new ArrayList<>();
        try (RunWriter writer = new RunWriter(merged)) {
            for (File input : inputs) {
                cursors.add(new RunCursor(input));
            }
            PriorityQueue<Cursor<T>> queue = queue(cursors);
            while (!queue.isEmpty()) {
                Cursor<T> cursor = queue.poll();
                writer.add(codec.encode(cursor.value()), cursor.provisional());
                if (cursor.advance()) {
                    queue.add(cursor);
                }
            }
        } finally {
            closeAll(cursors);
        }
        for (File input : inputs) {
            input.delete();
        }
        return merged;
    }

    /**
     * Merges everything seen into one sorted stream of distinct values, gives
     * each to {@code distinct}, and records its final id for all the
     * provisional ids it got. Frees the memory and the runs.
     */
    void finish(DistinctValues<T> distinct) throws IOException {
        List<File> remaining = awaitMerges();
        // keep the number of runs read at once bounded
        while (remaining.size() > MERGE_FAN_IN) {
            List<File> inputs = new ArrayList<>(remaining.subList(0, MERGE_FAN_IN));
            remaining.subList(0, MERGE_FAN_IN).clear();
            remaining.add(mergeRuns(inputs));
        }
        finalIds = new long[provisionalIds];
        List<Cursor<T>> cursors = new ArrayList<>();
        try {
            cursors.add(new MemoryCursor(sortedInMemory()));
            for (File run : remaining) {
                cursors.add(new RunCursor(run));
            }
            PriorityQueue<Cursor<T>> queue = queue(cursors);
            T previous = null;
            long finalId = 0;
            while (!queue.isEmpty()) {
                Cursor<T> cursor = queue.poll();
                T value = cursor.value();
                if (previous == null || codec.compare(previous, value) != 0) {
                    finalId = distinct.accept(value);
                    previous = value;
                }
                finalIds[cursor.provisional()] = finalId;
                if (cursor.advance()) {
                    queue.add(cursor);
                }
            }
        } finally {
            closeAll(cursors);
        }
        for (File run : remaining) {
            run.delete();
        }
        context.released(bytesInMemory);
        bytesInMemory = 0;
        inMemory = null;
    }

    private List<File> awaitMerges() throws IOException {
        while (true) {
            List<Future<?>> pending;
            synchronized (this) {
                pending = new ArrayList<>(merges);
                merges.clear();
                if (pending.isEmpty()) {
                    return new ArrayList<>(runs);
                }
            }
            for (Future<?> merge : pending) {
                try {
                    merge.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                } catch (ExecutionException e) {
                    throw new IOException("Merging dictionary runs failed", e.getCause());
                }
            }
        }
    }

    long finalId(long provisionalId) {
        return finalIds[indexInGroup(provisionalId)];
    }

    private Iterator<Entry<T>> sortedInMemory() {
        List<Entry<T>> entries = new ArrayList<>(inMemory.size());
        inMemory.forEach((value, provisional) -> entries.add(new Entry<>(value, provisional)));
        entries.sort((a, b) -> codec.compare(a.value, b.value));
        return entries.iterator();
    }

    private PriorityQueue<Cursor<T>> queue(List<Cursor<T>> cursors) throws IOException {
        PriorityQueue<Cursor<T>> queue = new PriorityQueue<>(Math.max(1, cursors.size()),
                (a, b) -> codec.compare(a.value(), b.value()));
        for (Cursor<T> cursor : cursors) {
            if (cursor.advance()) {
                queue.add(cursor);
            }
        }
        return queue;
    }

    private static <T> void closeAll(List<Cursor<T>> cursors) throws IOException {
        for (Cursor<T> cursor : cursors) {
            cursor.close();
        }
    }

    private record Entry<T>(T value, int provisional) {
    }

    /**
     * A sorted source of values with their provisional ids.
     */
    private interface Cursor<T> extends Closeable {

        /**
         * @return false when there are no more values
         */
        boolean advance() throws IOException;

        T value();

        int provisional();
    }

    private final class MemoryCursor implements Cursor<T> {

        private final Iterator<Entry<T>> entries;
        private Entry<T> current;

        private MemoryCursor(Iterator<Entry<T>> entries) {
            this.entries = entries;
        }

        @Override
        public boolean advance() {
            if (!entries.hasNext()) {
                return false;
            }
            current = entries.next();
            return true;
        }

        @Override
        public T value() {
            return current.value;
        }

        @Override
        public int provisional() {
            return current.provisional;
        }

        @Override
        public void close() {
        }
    }

    private final class RunCursor implements Cursor<T> {

        private final ParquetTable table;
        private final ColumnReaders batches;
        private String[] values;
        private long[] provisionals;
        private int next;
        private T value;
        private int provisional;

        private RunCursor(File run) throws IOException {
            this.table = ParquetTable.open(context.hardwood(), run);
            this.batches = table.columns(READ_BATCH_SIZE, VALUE, PROVISIONAL).orElse(null);
        }

        @Override
        public boolean advance() {
            if (batches == null) {
                return false;
            }
            while (values == null || next == values.length) {
                if (!batches.nextBatch()) {
                    return false;
                }
                values = batches.getColumnReader(VALUE).getStrings();
                provisionals = batches.getColumnReader(PROVISIONAL).getLongs();
                next = 0;
            }
            value = codec.decode(values[next]);
            provisional = (int) provisionals[next];
            next++;
            return true;
        }

        @Override
        public T value() {
            return value;
        }

        @Override
        public int provisional() {
            return provisional;
        }

        @Override
        public void close() throws IOException {
            if (batches != null) {
                batches.close();
            }
            table.close();
        }
    }

    private static final class RunWriter implements Closeable {

        private final ParquetTable.Writer writer;
        private final byte[][] values = new byte[BATCH_SIZE][];
        private final long[] provisionals = new long[BATCH_SIZE];
        private int size;

        private RunWriter(File run) throws IOException {
            this.writer = new ParquetTable.Writer(run, RUN_SCHEMA);
        }

        void add(String value, int provisional) throws IOException {
            if (size == BATCH_SIZE) {
                flush();
            }
            values[size] = value.getBytes(StandardCharsets.UTF_8);
            provisionals[size] = provisional;
            size++;
        }

        private void flush() throws IOException {
            if (size == 0) {
                return;
            }
            final byte[][] v = Arrays.copyOf(values, size);
            final long[] p = Arrays.copyOf(provisionals, size);
            writer.writeBatch(b -> b.bytes(VALUE, v).longs(PROVISIONAL, p));
            size = 0;
        }

        @Override
        public void close() throws IOException {
            flush();
            writer.close();
        }
    }
}
