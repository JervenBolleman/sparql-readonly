package sib.swiss.swissprot.sparql.ro.values;

import org.eclipse.rdf4j.model.BNode;

public class RoBnode implements BNode {
	private static final long serialVersionUID = 1L;

	private final long id;

	public RoBnode(long id) {
		super();
		this.id = id;
	}

	@Override
	public String stringValue() {
		return "#_" + getID();
	}

	@Override
	public String getID() {
		return String.valueOf(id);
	}

}
