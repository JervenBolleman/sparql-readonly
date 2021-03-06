package sib.swiss.swissprot.sparql.ro;

import java.util.Iterator;

import org.eclipse.rdf4j.IsolationLevel;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.iteration.CloseableIteratorIteration;
import org.eclipse.rdf4j.common.iteration.EmptyIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.algebra.evaluation.EvaluationStrategy;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.BindingAssigner;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.CompareOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.ConjunctiveConstraintSplitter;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.ConstantOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.DisjunctiveConstraintOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.FilterOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.IterativeEvaluationOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.OrderLimitOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.QueryModelNormalizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.SameTermFilterOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.StrictEvaluationStrategy;
import org.eclipse.rdf4j.query.impl.EmptyBindingSet;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.UnknownSailTransactionStateException;
import org.eclipse.rdf4j.sail.UpdateContext;

public class RoConnection implements SailConnection {

    private final RoStore store;
    private final RoTripleSource tripleSource;

    public RoConnection(RoStore store) {
        super();
        this.store = store;
        tripleSource = new RoTripleSource(store);
    }

    @Override
    public boolean isOpen() throws SailException {
        return true;
    }

    @Override
    public void close() throws SailException {

    }

    @Override
    public CloseableIteration<? extends BindingSet, QueryEvaluationException> evaluate(
            TupleExpr tupleExpr, Dataset dataset, BindingSet bindings,
            boolean includeInferred) throws SailException {
        try {

            EvaluationStrategy strategy = new StrictEvaluationStrategy(
                    tripleSource, store.getFederatedServiceResolver());
            tupleExpr = tupleExpr.clone();
            new BindingAssigner().optimize(tupleExpr, dataset, bindings);
            new ConstantOptimizer(strategy).optimize(tupleExpr, dataset,
                    bindings);
            new CompareOptimizer().optimize(tupleExpr, dataset, bindings);
            new ConjunctiveConstraintSplitter().optimize(tupleExpr, dataset,
                    bindings);
            new DisjunctiveConstraintOptimizer().optimize(tupleExpr, dataset,
                    bindings);
            new SameTermFilterOptimizer()
                    .optimize(tupleExpr, dataset, bindings);
            new QueryModelNormalizer().optimize(tupleExpr, dataset, bindings);

            new IterativeEvaluationOptimizer().optimize(tupleExpr, dataset,
                    bindings);
            new FilterOptimizer().optimize(tupleExpr, dataset, bindings);
            new OrderLimitOptimizer().optimize(tupleExpr, dataset, bindings);

            return strategy.evaluate(tupleExpr, EmptyBindingSet.getInstance());
        } catch (QueryEvaluationException e) {
            throw new SailException(e);
        }
    }

    @Override
    public CloseableIteration<? extends Resource, SailException> getContextIDs()
            throws SailException {
        return new EmptyIteration<>();
    }

    @Override
    public CloseableIteration<? extends Statement, SailException> getStatements(
            Resource subj, IRI pred, Value obj, boolean includeInferred,
            Resource... contexts) throws SailException {

        final RoFilteredTripleIterator fti = new RoFilteredTripleIterator(
                store, subj, pred, obj, contexts);
        return new CloseableIteratorIteration<Statement, SailException>() {

            @Override
            public boolean hasNext() throws SailException {
                try {
                    return fti.hasNext();
                } catch (QueryEvaluationException e) {
                    throw new SailException(e);
                }
            }

            @Override
            public Statement next() throws SailException {
                try {
                    return fti.next();
                } catch (QueryEvaluationException e) {
                    throw new SailException(e);
                }
            }

            @Override
            protected void handleClose() throws SailException {
                try {
                    fti.close();
                } catch (QueryEvaluationException e) {
                    throw new SailException(e);
                }
                super.handleClose();
            }
        };

    }

    @Override
    public long size(Resource... contexts) throws SailException {

        try (final RoFilteredTripleIterator bedFileFilterReader = new RoFilteredTripleIterator(
                store, null, null, null, contexts)) {
            long count = 0L;
            try {
                while (bedFileFilterReader.hasNext()) {
                    bedFileFilterReader.next();
                    count++;
                }
            } catch (QueryEvaluationException e) {
                throw new SailException(e);
            }
            return count;
        }
    }

// @Override
// public void begin() throws SailException {
// throw new SailException("RO sparql can not be updated via SPARQL");
// }
//
// @Override
// public void prepare() throws SailException {
// // TODO Auto-generated method stub
//
// }
    @Override
    public void commit() throws SailException {
        throw new SailException("RO sparql can not be updated via SPARQL");

    }

    @Override
    public void rollback() throws SailException {
        // TODO Auto-generated method stub

    }

    // @Override
    // public boolean isActive() throws UnknownSailTransactionStateException {
    // return false;
    // }
    @Override
    public void addStatement(Resource subj, IRI pred, Value obj,
            Resource... contexts) throws SailException {
        throw new SailException("RO sparql can not be updated via SPARQL");

    }

    @Override
    public void removeStatements(Resource subj, IRI pred, Value obj,
            Resource... contexts) throws SailException {
        throw new SailException("RO sparql can not be updated via SPARQL");

    }

    // @Override
    // public void startUpdate(UpdateContext op) throws SailException {
    // throw new SailException("RO sparql can not be updated via SPARQL");
    //
    // }
    //
    // @Override
    // public void addStatement(UpdateContext op, Resource subj, URI pred,
    // Value obj, Resource... contexts) throws SailException {
    // throw new SailException("RO sparql can not be updated via SPARQL");
    //
    // }
    //
    // @Override
    // public void removeStatement(UpdateContext op, Resource subj, URI pred,
    // Value obj, Resource... contexts) throws SailException {
    // throw new SailException("RO sparql can not be updated via SPARQL");
    //
    // }
    //
    // @Override
    // public void endUpdate(UpdateContext op) throws SailException {
    // throw new SailException("RO sparql can not be updated via SPARQL");
    //
    // }
    @Override
    public void clear(Resource... contexts) throws SailException {
        throw new SailException("RO sparql can not be updated via SPARQL");

    }

    @Override
    public CloseableIteration<? extends Namespace, SailException> getNamespaces()
            throws SailException {

        return new CloseableIteratorIteration<Namespace, SailException>() {
            private Iterator<RoNamespace> namespaces = store.getNamespaces().iterator();

            @Override
            public boolean hasNext() throws SailException {
                return namespaces.hasNext();
            }

            @Override
            public RoNamespace next() throws SailException {
                return namespaces.next();
            }
        ;
    }

    ;
	}

	@Override
    public String getNamespace(String prefix) throws SailException {
       return store.getNamespaces()
                .stream()
                .map(RoNamespace::getPrefix)
                .filter(n -> {
                    if (prefix == null && n == null) {
                        return true;
                    } else if (prefix != null && prefix.equals(n)) {
                        return true;
                    } else {
                        return false;
                    }
                })
                .findAny()
                .orElse(null);

    }

    @Override
    public void setNamespace(String prefix, String name) throws SailException {
        throw new SailException("RO sparql can not be updated via SPARQL");

    }

    @Override
    public void removeNamespace(String prefix) throws SailException {
        throw new SailException("RO sparql can not be updated via SPARQL");

    }

    @Override
    public void clearNamespaces() throws SailException {
        throw new SailException("RO sparql can not be updated via SPARQL");

    }

    @Override
    public void addStatement(UpdateContext arg0, Resource arg1, IRI arg2,
            Value arg3, Resource... arg4) throws SailException {
        throw new SailException("RO sparql can not be updated via SPARQL");
    }

    @Override
    public void begin() throws SailException {
        throw new SailException("RO sparql can not be updated via SPARQL");

    }

    @Override
    public void endUpdate(UpdateContext arg0) throws SailException {
        throw new SailException("RO sparql can not be updated via SPARQL");

    }

    @Override
    public boolean isActive() throws UnknownSailTransactionStateException {
        return false;
    }

    @Override
    public void prepare() throws SailException {
        throw new SailException("RO sparql can not be updated via SPARQL");
    }

    @Override
    public void removeStatement(UpdateContext arg0, Resource arg1, IRI arg2,
            Value arg3, Resource... arg4) throws SailException {
        throw new SailException("RO sparql can not be updated via SPARQL");
    }

    @Override
    public void startUpdate(UpdateContext arg0) throws SailException {
        throw new SailException("RO sparql can not be updated via SPARQL");
    }

    @Override
    public void begin(IsolationLevel arg0)
            throws UnknownSailTransactionStateException, SailException {
        // TODO Auto-generated method stub

    }

    @Override
    public void flush() throws SailException {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean hasStatement(Resource subj, IRI pred, Value obj,
            boolean includeInferred, Resource... contexts) throws SailException {
        // TODO Auto-generated method stub
        return false;
    }

}
