package sib.swiss.swissprot.sparql.ro;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public final class ByteBuffersBackedByFilesTools {
	/*
	 * This makes a buffer size of 960 MB.
	 */
	private static int BUCKET_SIZE = Long.BYTES * 15_000_000;
	private static int INTS_IN_BUCKET_SIZE = BUCKET_SIZE / Integer.BYTES;
	private static int LONGS_IN_BUCKET_SIZE = BUCKET_SIZE / Long.BYTES;

	/**
	 * Open a byte buffer against a memory mapped file. The memory mapped files
	 * do need addressable memory space. However, this memory is not in the heap
	 * and may actually not be claimed. This means that for the large datasets a
	 * 32bit machine is not good enough.
	 *
	 * @param source
	 *            the file containing the mapped accessions or sorted data.
	 * @return a ByteBuffer in which can be wrapped in an int or long buffer.
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static ByteBuffer[] openByteBuffer(final Path source)
			throws FileNotFoundException, IOException {
		final RandomAccessFile randomAccessFile = new RandomAccessFile(
				source.toFile(), "r");
		final FileChannel channel = randomAccessFile.getChannel();

		int numberOfBuffers = ((int) (randomAccessFile.length() / BUCKET_SIZE)) + 1;
		ByteBuffer[] buffers = new ByteBuffer[numberOfBuffers];

		long channelSize = channel.size();
		long totalCapacity = 0;
		for (int i = 0; i < numberOfBuffers; i++) {
			int bufferSize = BUCKET_SIZE;
			if (i + 1 == numberOfBuffers) {
				// Every buffer (bucket) is the maximum size except the last
				// one.
				bufferSize = (int) (channelSize - totalCapacity);
			}
			assert bufferSize >= 0;
			assert bufferSize <= channelSize;
			// Have to work in long space for calculating the offset
			buffers[i] = channel.map(FileChannel.MapMode.READ_ONLY,
					totalCapacity, bufferSize);
			totalCapacity = totalCapacity + buffers[i].capacity();

			assert totalCapacity <= channelSize;
		}
		channel.close();

		channel.close();
		randomAccessFile.close();
		return buffers;
	}

	/**
	 * Load a readonly byte buffer of ints usually a sort file.
	 *
	 * @param source
	 *            the file to load from
	 * @return an order array of document id's as sorted
	 * @throws java.io.IOException
	 */
	public static IntBuffer[] openIntBuffer(final Path sourceFile)
			throws IOException {
		ByteBuffer[] openByteBuffers = openByteBuffer(sourceFile);
		IntBuffer[] intBuffers = new IntBuffer[openByteBuffers.length];
		for (int i = 0; i < openByteBuffers.length; i++) {
			intBuffers[i] = openByteBuffers[i].asIntBuffer();
		}
		return intBuffers;
	}

	/**
	 * Load a readonly byte buffer of longs usually a file containing all keys.
	 *
	 * @param source
	 *            the file to load from
	 * @return an order array of document keys as inserted into the index
	 * @throws java.io.IOException
	 */
	public static LongBuffer[] openLongBuffer(final Path source)
			throws IOException {

		ByteBuffer[] openByteBuffers = openByteBuffer(source);
		LongBuffer[] longBuffers = new LongBuffer[openByteBuffers.length];
		for (int i = 0; i < openByteBuffers.length; i++)
			longBuffers[i] = openByteBuffers[i].asLongBuffer();

		return longBuffers;
	}

	public static long getLongAtIndexInLongBuffers(final int index,
			final LongBuffer[] buffers) {
		int local = index;
		for (int basket = 0; basket < buffers.length; basket++) {
			int limit = LONGS_IN_BUCKET_SIZE;
			if (local < limit) {
				return buffers[basket].get(local);
			} else
				local = local - limit;
		}
		throw new RuntimeException(
				"DocNumber not in internalIdsMapBuffer range:" + index);
	}

	public static int getIntAtIndexInIntBuffers(final int index,
			final IntBuffer[] buffers) {
		int local = index;
		for (IntBuffer buffer : buffers) {
			int limit = INTS_IN_BUCKET_SIZE;
			if (local < limit) {
				return buffer.get(local);
			} else
				local = local - limit;
		}
		throw new RuntimeException("DocNumber not in buffers range:" + index);
	}

	public static byte[] readByteArrayAt(long position, ByteBuffer[] buffers)
			throws IOException {

		int bufferToStartLookInto = 0;
		int contentLength = 0;
		int positionInCurrentBufferAsInt = 0;
		{
			long positionInCurrentBuffer = position;
			for (; bufferToStartLookInto < buffers.length; bufferToStartLookInto++) {
				int limit = BUCKET_SIZE;
				if (positionInCurrentBuffer < limit) {
					break;
				} else
					positionInCurrentBuffer = positionInCurrentBuffer - limit;
			}
			assert positionInCurrentBuffer <= Integer.MAX_VALUE
					&& positionInCurrentBuffer >= 0;
			positionInCurrentBufferAsInt = (int) positionInCurrentBuffer;
		}
		try {
			contentLength = buffers[bufferToStartLookInto]
					.getInt(positionInCurrentBufferAsInt);
			positionInCurrentBufferAsInt = positionInCurrentBufferAsInt
					+ Integer.BYTES;
		} catch (IndexOutOfBoundsException e) {
			// Deals with the case of a position being written across the
			// boundary.
			int j = 0;
			for (int i = positionInCurrentBufferAsInt; i < buffers[bufferToStartLookInto]
					.capacity(); i++, j++)
				contentLength = contentLength << 8
						| ((buffers[bufferToStartLookInto].get(i)) & 0xff);
			bufferToStartLookInto++;
			positionInCurrentBufferAsInt = 0;
			for (; j < Integer.BYTES; j++, positionInCurrentBufferAsInt++)
				contentLength = contentLength << 8
						| ((buffers[bufferToStartLookInto]
								.get(positionInCurrentBufferAsInt)) & 0xff);
		}

		if (contentLength + positionInCurrentBufferAsInt < buffers[bufferToStartLookInto]
				.capacity()) {
			ByteBuffer duplicate = buffers[bufferToStartLookInto].duplicate();
			duplicate.position(positionInCurrentBufferAsInt);
			byte[] data = new byte[contentLength];
			duplicate.get(data);
			return data;
		} else
			return readDataIntoByteArray(buffers, bufferToStartLookInto,
					contentLength, positionInCurrentBufferAsInt);
	}

	public static byte[] readDataIntoByteArray(ByteBuffer[] buffers,
			int bufferToStartLookInto, int contentLength,
			int positionInCurrentBufferAsInt) {
		byte[] data = new byte[contentLength];
		for (int i = 0; i < data.length; i++)
			try {
				data[i] = buffers[bufferToStartLookInto]
						.get(positionInCurrentBufferAsInt);
				positionInCurrentBufferAsInt++;
			} catch (IndexOutOfBoundsException e) {
				// Deals with the case of an entry being written across the
				// boundary.
				bufferToStartLookInto++;
				positionInCurrentBufferAsInt = 0;
				data[i] = buffers[bufferToStartLookInto]
						.get(positionInCurrentBufferAsInt);
				positionInCurrentBufferAsInt++;
			}
		return data;
	}
}