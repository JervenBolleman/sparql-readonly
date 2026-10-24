package sib.swiss.swissprot.sparql.ro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.query.algebra.Join;
import org.eclipse.rdf4j.query.algebra.SingletonSet;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.StatementPattern.Scope;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.algebra.Var;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryOptimizer;
import org.eclipse.rdf4j.query.algebra.helpers.AbstractSimpleQueryModelVisitor;

/**
 * Turns statement patterns that are joined and share an unbound subject
 * variable into a {@link StarJoin}. Runs after the other optimizers, on the
 * joins they ordered: the star takes the place of its first pattern, the
 * other patterns of the star leave the join.
 *
 * <p>
 * A pattern only joins a star if it is a direct part of the join, has a
 * constant predicate, is in the default graph, and its object is not the
 * subject variable. Patterns below a FILTER are left as they are, as are
 * queries over a dataset.
 */
public final class StarJoinOptimizer implements QueryOptimizer {

    @Override
    public void optimize(TupleExpr tupleExpr, Dataset dataset, BindingSet bindings) {
        if (dataset != null) {
            return;
        }
        tupleExpr.visit(new AbstractSimpleQueryModelVisitor<RuntimeException>(false) {
            @Override
            public void meet(Join join) {
                if (join.getParentNode() instanceof Join) {
                    // the outermost join of a join tree collects the stars
                    super.meet(join);
                    return;
                }
                List<TupleExpr> parts = new ArrayList<>();
                collect(join, parts);
                Map<String, List<StatementPattern>> bySubject = new LinkedHashMap<>();
                for (TupleExpr part : parts) {
                    if (part instanceof StatementPattern pattern && starPart(pattern)) {
                        bySubject.computeIfAbsent(pattern.getSubjectVar().getName(), s -> new ArrayList<>())
                                .add(pattern);
                    }
                }
                for (Map.Entry<String, List<StatementPattern>> star : bySubject.entrySet()) {
                    List<StatementPattern> patterns = star.getValue();
                    if (patterns.size() < 2) {
                        continue;
                    }
                    List<StatementPattern> copies = new ArrayList<>();
                    for (StatementPattern pattern : patterns) {
                        copies.add(pattern.clone());
                    }
                    patterns.get(0).replaceWith(new StarJoin(star.getKey(), copies));
                    for (StatementPattern pattern : patterns.subList(1, patterns.size())) {
                        pattern.replaceWith(new SingletonSet());
                    }
                }
                // joins below the parts, in OPTIONALs, UNIONs and subqueries
                for (TupleExpr part : parts) {
                    part.visit(this);
                }
            }
        });
    }

    private static void collect(TupleExpr expr, List<TupleExpr> parts) {
        if (expr instanceof Join join) {
            collect(join.getLeftArg(), parts);
            collect(join.getRightArg(), parts);
        } else {
            parts.add(expr);
        }
    }

    private static boolean starPart(StatementPattern pattern) {
        Var subject = pattern.getSubjectVar();
        Var object = pattern.getObjectVar();
        return !subject.hasValue()
                && pattern.getPredicateVar().hasValue()
                && pattern.getContextVar() == null
                && pattern.getScope() == Scope.DEFAULT_CONTEXTS
                && (object.hasValue() || !object.getName().equals(subject.getName()));
    }
}
