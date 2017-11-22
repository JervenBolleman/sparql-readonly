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
        Stream<Statement> stream;
        if (pred != null) {
            try {
                stream = store.getPredicateStore(pred).stream();
            } catch (IOException e) {
                throw new QueryEvaluationException(e);
            }
        } else if (contexts == null || contexts.length == 0) {
            stream = store.getPredicateStores()
                    .values()
                    .stream()
                    .flatMap(RoPredicateStore::stream)
                    .filter(s -> s.getContext() == null);
        } else if (contexts.length == 1) {

            stream = store.getPredicateStores()
                    .values()
                    .stream()
                    .flatMap(RoPredicateStore::stream)
                    .filter(s -> contexts[0].equals(s.getContext()));
        } else {
            Set<Resource> collect = Arrays.stream(contexts)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            stream = store.getPredicateStores()
                    .values()
                    .stream()
                    .flatMap(RoPredicateStore::stream)
                    .filter(s -> s.getContext() != null)
                    .filter(s -> collect.contains(s.getContext()));
        }
        if (subj != null) {
            stream = stream.filter(s -> s.getSubject().equals(subj));
        } else if (obj != null) {
            stream = stream.filter(s -> s.getObject().equals(obj));
        }
        predicateStores = stream.iterator();
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
