package sib.swiss.swissprot.sparql.ro;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource;

public class RoTripleSource implements TripleSource {

	private final RoStore store;

	public RoTripleSource(RoStore store) {
		this.store = store;
	}

	@Override
	public CloseableIteration<? extends Statement, QueryEvaluationException> getStatements(
			Resource subj, IRI pred, Value obj, Resource... contexts)
			throws QueryEvaluationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ValueFactory getValueFactory() {
		// TODO Auto-generated method stub
		return store.getValueFactory();
	}

}
