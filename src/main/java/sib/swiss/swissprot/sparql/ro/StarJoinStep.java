package sib.swiss.swissprot.sparql.ro;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import dev.hardwood.reader.ColumnReaders;
import dev.hardwood.reader.FilterPredicate;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryBindingSet;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryEvaluationStep;

import sib.swiss.swissprot.sparql.ro.quads.RoResourceRoValueList;
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
            List<QueryBindingSet> next = new ArrayList<>();
            if (name == null) {
                // a constant object: a row per triple, as there is one per
                // graph the triple is in
                for (QueryBindingSet row : rows) {
                    for (int k = 0; k < objects.get(i).size(); k++) {
                        next.add(row);
                    }
                }
                rows = next;
                continue;
            }
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
     * Triples of one or more patterns in subject order, a subject at a time.
     */
    private interface Source extends AutoCloseable {

        /**
         * Reads the triples of the next subject.
         *
         * @return false if there are none
         */
        boolean nextSubject();

        /**
         * The id of the current subject.
         */
        long subject();

        Value subjectValue();

        /**
         * The indexes of the patterns this source reads.
         */
        int[] patterns();

        /**
         * The objects of the current subject, per pattern of
         * {@link #patterns()}.
         */
        List<List<Value>> objects();

        /**
         * Moves to the first subject at or after the target.
         *
         * @return false if there is none
         */
        default boolean seek(long target) {
            while (subject() < target) {
                if (!nextSubject()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        void close();
    }

    /**
     * Reads one pattern from its triple lists.
     */
    private static final class PatternCursor implements Source {

        private final int pattern;
        private final Iterator<Statement> statements;
        private Statement ahead;
        private long subject = Long.MIN_VALUE;
        private final List<Statement> group = new ArrayList<>();

        private PatternCursor(int pattern, Iterator<Statement> statements) {
            this.pattern = pattern;
            this.statements = statements;
            this.ahead = statements.hasNext() ? statements.next() : null;
        }

        @Override
        public boolean nextSubject() {
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

        private static long id(Statement statement) {
            return ((RoResource) statement.getSubject()).getLongId();
        }

        @Override
        public long subject() {
            return subject;
        }

        @Override
        public Value subjectValue() {
            return group.get(0).getSubject();
        }

        @Override
        public int[] patterns() {
            return new int[]{pattern};
        }

        @Override
        public List<List<Value>> objects() {
            return List.of(group.stream().map(Statement::getObject).collect(Collectors.toList()));
        }

        @Override
        public void close() {
            if (statements instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    /**
     * Reads several patterns whose lists are columns of one wide table: a
     * row with all those columns present is a subject with an object for
     * each, already joined.
     */
    private static final class WideCursor implements Source {

        private static final int BATCH_SIZE = 64 * 1024;

        private final int[] patterns;
        private final List<RoResourceRoValueList<?, ?>> lists;
        private final List<String> columns;
        private final ColumnReaders batches;
        private long[] subjects = new long[0];
        private long[][] objects;
        /**
         * Per pattern, the graph columns of its list, none if its list is
         * all in the default graph.
         */
        private final List<List<String>> graphColumns = new ArrayList<>();
        private boolean[][][] inGraph;
        private int next = -1;

        /**
         * @param objects the bound object of each pattern, null if any
         */
        private WideCursor(int[] patterns, List<RoResourceRoValueList<?, ?>> lists, List<Term> objects) {
            this.patterns = patterns;
            this.lists = lists;
            this.columns = new ArrayList<>();
            List<String> projection = new ArrayList<>(List.of(RoResourceRoValueList.SUBJECT));
            FilterPredicate filter = null;
            for (int i = 0; i < lists.size(); i++) {
                String column = lists.get(i).wideColumn().orElseThrow().column();
                columns.add(column);
                projection.add(column);
                graphColumns.add(lists.get(i).graphColumns());
                projection.addAll(lists.get(i).graphColumns());
                FilterPredicate present = objects.get(i) == null
                        ? FilterPredicate.isNotNull(column)
                        : FilterPredicate.eq(column, objects.get(i).id());
                filter = filter == null ? present : FilterPredicate.and(filter, present);
            }
            try {
                this.batches = lists.get(0).bySubject()
                        .columns(filter, BATCH_SIZE, projection.toArray(String[]::new))
                        .orElse(null);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public boolean nextSubject() {
            next++;
            while (next >= subjects.length) {
                if (batches == null || !nextBatch()) {
                    return false;
                }
                subjects = batches.getColumnReader(RoResourceRoValueList.SUBJECT).getLongs();
                objects = new long[columns.size()][];
                inGraph = new boolean[columns.size()][][];
                for (int i = 0; i < columns.size(); i++) {
                    objects[i] = batches.getColumnReader(columns.get(i)).getLongs();
                    inGraph[i] = new boolean[graphColumns.get(i).size()][];
                    for (int g = 0; g < inGraph[i].length; g++) {
                        inGraph[i][g] = batches.getColumnReader(graphColumns.get(i).get(g)).getBooleans();
                    }
                }
                next = 0;
            }
            return true;
        }

        @Override
        public long subject() {
            return next < 0 ? Long.MIN_VALUE : subjects[next];
        }

        @Override
        public Value subjectValue() {
            return lists.get(0).subjectOf(subjects[next]);
        }

        @Override
        public int[] patterns() {
            return patterns;
        }

        @Override
        public List<List<Value>> objects() {
            List<List<Value>> values = new ArrayList<>();
            for (int i = 0; i < lists.size(); i++) {
                Value object = lists.get(i).objectOf(objects[i][next]);
                if (inGraph[i].length == 0) {
                    values.add(List.of(object));
                } else {
                    // the triple once per graph it is in, as a pattern on
                    // the default graph, the union of all, sees it
                    List<Value> perGraph = new ArrayList<>();
                    for (boolean[] in : inGraph[i]) {
                        if (in[next]) {
                            perGraph.add(object);
                        }
                    }
                    values.add(perGraph);
                }
            }
            return values;
        }

        private boolean nextBatch() {
            try {
                return batches.nextBatch();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() {
            if (batches != null) {
                try {
                    batches.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
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
        private List<Source> sources = List.of();
        private boolean done;

        private Merge(BindingSet bindings, List<Pattern> patterns) {
            this.bindings = bindings;
            this.patterns = patterns;
        }

        /**
         * Opens the sources for the next kind of subject: patterns whose only
         * list is a column of the same wide table are read together.
         *
         * @return false if no kind is left
         */
        private boolean nextKind() {
            closeSources();
            if (kinds.isEmpty()) {
                return false;
            }
            Term.Kind kind = kinds.poll();
            List<Term> objects = new ArrayList<>();
            for (Pattern pattern : patterns) {
                Term object = null;
                if (pattern.object() != null) {
                    Optional<Term> term = Term.of(pattern.object(), store.dictionaries());
                    if (term.isEmpty()) {
                        // no triple has this object: the star has no rows
                        kinds.clear();
                        return false;
                    }
                    object = term.get();
                }
                objects.add(object);
            }
            Map<File, List<Integer>> byTable = new LinkedHashMap<>();
            List<RoResourceRoValueList<?, ?>> onlyLists = new ArrayList<>();
            for (int i = 0; i < patterns.size(); i++) {
                RoResourceRoValueList<?, ?> list = patterns.get(i).triples().onlyList(kind, objects.get(i)).orElse(null);
                onlyLists.add(list);
                if (list != null && list.wideColumn().isPresent()) {
                    byTable.computeIfAbsent(list.wideColumn().get().table(), t -> new ArrayList<>()).add(i);
                }
            }
            List<Source> opened = new ArrayList<>();
            boolean[] covered = new boolean[patterns.size()];
            for (List<Integer> together : byTable.values()) {
                if (together.size() < 2) {
                    continue;
                }
                List<RoResourceRoValueList<?, ?>> lists = new ArrayList<>();
                List<Term> bound = new ArrayList<>();
                for (int i : together) {
                    lists.add(onlyLists.get(i));
                    bound.add(objects.get(i));
                    covered[i] = true;
                }
                opened.add(new WideCursor(together.stream().mapToInt(Integer::intValue).toArray(), lists, bound));
            }
            for (int i = 0; i < patterns.size(); i++) {
                if (!covered[i]) {
                    opened.add(new PatternCursor(i, patterns.get(i).triples().inSubjectOrder(kind, objects.get(i))));
                }
            }
            sources = opened;
            for (Source source : sources) {
                if (!source.nextSubject()) {
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
                if (sources.isEmpty() && !nextKind()) {
                    return false;
                }
                long target = Long.MIN_VALUE;
                for (Source source : sources) {
                    target = Math.max(target, source.subject());
                }
                boolean all = true;
                for (Source source : sources) {
                    if (!source.seek(target)) {
                        // this kind is done
                        if (!nextKind()) {
                            return false;
                        }
                        all = false;
                        break;
                    }
                    if (source.subject() != target) {
                        all = false;
                        break;
                    }
                }
                if (!all) {
                    continue;
                }
                List<List<Value>> objects = new ArrayList<>(Collections.nCopies(patterns.size(), (List<Value>) null));
                for (Source source : sources) {
                    int[] covered = source.patterns();
                    List<List<Value>> found = source.objects();
                    for (int k = 0; k < covered.length; k++) {
                        objects.set(covered[k], found.get(k));
                    }
                }
                pending.addAll(combine(bindings, sources.get(0).subjectValue(), patterns, objects));
                for (Source source : sources) {
                    if (!source.nextSubject()) {
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

        private void closeSources() {
            for (Source source : sources) {
                source.close();
            }
            sources = List.of();
        }

        @Override
        public void close() {
            closeSources();
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
