package sib.swiss.swissprot.sparql.ro;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.function.Function;

import dev.hardwood.reader.FilterPredicate;

import sib.swiss.swissprot.sparql.ro.quads.RoResourceRoValueList;

/**
 * The part of a SPARQL FILTER on the object of a statement pattern that the
 * triple lists can answer in Parquet. It only ever leaves out triples the
 * filter rejects: the FILTER stays in the query and decides on everything
 * read.
 *
 * <p>
 * What can be pushed down follows from how objects are stored. xsd:int and
 * the xsd:integer values that are their own id hold their value in the object
 * column, so a numeric comparison is a range there. Literals of other numeric
 * datatypes are dictionary ids, so their lists are read in full. Comparing a
 * number with an IRI, a blank node or a boolean is a type error, false in a
 * FILTER, so those lists are not read at all.
 *
 * @param lower the smallest integer the object may be, null for no bound
 * @param upper the largest integer the object may be, null for no bound
 * @param integers the only integers the object may be, null for any
 * @param bool the only boolean the object may be, null for any
 */
public record PushedFilter(BigInteger lower, BigInteger upper, long[] integers, Boolean bool) {

    /**
     * Restricts nothing.
     */
    public static final PushedFilter NONE = new PushedFilter(null, null, null, null);

    /**
     * How a triple list is to be read.
     *
     * @param read false if no triple of the list can pass
     * @param objects the condition on the object column, given its name, null
     * for none
     */
    public record ListRead(boolean read, Function<String, FilterPredicate> objects) {

        static final ListRead SKIP = new ListRead(false, null);
        static final ListRead ALL = new ListRead(true, null);

        static ListRead where(Function<String, FilterPredicate> objects) {
            return new ListRead(true, objects);
        }
    }

    private boolean numeric() {
        return lower != null || upper != null || integers != null;
    }

    /**
     * @return the conditions of both filters
     */
    public PushedFilter and(PushedFilter other) {
        BigInteger l = lower == null ? other.lower : other.lower == null ? lower : lower.max(other.lower);
        BigInteger u = upper == null ? other.upper : other.upper == null ? upper : upper.min(other.upper);
        long[] in = integers;
        if (in == null) {
            in = other.integers;
        } else if (other.integers != null) {
            in = Arrays.stream(in).filter(v -> Arrays.stream(other.integers).anyMatch(w -> w == v)).toArray();
        }
        Boolean b = bool;
        if (b == null) {
            b = other.bool;
        } else if (other.bool != null && !b.equals(other.bool)) {
            // true and false: no boolean passes, nor anything else
            return new PushedFilter(BigInteger.ONE, BigInteger.ZERO, null, Boolean.TRUE);
        }
        return new PushedFilter(l, u, in, b);
    }

    /**
     * @return how to read a list holding this kind of object
     */
    public ListRead forObjects(Term.Kind kind) {
        if (numeric() && bool != null) {
            // a value is not both a number and a boolean
            return ListRead.SKIP;
        }
        if (numeric()) {
            return switch (kind) {
                case INT -> inline(false);
                case INTEGER -> inline(true);
                // other numeric datatypes are dictionary literals
                case LITERAL -> ListRead.ALL;
                default -> ListRead.SKIP;
            };
        }
        if (bool != null) {
            return switch (kind) {
                case BOOLEAN -> ListRead.where(column -> FilterPredicate.eq(column, bool ? 1L : 0L));
                // a boolean that is not valid for its datatype is a literal
                case LITERAL -> ListRead.ALL;
                default -> ListRead.SKIP;
            };
        }
        return ListRead.ALL;
    }

    /**
     * The condition on objects that hold their integer value.
     *
     * @param dictionaryIds whether the list also holds ids of the integer
     * dictionary, negative longs, which are always read
     */
    private ListRead inline(boolean dictionaryIds) {
        if (dictionaryIds) {
            return ListRead.where(column -> {
                FilterPredicate values = values(column);
                FilterPredicate inDictionary = FilterPredicate.lt(column, 0L);
                return values == null ? inDictionary : FilterPredicate.or(values, inDictionary);
            });
        }
        return values(RoResourceRoValueList.OBJECT) == null ? ListRead.SKIP : ListRead.where(this::values);
    }

    /**
     * @return the condition on integer values, null if none passes
     */
    private FilterPredicate values(String column) {
        BigInteger min = BigInteger.valueOf(Long.MIN_VALUE);
        BigInteger max = BigInteger.valueOf(Long.MAX_VALUE);
        BigInteger l = lower == null ? min : lower.max(min);
        BigInteger u = upper == null ? max : upper.min(max);
        if (l.compareTo(max) > 0 || u.compareTo(min) < 0 || l.compareTo(u) > 0) {
            return null;
        }
        if (integers != null) {
            long[] inRange = Arrays.stream(integers)
                    .filter(v -> BigInteger.valueOf(v).compareTo(l) >= 0 && BigInteger.valueOf(v).compareTo(u) <= 0)
                    .toArray();
            return inRange.length == 0 ? null : FilterPredicate.in(column, inRange);
        }
        return FilterPredicate.and(FilterPredicate.gtEq(column, l.longValue()),
                FilterPredicate.ltEq(column, u.longValue()));
    }
}
