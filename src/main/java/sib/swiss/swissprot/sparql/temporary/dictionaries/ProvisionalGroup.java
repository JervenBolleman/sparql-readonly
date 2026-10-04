package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * The distinct values of one dictionary group (an IRI namespace, a literal
 * datatype or language) seen while parsing. Each value gets a provisional id,
 * the order in which it was first seen, so that triples can be stored before
 * the dictionary is sorted. Once it is, {@link #assignFinalIds} records the
 * final id of every provisional one.
 *
 * <p>
 * A provisional id over all groups is {@code group index << 32 | index in
 * the group}.
 */
final class ProvisionalGroup<T extends Comparable<? super T>> {

    private final int index;
    private Map<T, Integer> ids = new HashMap<>();
    private List<T> values = new ArrayList<>();
    private long[] finalIds;

    ProvisionalGroup(int index) {
        this.index = index;
    }

    static int group(long provisionalId) {
        return (int) (provisionalId >>> 32);
    }

    static int indexInGroup(long provisionalId) {
        return (int) provisionalId;
    }

    int index() {
        return index;
    }

    int size() {
        return values.size();
    }

    /**
     * @return the provisional id of the value, a new one the first time it is
     * seen
     */
    long add(T value) {
        Integer id = ids.get(value);
        if (id == null) {
            id = values.size();
            ids.put(value, id);
            values.add(value);
        }
        return ((long) index << 32) | id;
    }

    T value(int indexInGroup) {
        return values.get(indexInGroup);
    }

    /**
     * @return the distinct values, sorted
     */
    List<T> sorted() {
        List<T> sorted = new ArrayList<>(values);
        sorted.sort(null);
        return sorted;
    }

    /**
     * Records the final id of every value and forgets the map of values to
     * provisional ids, which is the bulk of the memory.
     *
     * @param inOrder the values in the order {@code finalId} is called with
     * @param finalId the final id of the value at a position of {@code inOrder}
     */
    void assignFinalIds(List<T> inOrder, PositionToId finalId) {
        finalIds = new long[values.size()];
        for (int position = 0; position < inOrder.size(); position++) {
            finalIds[ids.get(inOrder.get(position))] = finalId.of(position);
        }
        ids = null;
    }

    /**
     * Final ids that follow from the value itself, not from its position.
     */
    void assignFinalIds(ToLongFunction<T> finalId) {
        finalIds = new long[values.size()];
        for (int i = 0; i < values.size(); i++) {
            finalIds[i] = finalId.applyAsLong(values.get(i));
        }
        ids = null;
    }

    long finalId(long provisionalId) {
        return finalIds[indexInGroup(provisionalId)];
    }

    /**
     * Drops the values once the dictionary is written and they are no longer
     * needed.
     */
    void releaseValues() {
        values = null;
    }

    @FunctionalInterface
    interface PositionToId {

        long of(int position);
    }
}
