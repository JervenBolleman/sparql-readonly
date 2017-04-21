package sib.swiss.swissprot.sparql.ro.values;

import org.eclipse.rdf4j.model.BNode;

public class RoBnode implements BNode, RoResource {
	private static final long serialVersionUID = 1L;

	private final long id;

	public RoBnode(long id) {
		super();
		this.id = id;
	}

	@Override
	public String stringValue() {
		return "#_" + getLongId();
	}

	@Override
	public String getID() {
		return String.valueOf(id);
	}

	@Override
	public long getLongId() {
		return id;
	}

	public static RoBnode fromLongId(long id) {
		return new RoBnode(id);
	}
}
