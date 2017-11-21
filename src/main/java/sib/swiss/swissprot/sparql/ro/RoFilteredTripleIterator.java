package sib.swiss.swissprot.sparql.ro;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        } else if (contexts == null || contexts.length == 0) {
            Set<RoPredicateStore> st = store.getPredicateStores()
                    .values()
                    .stream()
                    .collect(Collectors.toSet());

            predicateStores = st.stream()
                    .flatMap(RoPredicateStore::stream)
                    .filter(s -> s.getContext() == null)
                    .iterator();
        } else if (contexts.length == 1) {

            predicateStores = store.getPredicateStores()
                    .values()
                    .stream()
                    .flatMap(RoPredicateStore::stream)
                    .filter(s -> contexts[0].equals(s.getContext()))
                    .iterator();
        } else {
            Set<Resource> collect = Arrays.stream(contexts)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            predicateStores = store.getPredicateStores()
                    .values()
                    .stream()
                    .flatMap(RoPredicateStore::stream)
                    .filter(s -> s.getContext() != null)
                    .filter(s -> collect.contains(s.getContext()))
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
