package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import org.eclipse.rdf4j.model.Literal;

import sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools;
import sib.swiss.swissprot.sparql.ro.values.RoBigIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;

public class RoIntegerDict extends RoDictionary {

	public RoIntegerDict(long[] offSetMap, ByteBuffer[] backingFile) {
		super(offSetMap, backingFile);
	}

	public Literal getFromId(long id) throws IOException {
		if ((SECOND_BYTE_TRUE & id) == SECOND_BYTE_TRUE) {
			long offset = lookupOffset(id);
			byte[] rawvalue = ByteBuffersBackedByFilesTools.readByteArrayAt(
					offset, backingFile);
			return new RoBigIntegerLiteral(new BigInteger(rawvalue));
		} else
			return new RoIntegerLiteral(id);
	}

	private long lookupOffset(long id) {
		return offSetMap[(int) id];
	}
}
