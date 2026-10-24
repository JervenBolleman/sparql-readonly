package sib.swiss.swissprot.sparql.ro;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.datatypes.XMLDatatypeUtil;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.query.algebra.And;
import org.eclipse.rdf4j.query.algebra.Compare;
import org.eclipse.rdf4j.query.algebra.Compare.CompareOp;
import org.eclipse.rdf4j.query.algebra.Filter;
import org.eclipse.rdf4j.query.algebra.ListMemberOperator;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.algebra.ValueConstant;
import org.eclipse.rdf4j.query.algebra.ValueExpr;
import org.eclipse.rdf4j.query.algebra.Var;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryOptimizer;
import org.eclipse.rdf4j.query.algebra.helpers.AbstractSimpleQueryModelVisitor;

/**
 * Finds the conditions of FILTERs on the object of the statement pattern
 * right below them that the triple lists can answer, see
 * {@link PushedFilter}, and records them for the evaluation of the pattern.
 * The FILTERs themselves stay.
 *
 * <p>
 * Runs after RDF4J's own optimizers: those move FILTERs down onto the
 * pattern that binds their variables, and turn sameTerm and = on IRIs into
 * a bound object, which the triple lists already select on.
 */
public final class FilterPushdownOptimizer implements QueryOptimizer {

    /**
     * Doubles are compared to integers as doubles: beyond this an integer
     * may round to the constant, so no bound is pushed.
     */
    private static final BigDecimal EXACT_DOUBLES = BigDecimal.valueOf(1L << 52);

    private final Map<StatementPattern, PushedFilter> pushed;

    /**
     * @param pushed where the filters are recorded, by pattern: an identity
     * map, the patterns of a query can be equal
     */
    public FilterPushdownOptimizer(Map<StatementPattern, PushedFilter> pushed) {
        this.pushed = pushed;
    }

    @Override
    public void optimize(TupleExpr tupleExpr, Dataset dataset, BindingSet bindings) {
        tupleExpr.visit(new AbstractSimpleQueryModelVisitor<RuntimeException>(false) {
            @Override
            public void meet(Filter filter) {
                TupleExpr below = filter.getArg();
                while (below instanceof Filter inner) {
                    below = inner.getArg();
                }
                if (below instanceof StatementPattern pattern && !pattern.getObjectVar().hasValue()) {
                    PushedFilter condition = pushable(filter.getCondition(), pattern.getObjectVar().getName());
                    if (condition != PushedFilter.NONE) {
                        pushed.merge(pattern, condition, PushedFilter::and);
                    }
                }
                super.meet(filter);
            }
        });
    }

    /**
     * @return what of the condition on the variable can be pushed down, the
     * conditions of an AND together
     */
    static PushedFilter pushable(ValueExpr condition, String variable) {
        if (condition instanceof And and) {
            return pushable(and.getLeftArg(), variable).and(pushable(and.getRightArg(), variable));
        } else if (condition instanceof Compare compare) {
            if (isVariable(compare.getLeftArg(), variable)) {
                return compared(compare.getOperator(), constant(compare.getRightArg()));
            } else if (isVariable(compare.getRightArg(), variable)) {
                return compared(flip(compare.getOperator()), constant(compare.getLeftArg()));
            }
        } else if (condition instanceof ListMemberOperator in) {
            return member(in, variable);
        }
        return PushedFilter.NONE;
    }

    /**
     * ?variable op value.
     */
    private static PushedFilter compared(CompareOp op, Value value) {
        if (!(value instanceof Literal literal)) {
            return PushedFilter.NONE;
        }
        if (XSD.BOOLEAN.equals(literal.getDatatype()) && op == CompareOp.EQ) {
            Boolean bool = parseBoolean(literal);
            return bool == null ? PushedFilter.NONE : new PushedFilter(null, null, null, bool);
        }
        BigDecimal number = number(literal);
        if (number == null) {
            return PushedFilter.NONE;
        }
        BigInteger floor = number.setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
        BigInteger ceiling = number.setScale(0, RoundingMode.CEILING).toBigIntegerExact();
        return switch (op) {
            case GT -> new PushedFilter(floor.add(BigInteger.ONE), null, null, null);
            case GE -> new PushedFilter(ceiling, null, null, null);
            case LT -> new PushedFilter(null, ceiling.subtract(BigInteger.ONE), null, null);
            case LE -> new PushedFilter(null, floor, null, null);
            // an integer equal to a number that is not one: none
            case EQ -> floor.equals(ceiling) ? new PushedFilter(floor, floor, null, null)
                    : new PushedFilter(BigInteger.ONE, BigInteger.ZERO, null, null);
            case NE -> PushedFilter.NONE;
        };
    }

    /**
     * ?variable IN (values): only pushed when all are numbers, a member that
     * is not could equal an object that is not a number.
     */
    private static PushedFilter member(ListMemberOperator in, String variable) {
        List<ValueExpr> arguments = in.getArguments();
        if (arguments.isEmpty() || !isVariable(arguments.get(0), variable)) {
            return PushedFilter.NONE;
        }
        List<Long> integers = new ArrayList<>();
        for (ValueExpr argument : arguments.subList(1, arguments.size())) {
            if (!(constant(argument) instanceof Literal literal)) {
                return PushedFilter.NONE;
            }
            BigDecimal number = number(literal);
            if (number == null) {
                return PushedFilter.NONE;
            }
            // a number that is not an integer matches no integer object
            if (number.stripTrailingZeros().scale() <= 0) {
                BigInteger integer = number.toBigIntegerExact();
                if (integer.bitLength() < Long.SIZE) {
                    integers.add(integer.longValue());
                } else {
                    // stored in the integer dictionary, not pushed
                    return PushedFilter.NONE;
                }
            }
        }
        return new PushedFilter(null, null, integers.stream().mapToLong(Long::longValue).toArray(), null);
    }

    /**
     * @return the exact value of a numeric literal, or null if it is not
     * one or not one that can be compared to integers exactly
     */
    private static BigDecimal number(Literal literal) {
        if (!XMLDatatypeUtil.isNumericDatatype(literal.getDatatype())) {
            return null;
        }
        try {
            if (XSD.DOUBLE.equals(literal.getDatatype()) || XSD.FLOAT.equals(literal.getDatatype())) {
                double d = literal.doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    return null;
                }
                BigDecimal value = new BigDecimal(d);
                return value.abs().compareTo(EXACT_DOUBLES) < 0 ? value : null;
            }
            return literal.decimalValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean parseBoolean(Literal literal) {
        try {
            return literal.booleanValue();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isVariable(ValueExpr expr, String variable) {
        return expr instanceof Var var && !var.hasValue() && var.getName().equals(variable);
    }

    private static Value constant(ValueExpr expr) {
        if (expr instanceof ValueConstant constant) {
            return constant.getValue();
        } else if (expr instanceof Var var && var.hasValue()) {
            return var.getValue();
        }
        return null;
    }

    private static CompareOp flip(CompareOp op) {
        return switch (op) {
            case LT -> CompareOp.GT;
            case LE -> CompareOp.GE;
            case GT -> CompareOp.LT;
            case GE -> CompareOp.LE;
            default -> op;
        };
    }
}
