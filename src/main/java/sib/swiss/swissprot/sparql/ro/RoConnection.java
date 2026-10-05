package sib.swiss.swissprot.sparql.ro;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.iteration.CloseableIteratorIteration;
import org.eclipse.rdf4j.common.iteration.EmptyIteration;
import org.eclipse.rdf4j.common.transaction.IsolationLevel;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.UnknownSailTransactionStateException;
import org.eclipse.rdf4j.sail.UpdateContext;

public class RoConnection implements SailConnection {

	private final RoStore store;
	private final RoTripleSource tripleSource;
	private volatile boolean open = true;

	public RoConnection(RoStore store) {
		super();
		this.store = store;
		tripleSource = new RoTripleSource(store);
	}

	@Override
	public boolean isOpen() throws SailException {
		return open;
	}

	@Override
	public void close() throws SailException {
		if (open) {
			open = false;
			store.connectionClosed(this);
		}
	}

	@Override
	public CloseableIteration<? extends BindingSet> evaluate(
			TupleExpr tupleExpr, Dataset dataset, BindingSet bindings,
			boolean includeInferred) throws SailException {
		try {
			EvaluationStatistics statistics = new EvaluationStatistics();
			RoEvaluationStrategy strategy = new RoEvaluationStrategy(store,
					tripleSource, dataset, store.getFederatedServiceResolver(), statistics);
			TupleExpr optimized = strategy.optimize(tupleExpr.clone(),
					statistics, bindings);
			return strategy.precompile(optimized).evaluate(bindings);
		} catch (QueryEvaluationException e) {
			throw new SailException(e);
		}
	}

	@Override
	public CloseableIteration<? extends Resource> getContextIDs()
			throws SailException {
		return new EmptyIteration<>();
	}

	@Override
	public CloseableIteration<? extends Statement> getStatements(
			Resource subj, IRI pred, Value obj, boolean includeInferred,
			Resource... contexts) throws SailException {
		return new RoFilteredTripleIterator(store, subj, pred, obj, contexts);
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

	@Override
	public void commit() throws SailException {
		throw new SailException("RO sparql can not be updated via SPARQL");

	}

	@Override
	public void rollback() throws SailException {

	}

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

	@Override
	public void clear(Resource... contexts) throws SailException {
		throw new SailException("RO sparql can not be updated via SPARQL");

	}

	@Override
	public CloseableIteration<? extends Namespace> getNamespaces()
			throws SailException {
		return new CloseableIteratorIteration<>(store.getNamespaces().iterator());
	}

	@Override
	public String getNamespace(String prefix) throws SailException {
		return store.getNamespaces().stream().map(RoNamespace::getPrefix)
				.filter(n -> {
					if (prefix == null && n == null) {
						return true;
					} else if (prefix != null && prefix.equals(n)) {
						return true;
					} else {
						return false;
					}
				}).findAny().orElse(null);

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

	}

	@Override
	public void flush() throws SailException {

	}

	@Override
	public boolean hasStatement(Resource subj, IRI pred, Value obj,
			boolean includeInferred, Resource... contexts)
			throws SailException {

		return false;
	}

}
