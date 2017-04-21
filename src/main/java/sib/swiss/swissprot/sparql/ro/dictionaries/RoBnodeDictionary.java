package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

import org.eclipse.rdf4j.model.BNode;

import sib.swiss.swissprot.sparql.ro.values.RoBnode;

public class RoBnodeDictionary extends RoDictionary<RoBnode, BNode> {

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

	@Override
	public Optional<RoBnode> find(BNode subject) {
		// TODO Auto-generated method stub
		return null;
	}

	public static RoBnodeDictionary load() {
		// TODO Auto-generated method stub
		return null;
	}

}
