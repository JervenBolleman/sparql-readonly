package sib.swiss.swissprot.sparql.ro;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.QueryEvaluationException;

public class RoFilteredTripleIterator implements
		CloseableIteration<Statement, QueryEvaluationException> {

	public RoFilteredTripleIterator(RoStore store, Resource subj, IRI pred,
			Value obj, Resource[] contexts) {
		if (pred != null) {
			// iterate(store, subj, pred);
		}
	}

	@Override
	public boolean hasNext() throws QueryEvaluationException {
		return false;
	}

	@Override
	public Statement next() throws QueryEvaluationException {
		return null;
	}

	@Override
	public void remove() throws QueryEvaluationException {
		throw new QueryEvaluationException("RO does not support removal");

	}

	@Override
	public void close() throws QueryEvaluationException {

	}
}
