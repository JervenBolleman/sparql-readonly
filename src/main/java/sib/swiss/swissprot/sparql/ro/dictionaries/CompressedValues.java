package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import swiss.sib.swissprot.fsst4j.ByteStrings;
import swiss.sib.swissprot.fsst4j.FSST.FsstCompressedData;
import swiss.sib.swissprot.fsst4j.FsstDecoder;
import swiss.sib.swissprot.fsst4j.FsstEncoder;

/**
 * Values compressed with FSST, in batches of {@value #BATCH} that each value
 * of can be decompressed on its own. The id of a value is the offset of its
 * batch in the file, shifted left by {@value #BATCH_BITS}, plus its index in
 * the batch.
 *
 * <p>
 * The values are written in segments of up to {@value #SEGMENT_BATCHES}
 * batches that share a symbol table, built from the values of the segment.
 * A segment is its symbol table, in the format of FSST's
 * {@code fsst_export}, followed by its batches. A batch is:
 * <ul>
 * <li>the distance back from the batch to the symbol table of its segment,
 * an unsigned LEB128 varint;</li>
 * <li>the number of values in it, one byte, 1 to {@value #BATCH} stored as
 * that number minus one;</li>
 * <li>the width of the lengths, one byte: the fewest bytes, 1 to 4, that
 * hold the longest compressed value of the batch;</li>
 * <li>the compressed length of each value, in that many bytes, little
 * endian;</li>
 * <li>the compressed values, one after the other.</li>
 * </ul>
 * All batches but the last of a group of values hold {@value #BATCH}, so the
 * n-th value of a group is in its batch n / {@value #BATCH}. The file is
 * memory mapped, the symbol tables decoded once.
 */
public final class CompressedValues implements Closeable {

    public static final int BATCH_BITS = 7;
    public static final int BATCH = 1 << BATCH_BITS;
    private static final int INDEX_MASK = BATCH - 1;
    /**
     * Batches that share a symbol table: enough values for a good table, few
     * enough that it follows the data.
     */
    public static final int SEGMENT_BATCHES = 64;
    private static final CompressedValues EMPTY = new CompressedValues(null, MemorySegment.NULL);

    private final Arena arena;
    private final MemorySegment values;
    private final Map<Long, FsstDecoder> decoders = new ConcurrentHashMap<>();

    private CompressedValues(Arena arena, MemorySegment values) {
        this.arena = arena;
        this.values = values;
    }

    /**
     * @return the values of the file, none if there is no file
     */
    public static CompressedValues open(File file) throws IOException {
        if (!file.exists() || file.length() == 0) {
            return EMPTY;
        }
        Arena arena = Arena.ofShared();
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            return new CompressedValues(arena, channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena));
        } catch (IOException | RuntimeException e) {
            arena.close();
            throw e;
        }
    }

    /**
     * @return the id of the value at this index in the batch at this offset
     */
    public static long id(long batchOffset, int index) {
        return batchOffset << BATCH_BITS | index;
    }

    /**
     * @return the id that is less than the id of every value at or after
     * this offset, and more than that of every value before it
     */
    public static long idAt(long offset) {
        return offset << BATCH_BITS;
    }

    /**
     * @return the size of the file in bytes
     */
    public long size() {
        return values.byteSize();
    }

    /**
     * @return the value with this id
     */
    public String string(long id) {
        Scratch scratch = SCRATCH.get();
        int length = decompress(id, scratch);
        return new String(scratch.out, 0, length, StandardCharsets.UTF_8);
    }

    /**
     * @return whether the value with this id is these UTF-8 bytes
     */
    public boolean equalsAt(long id, byte[] value) {
        Scratch scratch = SCRATCH.get();
        int length = decompress(id, scratch);
        return Arrays.equals(scratch.out, 0, length, value, 0, value.length);
    }

    /**
     * @return the UTF-8 bytes of the value with this id
     */
    public byte[] bytes(long id) {
        Scratch scratch = SCRATCH.get();
        return Arrays.copyOf(scratch.out, decompress(id, scratch));
    }

    /**
     * The arrays a thread reads values with, grown as needed.
     */
    private static final class Scratch {

        byte[] lengths = new byte[BATCH * 4];
        byte[] in = new byte[256];
        byte[] out = new byte[2048];
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    /**
     * Decompresses the value with this id into scratch.out.
     *
     * @return its length
     */
    private int decompress(long id, Scratch scratch) {
        long batch = id >>> BATCH_BITS;
        int index = (int) (id & INDEX_MASK);
        long[] at = {batch};
        long table = batch - varint(at);
        int count = (values.get(ValueLayout.JAVA_BYTE, at[0]) & 0xFF) + 1;
        int width = values.get(ValueLayout.JAVA_BYTE, at[0] + 1);
        long lengthsAt = at[0] + 2;
        // the lengths up to this value's, in one copy
        byte[] lengths = scratch.lengths;
        MemorySegment.copy(values, ValueLayout.JAVA_BYTE, lengthsAt, lengths, 0, (index + 1) * width);
        long before = 0;
        if (width == 1) {
            for (int i = 0; i < index; i++) {
                before += lengths[i] & 0xFF;
            }
        } else if (width == 2) {
            for (int i = 0; i < index; i++) {
                before += (lengths[2 * i] & 0xFF) | (lengths[2 * i + 1] & 0xFF) << 8;
            }
        } else {
            for (int i = 0; i < index; i++) {
                before += length(lengths, i, width);
            }
        }
        int length = length(lengths, index, width);
        if (scratch.in.length < length) {
            scratch.in = new byte[length];
        }
        MemorySegment.copy(values, ValueLayout.JAVA_BYTE, lengthsAt + (long) count * width + before, scratch.in, 0,
                length);
        // a code decodes to at most 8 bytes, and the decoder writes 8 at a time
        int most = 8 * length + 8;
        if (scratch.out.length < most) {
            scratch.out = new byte[most];
        }
        return decoder(table).decompress(scratch.in, 0, length, scratch.out, 0, scratch.out.length);
    }

    /**
     * @return the i-th length of width bytes, little endian
     */
    private static int length(byte[] lengths, int i, int width) {
        int at = i * width;
        int length = 0;
        for (int b = 0; b < width; b++) {
            length |= (lengths[at + b] & 0xFF) << (8 * b);
        }
        return length;
    }

    /**
     * Reads an unsigned LEB128 varint at at[0], and moves at[0] past it.
     */
    private long varint(long[] at) {
        long value = 0;
        int shift = 0;
        byte b;
        do {
            b = values.get(ValueLayout.JAVA_BYTE, at[0]++);
            value |= (long) (b & 0x7F) << shift;
            shift += 7;
        } while (b < 0);
        return value;
    }

    private FsstDecoder decoder(long table) {
        FsstDecoder decoder = decoders.get(table);
        if (decoder != null) {
            return decoder;
        }
        return decoders.computeIfAbsent(table, t -> {
            byte[] header = new byte[(int) Math.min(FsstDecoder.MAX_HEADER, values.byteSize() - t)];
            MemorySegment.copy(values, ValueLayout.JAVA_BYTE, t, header, 0, header.length);
            return FsstDecoder.importTable(header, 0);
        });
    }

    @Override
    public void close() {
        if (arena != null) {
            arena.close();
        }
    }

    /**
     * Told the id of every value once its batch is written.
     */
    @FunctionalInterface
    public interface Written {

        void written(byte[] value, long id) throws IOException;
    }

    /**
     * Writes values in groups, such as the names of a namespace: a group
     * starts a segment, so its batches hold only its values.
     */
    public static final class Writer implements Closeable {

        private final OutputStream out;
        private final Written written;
        private long position;
        private final List<byte[]> segment = new ArrayList<>();
        private long inGroup;
        private long[] batches = new long[16];
        private int batchCount;

        /**
         * @param written told the id of each value once it is written
         */
        public Writer(File file, Written written) throws IOException {
            this.out = new BufferedOutputStream(new FileOutputStream(file), 1 << 16);
            this.written = written;
        }

        /**
         * @return the offset the next group starts at
         */
        public long position() {
            return position;
        }

        /**
         * Adds the next value of the group.
         *
         * @return its index in the group, see {@link #endGroup()}
         */
        public long add(byte[] value) throws IOException {
            segment.add(value);
            if (segment.size() == SEGMENT_BATCHES * BATCH) {
                writeSegment();
            }
            return inGroup++;
        }

        /**
         * Writes what is left of the group.
         *
         * @return the ids of the values of the group, by their index in it
         */
        public Ids endGroup() throws IOException {
            writeSegment();
            Ids ids = new Ids(Arrays.copyOf(batches, batchCount));
            inGroup = 0;
            batchCount = 0;
            return ids;
        }

        private void writeSegment() throws IOException {
            if (segment.isEmpty()) {
                return;
            }
            ByteStrings strings = ByteStrings.of(segment);
            FsstEncoder encoder = FsstEncoder.build(strings, false);
            FsstCompressedData compressed = encoder.compress(strings, FsstEncoder.Kernel.SCALAR);
            int[] lengths = compressed.compressedLengths();
            int[] offsets = compressed.offsets();
            byte[] data = compressed.compressedData();
            long table = position;
            write(encoder.exportTable());
            for (int first = 0; first < segment.size(); first += BATCH) {
                int count = Math.min(BATCH, segment.size() - first);
                long batch = position;
                if (batchCount == batches.length) {
                    batches = Arrays.copyOf(batches, batchCount * 2);
                }
                batches[batchCount++] = batch;
                writeVarint(batch - table);
                int longest = 0;
                for (int i = first; i < first + count; i++) {
                    longest = Math.max(longest, lengths[i]);
                }
                int width = longest < 1 << 8 ? 1 : longest < 1 << 16 ? 2 : longest < 1 << 24 ? 3 : 4;
                write(new byte[]{(byte) (count - 1), (byte) width});
                byte[] widths = new byte[count * width];
                for (int i = 0; i < count; i++) {
                    for (int b = 0; b < width; b++) {
                        widths[i * width + b] = (byte) (lengths[first + i] >>> (8 * b));
                    }
                }
                write(widths);
                int start = offsets[first];
                int end = offsets[first + count - 1] + lengths[first + count - 1];
                out.write(data, start, end - start);
                position += end - start;
                for (int i = 0; i < count; i++) {
                    written.written(segment.get(first + i), id(batch, i));
                }
            }
            segment.clear();
        }

        private void write(byte[] bytes) throws IOException {
            out.write(bytes);
            position += bytes.length;
        }

        private void writeVarint(long value) throws IOException {
            while ((value & ~0x7FL) != 0) {
                out.write((int) (value & 0x7F) | 0x80);
                value >>>= 7;
                position++;
            }
            out.write((int) value);
            position++;
        }

        @Override
        public void close() throws IOException {
            writeSegment();
            out.close();
        }
    }

    /**
     * The ids of the values of a group, by their index in it.
     */
    public static final class Ids {

        private final long[] batches;

        private Ids(long[] batches) {
            this.batches = batches;
        }

        /**
         * @return the id of the value with this index in the group
         */
        public long id(long index) {
            return CompressedValues.id(batches[(int) (index >>> BATCH_BITS)], (int) (index & INDEX_MASK));
        }
    }
}
