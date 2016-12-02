package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools;

public class RoBnodeDictionary extends RoDictionary {

	protected RoBnodeDictionary(long[] offSetMap, ByteBuffer[] backingFile) {
		super(offSetMap, backingFile);
	}

	public String getFromId(long id) throws IOException {
		if ((SECOND_BYTE_TRUE & id) == SECOND_BYTE_TRUE) {
			int withoutMask = (int) id;
			long offset = offSetMap[withoutMask];
			return new String(ByteBuffersBackedByFilesTools.readByteArrayAt(
					offset, backingFile), StandardCharsets.UTF_8);
		} else
			return String.valueOf(id);

	}

}
