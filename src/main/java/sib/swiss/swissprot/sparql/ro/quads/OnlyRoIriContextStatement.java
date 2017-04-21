package sib.swiss.swissprot.sparql.ro.quads;

import org.eclipse.rdf4j.model.Statement;

import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class OnlyRoIriContextStatement implements Statement {

	private static final long serialVersionUID = 1L;
	private final long subject;
	private final long predicate;
	private final long object;
	private final long context;
	private final RoIriDictionary iriDictionary;

	public OnlyRoIriContextStatement(long subject, long predicate, long object,
			long context, RoIriDictionary iriDictionary) {
		this.subject = subject;
		this.predicate = predicate;
		this.object = object;
		this.context = context;
		this.iriDictionary = iriDictionary;

	}

	@Override
	public RoIri getSubject() {
		return new RoIri(subject, iriDictionary);
	}

	@Override
	public RoIri getPredicate() {
		return new RoIri(predicate, iriDictionary);
	}

	@Override
	public RoIri getObject() {
		return new RoIri(object, iriDictionary);
	}

	@Override
	public RoIri getContext() {
		return new RoIri(context, iriDictionary);
	}

}
