package sib.swiss.swissprot.sparql.ro.values;

import java.math.BigInteger;

import org.eclipse.rdf4j.model.impl.IntegerLiteral;

public class RoBigIntegerLiteral extends IntegerLiteral {

	private static final long serialVersionUID = 1L;

	public RoBigIntegerLiteral(BigInteger value) {
		super(value);
	}

	public RoBigIntegerLiteral(long value) {
		super(BigInteger.valueOf(value));
	}
}
