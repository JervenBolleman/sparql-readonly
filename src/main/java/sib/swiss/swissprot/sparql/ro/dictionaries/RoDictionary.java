package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools;

abstract class RoDictionary {
	protected static final long SECOND_BYTE_TRUE = 1L << 63;
	protected final long[] offSetMap;
	protected final ByteBuffer[] buffers;

	protected RoDictionary(long[] offSetMap, ByteBuffer[] buffers) {
		super();
		this.offSetMap = offSetMap;
		this.buffers = buffers;
	}

	protected String readStringAt(long offset) throws IOException {
		return new String(readBytesAt(offset), StandardCharsets.UTF_8);
	}

	protected byte[] readBytesAt(long offset) throws IOException {
		return ByteBuffersBackedByFilesTools.readByteArrayAt(offset, buffers);
	}

	protected long getOffset(int withoutMask) {
		return offSetMap[withoutMask];
	}
}
