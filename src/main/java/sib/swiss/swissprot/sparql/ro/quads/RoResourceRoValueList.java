package sib.swiss.swissprot.sparql.ro.quads;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import dev.hardwood.Hardwood;
import dev.hardwood.metadata.PhysicalType;
import dev.hardwood.metadata.RepetitionType;
import dev.hardwood.reader.FilterPredicate;
import dev.hardwood.reader.ColumnReaders;
import dev.hardwood.schema.FileSchema;
import org.eclipse.rdf4j.model.Statement;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import sib.swiss.swissprot.sparql.ro.Term;
import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;
import sib.swiss.swissprot.sparql.ro.values.RoValue;

public abstract class RoResourceRoValueList<S extends RoResource, O extends RoValue> implements Iterable<Statement>, Closeable {

    /**
     * Rows read at a time: many when reading a whole list, few when looking up
     * a subject or object, which a sorted list keeps in one row group.
     */
    private static final int SCAN_BATCH_SIZE = 64 * 1024;
    private static final int LOOKUP_BATCH_SIZE = 4096;

    public static final String SUBJECT = "subject";
    public static final String OBJECT = "object";
    /**
     * Every list stores the long ids of its subjects and objects.
     */
    protected static final FileSchema SCHEMA = FileSchema.builder("triples")
            .addColumn(SUBJECT, PhysicalType.INT64, RepetitionType.REQUIRED)
            .addColumn(OBJECT, PhysicalType.INT64, RepetitionType.REQUIRED)
            .build();
    protected final RoIri predicate;
    /**
     * The triples sorted by subject: the list's own file, or a column of a
     * wide table, see {@link WideTables}.
     */
    private final ParquetTable bySubject;
    /**
     * The object column of {@link #bySubject}.
     */
    private final String objectColumn;
    /**
     * In a wide table, the rows that have an object in the column; null for
     * the list's own file.
     */
    private final FilterPredicate present;
    /**
     * The same triples sorted by object, for lookups of objects.
     */
    private final ParquetTable byObject;
    private final WideTables.Column wide;
    /**
     * The graph columns of the list, see {@link GraphColumns}, without the
     * prefix they have in a wide table; none if it is all in the default
     * graph.
     */
    private final List<String> graphColumns;
    /**
     * The graph of each graph column, null for the default graph.
     */
    private final List<RoResource> graphs;

    protected final RoDictionaries dictionaries;

    protected abstract S getSubject(long id);

    protected abstract O getObject(long id);

    /**
     * @return the subject with this id in this list
     */
    public S subjectOf(long id) {
        return getSubject(id);
    }

    /**
     * @return the object with this id in this list
     */
    public O objectOf(long id) {
        return getObject(id);
    }

    /**
     * @return true if the list has no triples
     */
    public boolean isEmpty() {
        // a wide table also holds the rows of other lists: the copy sorted
        // by object holds just this list's
        return wide != null ? byObject.isEmpty() : bySubject.isEmpty();
    }

    /**
     * @return the column of this list in a wide table, if it was merged into
     * one
     */
    public Optional<WideTables.Column> wideColumn() {
        return Optional.ofNullable(wide);
    }

    /**
     * @return the table holding this list sorted by subject: its own file or
     * a wide table, to read several columns of at once
     */
    public ParquetTable bySubject() {
        return bySubject;
    }

    protected RoResourceRoValueList(File file, RoIri predicate,
            RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super();
        this.predicate = predicate;
        Optional<WideTables.Column> wide = WideTables.columnOf(file);
        this.wide = wide.orElse(null);
        if (wide.isPresent()) {
            this.bySubject = ParquetTable.open(hardwood, wide.get().table());
            this.objectColumn = wide.get().column();
            this.present = FilterPredicate.isNotNull(objectColumn);
        } else {
            this.bySubject = ParquetTable.open(hardwood, file);
            this.objectColumn = OBJECT;
            this.present = null;
        }
        this.byObject = ParquetTable.open(hardwood, byObjectFile(file));
        this.dictionaries = dictionaries;
        this.graphColumns = GraphColumns.in(bySubject, graphPrefix());
        this.graphs = new ArrayList<>();
        for (String column : graphColumns) {
            graphs.add(GraphColumns.graph(column, dictionaries.getIriDict()));
        }
    }

    /**
     * @return the prefix of the graph columns of this list in its table
     * sorted by subject
     */
    private String graphPrefix() {
        return wide == null ? "" : GraphColumns.prefix(objectColumn);
    }

    /**
     * @return the graph columns of this list in its table sorted by subject,
     * with their prefix
     */
    public List<String> graphColumns() {
        return graphColumns.stream().map(c -> graphPrefix() + c).toList();
    }

    /**
     * @return the graph of each of {@link #graphColumns()}, null for the
     * default graph
     */
    public List<RoResource> graphs() {
        return graphs;
    }

    @Override
    public void close() throws IOException {
        bySubject.close();
        byObject.close();
    }

    static File byObjectFile(File file) {
        return new File(file.getParentFile(), file.getName() + "-by-object");
    }

    public Stream<Statement> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /**
     * @param graphs the graph columns, see {@link GraphColumns}, of the
     * graphs to read the triples of; null for all
     * @return the indexes in {@link #graphs()} of the graph columns to read,
     * null if the list has none and is read whole, empty if no triple can be
     * in these graphs
     */
    private List<Integer> selected(Set<String> graphs) {
        if (graphColumns.isEmpty()) {
            // all in the default graph
            return graphs == null || graphs.contains(GraphColumns.DEFAULT) ? null : List.of();
        }
        List<Integer> selected = new ArrayList<>();
        for (int g = 0; g < graphColumns.size(); g++) {
            if (graphs == null || graphs.contains(graphColumns.get(g))) {
                selected.add(g);
            }
        }
        return selected;
    }

    /**
     * @return the rows in one of the selected graphs, null if all rows are
     */
    private FilterPredicate inGraphs(List<Integer> selected, String prefix) {
        if (selected == null || selected.size() == graphColumns.size()) {
            return null;
        }
        FilterPredicate[] any = new FilterPredicate[selected.size()];
        for (int i = 0; i < any.length; i++) {
            any[i] = FilterPredicate.eq(prefix + graphColumns.get(selected.get(i)), true);
        }
        return any.length == 1 ? any[0] : FilterPredicate.or(any);
    }

    @Override
    public Iterator<Statement> iterator() {
        return read(bySubject, objectColumn, graphPrefix(), present, SCAN_BATCH_SIZE, null);
    }

    /**
     * @return the kind of every subject in this list
     */
    public abstract Term.Kind subjectKind();

    /**
     * @return the kind of every object in this list
     */
    public abstract Term.Kind objectKind();

    /**
     * The triples with this subject and object, selected by Parquet.
     *
     * @param subject null for any subject, otherwise of the
     * {@link #subjectKind()}
     * @param object null for any object, otherwise of the
     * {@link #objectKind()}
     */
    public Stream<Statement> stream(Term subject, Term object) {
        return stream(subject, object, null);
    }

    /**
     * @param objects a further condition on the object column, given its
     * name, null for none
     */
    public Stream<Statement> stream(Term subject, Term object, Function<String, FilterPredicate> objects) {
        return stream(subject, object, objects, null);
    }

    /**
     * @param graphs the graph columns, see {@link GraphColumns}, of the
     * graphs to read the triples of, null for all: only the triples in these
     * graphs are read, each once per selected graph it is in
     */
    public Stream<Statement> stream(Term subject, Term object, Function<String, FilterPredicate> objects,
            Set<String> graphs) {
        List<Integer> selected = selected(graphs);
        if (selected != null && selected.isEmpty()) {
            return Stream.empty();
        }
        if (subject == null && object == null && objects == null && graphs == null) {
            return stream();
        }
        // sorted on the column that is filtered, so that statistics skip
        boolean useByObject = subject == null && !byObject.isEmpty();
        ParquetTable table = useByObject ? byObject : bySubject;
        String column = useByObject ? OBJECT : objectColumn;
        FilterPredicate filter = useByObject ? null : present;
        if (objects != null) {
            filter = and(filter, objects.apply(column));
        }
        if (subject != null) {
            filter = and(filter, FilterPredicate.eq(SUBJECT, subject.id()));
        }
        if (object != null) {
            filter = and(filter, FilterPredicate.eq(column, object.id()));
        }
        String prefix = useByObject ? "" : graphPrefix();
        filter = and(filter, inGraphs(selected, prefix));
        Iterator<Statement> iterator = read(table, column, prefix, filter,
                subject == null && object == null ? SCAN_BATCH_SIZE : LOOKUP_BATCH_SIZE, selected);
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }

    private static FilterPredicate and(FilterPredicate left, FilterPredicate right) {
        return left == null ? right : right == null ? left : FilterPredicate.and(left, right);
    }

    /**
     * Reads the subject, object and graph columns of a table, the rows
     * matching the filter if there is one.
     *
     * @param graphPrefix the prefix of the graph columns in the table
     */
    private Iterator<Statement> read(ParquetTable table, String column, String graphPrefix,
            FilterPredicate filter, int batchSize, List<Integer> selected) {
        List<String> projection = new ArrayList<>(List.of(SUBJECT, column));
        List<RoResource> contexts = new ArrayList<>();
        for (int g = 0; g < graphColumns.size(); g++) {
            if (selected == null || selected.contains(g)) {
                projection.add(graphPrefix + graphColumns.get(g));
                contexts.add(graphs.get(g));
            }
        }
        String[] columns = projection.toArray(String[]::new);
        Optional<ColumnReaders> batches;
        try {
            batches = filter == null
                    ? table.columns(batchSize, columns)
                    : table.columns(filter, batchSize, columns);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return batches.<Iterator<Statement>>map(readers -> {
            RoResourceValueListIterator iterator = newIterator(readers);
            iterator.columns(columns, contexts);
            return iterator;
        }).orElseGet(Collections::emptyIterator);
    }

    /**
     * The triples with this object, in the order of their subjects, for a
     * merge on subjects.
     *
     * @param object null for any object
     */
    public Iterator<Statement> inSubjectOrder(Term object) {
        if (object == null) {
            return iterator();
        }
        return read(bySubject, objectColumn, graphPrefix(),
                and(present, FilterPredicate.eq(objectColumn, object.id())), SCAN_BATCH_SIZE, null);
    }


    protected abstract RoResourceValueListIterator newIterator(ColumnReaders rows);

    protected static abstract class AbstractBuilder<S extends RoResource, O extends RoValue> {

        protected final File file;
        protected final RoIri predicate;
        protected final RoDictionaries dictionaries;
        protected final Hardwood hardwood;
        // Most lists of a predicate stay empty: they only take memory once a
        // triple arrives, and an empty list has no file.
        private final SortedTripleWriter triples;
        // the contexts of the triples, numbered in the order they are seen
        private final List<RoResource> contexts = new ArrayList<>();
        private final Map<RoResource, Integer> contextNumbers = new HashMap<>();

        protected AbstractBuilder(File file, RoIri predicate,
                RoDictionaries dictionaries, Hardwood hardwood)
                throws FileNotFoundException, IOException {
            super();
            this.file = file;
            this.predicate = predicate;
            this.dictionaries = dictionaries;
            this.hardwood = hardwood;
            this.triples = new SortedTripleWriter(file, hardwood, false);
        }

        public void add(S subject, O object,
                RoResource context) throws IOException {
            int contextNumber = SortedTripleWriter.NO_CONTEXT;
            if (context != null) {
                contextNumber = contextNumbers.computeIfAbsent(context, c -> {
                    contexts.add(c);
                    return contexts.size() - 1;
                });
            }
            triples.add(subject.getLongId(), object.getLongId(), contextNumber);
        }

        /**
         * Writes the copy of the list sorted by object, from the list, with
         * the same graph columns.
         */
        private void writeByObject(List<String> graphs) throws IOException {
            SortedTripleWriter sorted = new SortedTripleWriter(byObjectFile(file), hardwood, true);
            List<String> columns = new ArrayList<>(List.of(SUBJECT, OBJECT));
            columns.addAll(graphs);
            if (!graphs.isEmpty()) {
                columns.add(GraphColumns.DEFAULT);
            }
            try (ParquetTable list = ParquetTable.open(hardwood, file)) {
                Optional<ColumnReaders> batches = list.columns(SCAN_BATCH_SIZE, columns.toArray(String[]::new));
                if (batches.isPresent()) {
                    try (ColumnReaders rows = batches.get()) {
                        while (rows.nextBatch()) {
                            long[] subjects = rows.getColumnReader(SUBJECT).getLongs();
                            long[] objects = rows.getColumnReader(OBJECT).getLongs();
                            boolean[][] inGraph = new boolean[columns.size() - 2][];
                            for (int g = 0; g < inGraph.length; g++) {
                                inGraph[g] = rows.getColumnReader(columns.get(g + 2)).getBooleans();
                            }
                            for (int i = 0; i < subjects.length; i++) {
                                if (inGraph.length == 0) {
                                    sorted.add(subjects[i], objects[i], SortedTripleWriter.NO_CONTEXT);
                                }
                                for (int g = 0; g < inGraph.length; g++) {
                                    if (inGraph[g][i]) {
                                        // the default graph is the last column
                                        sorted.add(subjects[i], objects[i],
                                                g < graphs.size() ? g : SortedTripleWriter.NO_CONTEXT);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            sorted.finish(graphs);
        }

        /**
         * Writes the list sorted by subject and its copy sorted by object.
         */
        protected void save() throws IOException {
            List<String> graphs = contexts.stream().map(GraphColumns::name).toList();
            triples.finish(graphs);
            writeByObject(graphs);
        }
    }

    /**
     * The triples of the rows read, a statement per graph a triple is in.
     */
    protected abstract class RoResourceValueListIterator
            implements Iterator<Statement>, AutoCloseable {

        private final ColumnReaders batches;
        private boolean open = true;
        private String[] columns;
        private List<RoResource> contexts;
        private long[] subjects;
        private long[] objects;
        private boolean[][] inGraph;
        private int next;
        /**
         * The graph of the triple at next: the first one it is in at or
         * after this one.
         */
        private int graph;

        protected RoResourceValueListIterator(ColumnReaders batches) {
            this.batches = batches;
        }

        /**
         * @param columns the columns read: the subject, the object, then
         * graph columns of the list
         * @param contexts the graph of each of those graph columns
         */
        void columns(String[] columns, List<RoResource> contexts) {
            this.columns = columns;
            this.contexts = contexts;
        }

        @Override
        public Statement next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            S subject = getSubject(subjects[next]);
            O object = getObject(objects[next]);
            RoResource context = null;
            if (inGraph.length == 0) {
                next++;
            } else {
                context = contexts.get(graph);
                graph++;
            }
            return statement(subject, predicate, object, context);
        }

        public Statement statement(S subject, RoIri predicate, O object, RoResource graph) {
            return new RoContextStatement(subject, predicate,
                    object, graph);
        }

        /**
         * Releases the readers when not all triples are read.
         */
        @Override
        public void close() {
            if (open) {
                open = false;
                try {
                    batches.close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        @Override
        public boolean hasNext() {
            while (open) {
                if (subjects != null && next < subjects.length) {
                    if (inGraph.length == 0) {
                        return true;
                    }
                    while (graph < inGraph.length && !inGraph[graph][next]) {
                        graph++;
                    }
                    if (graph < inGraph.length) {
                        return true;
                    }
                    next++;
                    graph = 0;
                } else if (nextBatch()) {
                    subjects = batches.getColumnReader(columns[0]).getLongs();
                    objects = batches.getColumnReader(columns[1]).getLongs();
                    inGraph = new boolean[columns.length - 2][];
                    for (int g = 0; g < inGraph.length; g++) {
                        inGraph[g] = batches.getColumnReader(columns[g + 2]).getBooleans();
                    }
                    next = 0;
                    graph = 0;
                } else {
                    close();
                }
            }
            return false;
        }

        private boolean nextBatch() {
            try {
                return batches.nextBatch();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
