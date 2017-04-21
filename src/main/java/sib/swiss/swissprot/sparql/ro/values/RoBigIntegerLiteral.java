package sib.swiss.swissprot.sparql.ro.values;

import java.math.BigInteger;

import org.eclipse.rdf4j.model.impl.IntegerLiteral;

public class RoBigIntegerLiteral extends IntegerLiteral implements RoLiteral {

	private static final long serialVersionUID = 1L;
	private final long id;

	public RoBigIntegerLiteral(long id, BigInteger value) {
		super(value);
		this.id = id;
	}

	public RoBigIntegerLiteral(long value) {
		super(BigInteger.valueOf(value));
		this.id = value;
	}

	@Override
	public long getLongId() {
		return id;
	}
}
