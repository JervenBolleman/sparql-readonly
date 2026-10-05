package sib.swiss.swissprot.sparql.ro;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryEvaluationStep;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryOptimizer;
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.DefaultEvaluationStrategy;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.QueryEvaluationContext;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.evaluationsteps.StatementPatternQueryEvaluationStep;
import org.eclipse.rdf4j.query.algebra.evaluation.optimizer.StandardQueryOptimizerPipeline;

/**
 * RDF4J's evaluation, with the FILTERs on objects that
 * {@link FilterPushdownOptimizer} found evaluated in the triple lists.
 */
final class RoEvaluationStrategy extends DefaultEvaluationStrategy {

    private final RoStore store;
    private final Map<StatementPattern, PushedFilter> pushed = new IdentityHashMap<>();

    RoEvaluationStrategy(RoStore store, RoTripleSource tripleSource, Dataset dataset,
            FederatedServiceResolver serviceResolver, EvaluationStatistics statistics) {
        super(tripleSource, dataset, serviceResolver);
        this.store = store;
        // strict or standard comparisons, as configured for the store
        setQueryEvaluationMode(store.getDefaultQueryEvaluationMode());
        StandardQueryOptimizerPipeline standard = new StandardQueryOptimizerPipeline(this, tripleSource, statistics);
        List<QueryOptimizer> optimizers = new ArrayList<>();
        standard.getOptimizers().forEach(optimizers::add);
        // after the standard ones, which move the filters onto their patterns
        optimizers.add(new FilterPushdownOptimizer(pushed));
        setOptimizerPipeline(() -> optimizers);
    }

    @Override
    protected QueryEvaluationStep prepare(StatementPattern pattern, QueryEvaluationContext context)
            throws QueryEvaluationException {
        PushedFilter filter = pushed.get(pattern);
        if (filter == null) {
            return super.prepare(pattern, context);
        }
        return new StatementPatternQueryEvaluationStep(pattern, context,
                new RoTripleSource(store, filter));
    }
}
