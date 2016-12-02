package sib.swiss.swissprot.sparql.ro.values;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleLiteral;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;

public class RoIntegerLiteral extends SimpleLiteral {
	private static final long serialVersionUID = 1L;
	private final long value;

	public RoIntegerLiteral(long value) {
		this.value = value;
	}

	@Override
	public String stringValue() {
		return Long.toString(value);
	}

	@Override
	public IRI getDatatype() {
		return XMLSchema.INTEGER;
	}
}
