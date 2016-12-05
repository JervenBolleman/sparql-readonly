package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.nio.ByteBuffer;

public class RoBnodeDictionary extends RoDictionary {

	public RoBnodeDictionary(long[] offSetMap, ByteBuffer[] buffers) {
		super(offSetMap, buffers);
	}

	public String getFromId(long id) throws IOException {
		if ((SECOND_BYTE_TRUE & id) == SECOND_BYTE_TRUE) {
			int withoutMask = (int) id;
			long offset = getOffset(withoutMask);
			return readStringAt(offset);
		} else
			return String.valueOf(id);

	}

}
