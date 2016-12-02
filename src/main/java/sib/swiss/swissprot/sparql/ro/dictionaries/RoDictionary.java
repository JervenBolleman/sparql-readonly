package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.nio.ByteBuffer;

abstract class RoDictionary {
	protected static final long SECOND_BYTE_TRUE = 1L << 63;
	protected final long[] offSetMap;
	protected final ByteBuffer[] backingFile;

	protected RoDictionary(long[] offSetMap, ByteBuffer[] backingFile) {
		super();
		this.offSetMap = offSetMap;
		this.backingFile = backingFile;
	}

}
