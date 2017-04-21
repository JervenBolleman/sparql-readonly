package sib.swiss.swissprot.sparql.ro;

import java.io.IOException;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.QueryEvaluationException;

public class RoFilteredTripleIterator implements
		CloseableIteration<Statement, QueryEvaluationException> {
	final RoPredicateStore predicateStore;

	public RoFilteredTripleIterator(RoStore store, Resource subj, IRI pred,
			Value obj, Resource[] contexts) {
		if (pred != null) {
			try {
				predicateStore = store.getPredicateStore(pred);
			} catch (IOException e) {
				throw new QueryEvaluationException(e);
			}
		} else {
			predicateStore = null;
		}
	}

	@Override
	public boolean hasNext() throws QueryEvaluationException {
		if (predicateStore == null)
			return false;
		return false;
	}

	@Override
	public Statement next() throws QueryEvaluationException {
		if (predicateStore == null)
			return null;
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
