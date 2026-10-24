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
    private final PushedFilter filter;

    public RoTripleSource(RoStore store) {
        this(store, PushedFilter.NONE);
    }

    /**
     * @param filter on the objects of every pattern, see {@link PushedFilter}
     */
    RoTripleSource(RoStore store, PushedFilter filter) {
        this.store = store;
        this.filter = filter;
    }

    @Override
    public CloseableIteration<? extends Statement> getStatements(
            Resource subj, IRI pred, Value obj, Resource... contexts)
            throws QueryEvaluationException {
        if (pred != null && store.getPredicateStore(pred) == null) {
            return new EmptyIteration<>();
        }
        return new RoFilteredTripleIterator(store, subj, pred, obj, contexts, filter);
    }

    @Override
    public ValueFactory getValueFactory() {
        return store.getValueFactory();
    }

}
