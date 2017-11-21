package sib.swiss.swissprot.sparql.ro.quads;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;

import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;
import sib.swiss.swissprot.sparql.ro.values.RoValue;

public class RoContextStatement implements Statement {

	private static final long serialVersionUID = 1L;
	private final RoResource subject;
	private final RoIri predicate;
	private final RoValue object;
	private final RoResource context;

	public RoContextStatement(RoResource subject, RoIri predicate,
			RoValue object, RoResource context) {
		this.subject = subject;
		this.predicate = predicate;
		this.object = object;
		this.context = context;

	}

	@Override
	public Resource getSubject() {
		return subject;
	}

	@Override
	public IRI getPredicate() {
		return predicate;
	}

	@Override
	public Value getObject() {
		return object;
	}

	@Override
	public Resource getContext() {
		return context;
	}

}
