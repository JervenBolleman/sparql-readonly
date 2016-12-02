package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools;

public class RoIriNamespaceDictionary extends RoDictionary {

	public RoIriNamespaceDictionary(long[] offSetMap, ByteBuffer[] backingFile) {
		super(offSetMap, backingFile);
	}

	public String getLocalNameFromId(long id) throws IOException {
		int withoutMask = (int) id;
		long offset = offSetMap[withoutMask];
		return new String(ByteBuffersBackedByFilesTools.readByteArrayAt(offset,
				backingFile), StandardCharsets.UTF_8);
	}
}
