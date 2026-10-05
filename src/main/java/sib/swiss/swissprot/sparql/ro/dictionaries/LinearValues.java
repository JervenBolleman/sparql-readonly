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

/**
 * Values one after the other in a file, uncompressed, in the order they were
 * written: per value its length in bytes as an unsigned LEB128 varint, then
 * its UTF-8 bytes. A value is known by its offset, the position of its
 * length in the file. The file is memory mapped.
 */
public final class LinearValues implements Closeable {

    private static final LinearValues EMPTY = new LinearValues(null, MemorySegment.NULL);

    private final Arena arena;
    private final MemorySegment values;

    private LinearValues(Arena arena, MemorySegment values) {
        this.arena = arena;
        this.values = values;
    }

    /**
     * @return the values of the file, none if there is no file
     */
    public static LinearValues open(File file) throws IOException {
        if (!file.exists() || file.length() == 0) {
            return EMPTY;
        }
        Arena arena = Arena.ofShared();
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            return new LinearValues(arena, channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena));
        } catch (IOException | RuntimeException e) {
            arena.close();
            throw e;
        }
    }

    /**
     * @return the size of the file in bytes
     */
    public long size() {
        return values.byteSize();
    }

    /**
     * @return the value at the offset
     */
    public String string(long offset) {
        long length = 0;
        int shift = 0;
        long at = offset;
        byte b;
        do {
            b = values.get(ValueLayout.JAVA_BYTE, at++);
            length |= (long) (b & 0x7F) << shift;
            shift += 7;
        } while (b < 0);
        byte[] bytes = values.asSlice(at, length).toArray(ValueLayout.JAVA_BYTE);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * @return whether the value at the offset is these UTF-8 bytes
     */
    public boolean equalsAt(long offset, byte[] value) {
        long length = 0;
        int shift = 0;
        long at = offset;
        byte b;
        do {
            b = values.get(ValueLayout.JAVA_BYTE, at++);
            length |= (long) (b & 0x7F) << shift;
            shift += 7;
        } while (b < 0);
        if (length != value.length) {
            return false;
        }
        return MemorySegment.mismatch(values, at, at + length, MemorySegment.ofArray(value), 0, value.length) == -1;
    }

    @Override
    public void close() {
        if (arena != null) {
            arena.close();
        }
    }

    /**
     * Writes values one after the other.
     */
    public static final class Writer implements Closeable {

        private final OutputStream out;
        private long position;

        public Writer(File file) throws IOException {
            this.out = new BufferedOutputStream(new FileOutputStream(file), 1 << 16);
        }

        /**
         * @return the offset of the value
         */
        public long add(byte[] value) throws IOException {
            long offset = position;
            int length = value.length;
            while ((length & ~0x7F) != 0) {
                out.write((length & 0x7F) | 0x80);
                length >>>= 7;
                position++;
            }
            out.write(length);
            position++;
            out.write(value);
            position += value.length;
            return offset;
        }

        /**
         * @return the offset the next value gets
         */
        public long position() {
            return position;
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }
}
