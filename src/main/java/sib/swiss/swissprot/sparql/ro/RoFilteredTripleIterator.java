package sib.swiss.swissprot.sparql.ro;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.QueryEvaluationException;

public class RoFilteredTripleIterator implements
        CloseableIteration<Statement, QueryEvaluationException> {

    final Iterator<Statement> predicateStores;

    public RoFilteredTripleIterator(RoStore store, Resource subj, IRI pred,
            Value obj, Resource[] contexts) {
        if (pred != null) {
            try {
                predicateStores = store.getPredicateStore(pred)
                        .iterator();
            } catch (IOException e) {
                throw new QueryEvaluationException(e);
            }
        } else {
            predicateStores = store.getPredicateStores()
                    .values()
                    .stream()
                    .map(RoPredicateStore::spliterator)
                    .flatMap(s -> StreamSupport.stream(s, true))
                    .iterator();
        }
    }

    @Override
    public boolean hasNext() throws QueryEvaluationException {
        return predicateStores.hasNext();
    }

    @Override
    public Statement next() throws QueryEvaluationException {
        return predicateStores.next();
    }

    @Override
    public void remove() throws QueryEvaluationException {
        throw new QueryEvaluationException("RO does not support removal");

    }

    @Override
    public void close() throws QueryEvaluationException {

    }
}
