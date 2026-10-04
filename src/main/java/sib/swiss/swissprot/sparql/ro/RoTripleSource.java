package sib.swiss.swissprot.sparql.ro;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.iteration.EmptyIteration;
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
    public CloseableIteration<? extends Statement> getStatements(
            Resource subj, IRI pred, Value obj, Resource... contexts)
            throws QueryEvaluationException {
        if (pred != null && store.getPredicateStore(pred) == null) {
            return new EmptyIteration<>();
        }
        return new RoFilteredTripleIterator(store, subj, pred, obj, contexts);
    }

    @Override
    public ValueFactory getValueFactory() {
        return store.getValueFactory();
    }

}
