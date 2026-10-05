package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.function.LongPredicate;

/**
 * Finds the offset of a value in {@link LinearValues} by the hash of the
 * value. An open addressing table with linear probing, memory mapped: a
 * long with the number of slots, a power of two, then the slots. A slot
 * holds 0 if it is empty, otherwise 24 bits of the hash of its value above
 * the offset of the value plus one, in 40 bits. So offsets go up to 1 TiB,
 * and most slots of other values are passed over without reading them.
 */
public final class HashIndex implements Closeable {

    private static final int OFFSET_BITS = 40;
    private static final long OFFSET_MASK = (1L << OFFSET_BITS) - 1;
    /**
     * The most slots in use, as a share of all slots.
     */
    private static final double LOAD = 0.7;
    private static final HashIndex EMPTY = new HashIndex(null, MemorySegment.NULL, 0);

    private final Arena arena;
    private final MemorySegment slots;
    private final long mask;

    private HashIndex(Arena arena, MemorySegment slots, long capacity) {
        this.arena = arena;
        this.slots = slots;
        this.mask = capacity - 1;
    }

    /**
     * @return the index of the file, finding nothing if there is no file
     */
    public static HashIndex open(File file) throws IOException {
        if (!file.exists() || file.length() == 0) {
            return EMPTY;
        }
        Arena arena = Arena.ofShared();
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
            long capacity = segment.get(ValueLayout.JAVA_LONG_UNALIGNED, 0);
            return new HashIndex(arena, segment, capacity);
        } catch (IOException | RuntimeException e) {
            arena.close();
            throw e;
        }
    }

    /**
     * The hash of a value in a group of values, such as a namespace.
     */
    public static long hash(byte[] value, long group) {
        // FNV-1a, then mixed as in MurmurHash3, so that every bit counts
        long h = 0xcbf29ce484222325L ^ group * 0x9E3779B97F4A7C15L;
        for (byte b : value) {
            h ^= b & 0xFF;
            h *= 0x100000001b3L;
        }
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    private static long tag(long hash) {
        return hash >>> OFFSET_BITS;
    }

    /**
     * @param hash the hash of the value, see {@link #hash(byte[], long)}
     * @param matches whether the value at an offset is the one looked for
     * @return the offset of the value, -1 if it is not there
     */
    public long find(long hash, LongPredicate matches) {
        if (slots == MemorySegment.NULL) {
            return -1;
        }
        long tag = tag(hash);
        for (long slot = hash & mask;; slot = (slot + 1) & mask) {
            long entry = slots.get(ValueLayout.JAVA_LONG_UNALIGNED, Long.BYTES * (slot + 1));
            if (entry == 0) {
                return -1;
            }
            if (entry >>> OFFSET_BITS == tag) {
                long offset = (entry & OFFSET_MASK) - 1;
                if (matches.test(offset)) {
                    return offset;
                }
            }
        }
    }

    @Override
    public void close() {
        if (arena != null) {
            arena.close();
        }
    }

    /**
     * Writes an index for at most a given number of values. The table is
     * filled in a memory mapped file, so its size is not bounded by the
     * heap.
     */
    public static final class Writer implements Closeable {

        private final Arena arena = Arena.ofShared();
        private final MemorySegment slots;
        private final long mask;

        /**
         * @param values the most values that will be added
         */
        public Writer(File file, long values) throws IOException {
            long capacity = Long.highestOneBit(Math.max(1, (long) Math.ceil(values / LOAD)));
            if (capacity * LOAD < values) {
                capacity <<= 1;
            }
            this.mask = capacity - 1;
            try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                // a new file reads as zeros: all slots empty
                slots = channel.map(FileChannel.MapMode.READ_WRITE, 0, Long.BYTES * (capacity + 1), arena);
            } catch (IOException | RuntimeException e) {
                arena.close();
                throw e;
            }
            slots.set(ValueLayout.JAVA_LONG_UNALIGNED, 0, capacity);
        }

        /**
         * @param hash the hash of the value, see {@link #hash(byte[], long)}
         * @param offset the offset of the value, which is not in the index
         */
        public void add(long hash, long offset) throws IOException {
            if (offset + 1 > OFFSET_MASK) {
                throw new IOException("Values beyond " + OFFSET_MASK + " bytes can not be indexed");
            }
            long entry = (tag(hash) << OFFSET_BITS) | (offset + 1);
            for (long slot = hash & mask;; slot = (slot + 1) & mask) {
                long at = Long.BYTES * (slot + 1);
                if (slots.get(ValueLayout.JAVA_LONG_UNALIGNED, at) == 0) {
                    slots.set(ValueLayout.JAVA_LONG_UNALIGNED, at, entry);
                    return;
                }
            }
        }

        @Override
        public void close() {
            slots.force();
            arena.close();
        }
    }
}
