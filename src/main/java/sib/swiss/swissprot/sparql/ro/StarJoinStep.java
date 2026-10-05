package sib.swiss.swissprot.sparql.ro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryBindingSet;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryEvaluationStep;

import sib.swiss.swissprot.sparql.ro.values.RoResource;

/**
 * Evaluates a {@link StarJoin}. With the subject bound, every pattern is a
 * lookup of that subject. Otherwise, if a pattern with a bound object has
 * few triples, it gives the subjects to look up. Otherwise the triple lists
 * of all patterns are read in subject order and merged: a subject that every
 * pattern has gives the combinations of its objects.
 */
final class StarJoinStep implements QueryEvaluationStep {

    /**
     * A pattern with a bound object and at most this many triples gives the
     * subjects; with more, merging the lists is cheaper than a lookup per
     * subject.
     */
    static final int DRIVING_PATTERN_LIMIT = 1000;

    private final RoStore store;
    private final StarJoin star;

    StarJoinStep(RoStore store, StarJoin star) {
        this.store = store;
        this.star = star;
    }

    /**
     * A pattern of the star for one evaluation.
     *
     * @param objectName the object variable, null if the object is constant
     * @param object the constant or bound object, null if any
     */
    private record Pattern(RoPredicateStore triples, String objectName, Value object) {
    }

    @Override
    public CloseableIteration<BindingSet> evaluate(BindingSet bindings) {
        List<Pattern> patterns = new ArrayList<>();
        for (StatementPattern sp : star.getPatterns()) {
            RoPredicateStore triples = store.getPredicateStore((IRI) sp.getPredicateVar().getValue());
            if (triples == null) {
                return QueryEvaluationStep.EMPTY_ITERATION;
            }
            String objectName = sp.getObjectVar().hasValue() ? null : sp.getObjectVar().getName();
            Value object = objectName == null ? sp.getObjectVar().getValue() : bindings.getValue(objectName);
            patterns.add(new Pattern(triples, objectName, object));
        }
        Value subject = bindings.getValue(star.getSubject());
        if (subject != null) {
            return new Rows(lookup(bindings, (Resource) subject, patterns).iterator(), null);
        }
        List<Resource> driving = drivingSubjects(patterns);
        if (driving != null) {
            List<BindingSet> rows = new ArrayList<>();
            for (Resource s : driving) {
                rows.addAll(lookup(bindings, s, patterns));
            }
            return new Rows(rows.iterator(), null);
        }
        Merge merge = new Merge(bindings, patterns);
        return new Rows(merge, merge);
    }

    /**
     * @return the subjects of the pattern with a bound object and the fewest
     * triples, if it has at most {@link #DRIVING_PATTERN_LIMIT}; null to merge
     */
    private static List<Resource> drivingSubjects(List<Pattern> patterns) {
        List<Resource> fewest = null;
        for (Pattern pattern : patterns) {
            if (pattern.object() == null) {
                continue;
            }
            Set<Resource> subjects = new LinkedHashSet<>();
            Iterator<Statement> it = pattern.triples().stream(null, pattern.object()).iterator();
            while (it.hasNext() && subjects.size() <= DRIVING_PATTERN_LIMIT) {
                subjects.add(it.next().getSubject());
            }
            if (subjects.size() <= DRIVING_PATTERN_LIMIT && (fewest == null || subjects.size() < fewest.size())) {
                fewest = new ArrayList<>(subjects);
            }
        }
        return fewest;
    }

    /**
     * The rows for one subject, each pattern looked up.
     */
    private List<BindingSet> lookup(BindingSet bindings, Resource subject, List<Pattern> patterns) {
        List<List<Value>> objects = new ArrayList<>();
        for (Pattern pattern : patterns) {
            List<Value> found = pattern.triples().stream(subject, pattern.object())
                    .map(Statement::getObject)
                    .collect(Collectors.toList());
            if (found.isEmpty()) {
                return List.of();
            }
            objects.add(found);
        }
        return combine(bindings, subject, patterns, objects);
    }

    /**
     * All combinations of the objects of each pattern for one subject, where
     * patterns with the same object variable agree.
     */
    private List<BindingSet> combine(BindingSet bindings, Value subject, List<Pattern> patterns,
            List<List<Value>> objects) {
        QueryBindingSet first = new QueryBindingSet(bindings);
        if (!first.hasBinding(star.getSubject())) {
            first.addBinding(star.getSubject(), subject);
        }
        List<QueryBindingSet> rows = List.of(first);
        for (int i = 0; i < patterns.size(); i++) {
            String name = patterns.get(i).objectName();
            if (name == null) {
                continue;
            }
            List<QueryBindingSet> next = new ArrayList<>();
            for (QueryBindingSet row : rows) {
                for (Value object : objects.get(i)) {
                    Value bound = row.getValue(name);
                    if (bound == null) {
                        QueryBindingSet extended = new QueryBindingSet(row);
                        extended.addBinding(name, object);
                        next.add(extended);
                    } else if (bound.equals(object)) {
                        next.add(row);
                    }
                }
            }
            rows = next;
        }
        return new ArrayList<>(rows);
    }

    /**
     * Reads the triples of a pattern in subject order, a subject at a time.
     */
    private static final class Cursor implements AutoCloseable {

        private final Iterator<Statement> statements;
        private Statement ahead;
        private long subject;
        private final List<Statement> group = new ArrayList<>();

        private Cursor(Iterator<Statement> statements) {
            this.statements = statements;
            this.ahead = statements.hasNext() ? statements.next() : null;
        }

        /**
         * Reads the triples of the next subject.
         *
         * @return false if there are none
         */
        boolean nextSubject() {
            group.clear();
            if (ahead == null) {
                return false;
            }
            subject = id(ahead);
            while (ahead != null && id(ahead) == subject) {
                group.add(ahead);
                ahead = statements.hasNext() ? statements.next() : null;
            }
            return true;
        }

        /**
         * Moves to the first subject at or after the target.
         *
         * @return false if there is none
         */
        boolean seek(long target) {
            while (group.isEmpty() || subject < target) {
                if (!nextSubject()) {
                    return false;
                }
            }
            return true;
        }

        private static long id(Statement statement) {
            return ((RoResource) statement.getSubject()).getLongId();
        }

        @Override
        public void close() throws Exception {
            if (statements instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    /**
     * Merges the patterns on subject, first those with IRI subjects, then
     * those with blank node subjects: the two are numbered apart.
     */
    private final class Merge implements Iterator<BindingSet>, AutoCloseable {

        private final BindingSet bindings;
        private final List<Pattern> patterns;
        private final Deque<Term.Kind> kinds = new ArrayDeque<>(List.of(Term.Kind.IRI, Term.Kind.BNODE));
        private final Deque<BindingSet> pending = new ArrayDeque<>();
        private List<Cursor> cursors = List.of();
        private boolean done;

        private Merge(BindingSet bindings, List<Pattern> patterns) {
            this.bindings = bindings;
            this.patterns = patterns;
        }

        /**
         * Opens cursors for the next kind of subject.
         *
         * @return false if no kind is left
         */
        private boolean nextKind() {
            closeCursors();
            if (kinds.isEmpty()) {
                return false;
            }
            Term.Kind kind = kinds.poll();
            List<Cursor> opened = new ArrayList<>();
            for (Pattern pattern : patterns) {
                Term object = null;
                if (pattern.object() != null) {
                    Optional<Term> term = Term.of(pattern.object(), store.dictionaries());
                    if (term.isEmpty()) {
                        // no triple has this object: the star has no rows
                        kinds.clear();
                        cursors = opened;
                        return false;
                    }
                    object = term.get();
                }
                opened.add(new Cursor(pattern.triples().inSubjectOrder(kind, object)));
            }
            cursors = opened;
            for (Cursor cursor : cursors) {
                if (!cursor.nextSubject()) {
                    return nextKind();
                }
            }
            return true;
        }

        /**
         * Finds the next subject all patterns have and queues its rows.
         *
         * @return false if there is none
         */
        private boolean nextSubject() {
            while (true) {
                if (cursors.isEmpty() && !nextKind()) {
                    return false;
                }
                long target = Long.MIN_VALUE;
                for (Cursor cursor : cursors) {
                    target = Math.max(target, cursor.subject);
                }
                boolean all = true;
                for (Cursor cursor : cursors) {
                    if (!cursor.seek(target)) {
                        // this kind is done
                        if (!nextKind()) {
                            return false;
                        }
                        all = false;
                        break;
                    }
                    if (cursor.subject != target) {
                        all = false;
                        break;
                    }
                }
                if (!all) {
                    continue;
                }
                List<List<Value>> objects = new ArrayList<>();
                for (Cursor cursor : cursors) {
                    objects.add(cursor.group.stream().map(Statement::getObject).collect(Collectors.toList()));
                }
                pending.addAll(combine(bindings, cursors.get(0).group.get(0).getSubject(), patterns, objects));
                for (Cursor cursor : cursors) {
                    if (!cursor.nextSubject()) {
                        // one pattern has no more subjects of this kind
                        if (!nextKind()) {
                            done = true;
                        }
                        break;
                    }
                }
                if (!pending.isEmpty()) {
                    return true;
                }
            }
        }

        @Override
        public boolean hasNext() {
            if (pending.isEmpty() && !done) {
                if (!nextSubject()) {
                    done = true;
                }
            }
            return !pending.isEmpty();
        }

        @Override
        public BindingSet next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return pending.poll();
        }

        private void closeCursors() {
            for (Cursor cursor : cursors) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    // closing what was read in full or not at all
                }
            }
            cursors = List.of();
        }

        @Override
        public void close() {
            closeCursors();
            kinds.clear();
            done = true;
        }
    }

    /**
     * The rows as an iteration that closes the merge.
     */
    private static final class Rows implements CloseableIteration<BindingSet> {

        private final Iterator<BindingSet> rows;
        private final AutoCloseable resources;

        private Rows(Iterator<BindingSet> rows, AutoCloseable resources) {
            this.rows = rows;
            this.resources = resources;
        }

        @Override
        public boolean hasNext() {
            return rows.hasNext();
        }

        @Override
        public BindingSet next() {
            return rows.next();
        }

        @Override
        public void close() {
            if (resources != null) {
                try {
                    resources.close();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }
}
