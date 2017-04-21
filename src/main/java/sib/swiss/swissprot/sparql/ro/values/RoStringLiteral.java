package sib.swiss.swissprot.sparql.ro.values;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleLiteral;

import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;

public class RoStringLiteral extends SimpleLiteral implements RoLiteral {
	private static final long serialVersionUID = 1L;
	private final long id;
	private final RoLiteralDict dict;

	public RoStringLiteral(long id, RoLiteralDict dict) {
		this.id = id;
		this.dict = dict;
	}

	@Override
	public String stringValue() {
		return dict.stringValue(id);
	}

	@Override
	public IRI getDatatype() {
		return dict.dataType(id);
	}

	@Override
	public long getLongId() {
		return id;
	}
}
