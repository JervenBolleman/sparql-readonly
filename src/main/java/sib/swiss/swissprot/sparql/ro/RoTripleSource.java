package sib.swiss.swissprot.sparql.ro;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    public CloseableIteration<? extends Statement, QueryEvaluationException> getStatements(
            Resource subj, IRI pred, Value obj, Resource... contexts)
            throws QueryEvaluationException {
        try {
            RoPredicateStore predicateStore = store.getPredicateStore(pred);
            if (predicateStore == null)
                return new EmptyIteration<>();
            else
                return new RoFilteredTripleIterator(store, subj, pred, obj, contexts);
        } catch (IOException ex) {
            throw new QueryEvaluationException(ex);
        }
    }

    @Override
    public ValueFactory getValueFactory() {
        return store.getValueFactory();
    }

}
