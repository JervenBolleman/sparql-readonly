package sib.swiss.swissprot.sparql.ro;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.QueryEvaluationException;

public class RoFilteredTripleIterator implements
        CloseableIteration<Statement> {

    final Iterator<Statement> predicateStores;

    /**
     * @param subj null for any subject
     * @param pred null for any predicate
     * @param obj null for any object
     * @param contexts none for any context, null in it for the default graph
     */
    public RoFilteredTripleIterator(RoStore store, Resource subj, IRI pred,
            Value obj, Resource[] contexts) {
        this(store, subj, pred, obj, contexts, PushedFilter.NONE);
    }

    /**
     * @param filter leaves out statements whose object a FILTER rejects
     */
    RoFilteredTripleIterator(RoStore store, Resource subj, IRI pred,
            Value obj, Resource[] contexts, PushedFilter filter) {
        Stream<RoPredicateStore> stores;
        if (pred != null) {
            RoPredicateStore predicateStore = store.getPredicateStore(pred);
            stores = predicateStore == null ? Stream.empty() : Stream.of(predicateStore);
        } else {
            stores = store.getPredicateStores().values().stream();
        }
        // the subject and object are selected by Parquet in the triple lists
        Stream<Statement> stream = stores.flatMap(p -> p.stream(subj, obj, filter));
        if (contexts != null && contexts.length > 0) {
            List<Resource> inContexts = Arrays.asList(contexts);
            stream = stream.filter(s -> inContexts.stream().anyMatch(c -> Objects.equals(c, s.getContext())));
        }
        predicateStores = stream.iterator();
    }

    @Override
    public boolean hasNext() {
        return predicateStores.hasNext();
    }

    @Override
    public Statement next() {
        return predicateStores.next();
    }

    @Override
    public void remove() {
        throw new QueryEvaluationException("RO does not support removal");

    }

    @Override
    public void close() {

    }
}
