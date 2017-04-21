package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Optional;

import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.impl.IntegerLiteral;

import sib.swiss.swissprot.sparql.ro.values.RoBigIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;

public class RoIntegerDict
		extends RoDictionary<RoIntegerLiteral, IntegerLiteral> {

	public RoIntegerDict(long[] offSetMap, ByteBuffer[] backingFile) {
		super(offSetMap, backingFile);
	}

	public Literal getFromId(long id) throws IOException {
		if ((SECOND_BYTE_TRUE & id) == SECOND_BYTE_TRUE) {
			long offset = lookupOffset(id);
			byte[] rawvalue = readBytesAt(offset);
			return new RoBigIntegerLiteral(id, new BigInteger(rawvalue));
		} else
			return new RoIntegerLiteral(id);
	}

	@Override
	public Optional<RoIntegerLiteral> find(IntegerLiteral value) {
		// TODO Auto-generated method stub
		return null;
	}
}
