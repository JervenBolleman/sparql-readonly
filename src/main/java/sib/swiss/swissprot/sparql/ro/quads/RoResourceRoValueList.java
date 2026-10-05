package sib.swiss.swissprot.sparql.ro.quads;

import java.io.Closeable;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
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
import org.roaringbitmap.RoaringBitmap;

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
    protected final Map<RoIri, RoaringBitmap> iriGraphsMap;
    protected final Map<RoBnode, RoaringBitmap> bNodeGraphsMap;
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
            Map<RoIri, RoaringBitmap> iriGraphsMap,
            Map<RoBnode, RoaringBitmap> bNodeGraphsMap,
            RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super();
        this.predicate = predicate;
        this.iriGraphsMap = iriGraphsMap;
        this.bNodeGraphsMap = bNodeGraphsMap;
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
    }

    @Override
    public void close() throws IOException {
        bySubject.close();
        byObject.close();
    }

    static File byObjectFile(File file) {
        return new File(file.getParentFile(), file.getName() + "-by-object");
    }

    /**
     * The rows of the list in each named graph. Only a list with triples in
     * named graphs has this file: the number of graphs, then per graph
     * whether it is an IRI, its id and a serialized RoaringBitmap of rows.
     */
    static File graphsFile(File file) {
        return new File(file.getParentFile(), file.getName() + "-graphs");
    }

    /**
     * The rows of a list in one named graph.
     *
     * @param iri whether the graph is an IRI, else a blank node
     * @param id the id of the graph
     * @param rows the rows of the triples sorted by subject in the graph
     */
    record Graph(boolean iri, long id, RoaringBitmap rows) {
    }

    /**
     * @return the named graphs of the list, none if it has no
     * {@link #graphsFile(File)}
     */
    static List<Graph> readGraphs(File file) throws IOException {
        File graphs = graphsFile(file);
        List<Graph> read = new ArrayList<>();
        if (!graphs.exists()) {
            return read;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(graphs)))) {
            int numberOfGraphs = in.readInt();
            for (int i = 0; i < numberOfGraphs; i++) {
                boolean iri = in.readBoolean();
                long id = in.readLong();
                RoaringBitmap rows = new RoaringBitmap();
                rows.deserialize(in);
                read.add(new Graph(iri, id, rows));
            }
        }
        return read;
    }

    /**
     * Writes the {@link #graphsFile(File)} of a list, or removes it if there
     * are no graphs.
     */
    static void writeGraphs(File file, List<Graph> graphs) throws IOException {
        File graphsFile = graphsFile(file);
        if (graphs.isEmpty()) {
            Files.deleteIfExists(graphsFile.toPath());
            return;
        }
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(graphsFile)))) {
            out.writeInt(graphs.size());
            for (Graph graph : graphs) {
                out.writeBoolean(graph.iri());
                out.writeLong(graph.id());
                graph.rows().serialize(out);
            }
        }
    }

    protected static Map<RoIri, RoaringBitmap> getRoIriGraphsMap(File file, RoIriDictionary iriDict)
            throws IOException {
        Map<RoIri, RoaringBitmap> map = new HashMap<>();
        for (Graph graph : readGraphs(file)) {
            if (graph.iri()) {
                map.put(new RoIri(graph.id(), iriDict), graph.rows());
            }
        }
        return map;
    }

    protected static Map<RoBnode, RoaringBitmap> getRoBnodeGraphsMap(File file) throws IOException {
        Map<RoBnode, RoaringBitmap> map = new HashMap<>();
        for (Graph graph : readGraphs(file)) {
            if (!graph.iri()) {
                map.put(new RoBnode(graph.id()), graph.rows());
            }
        }
        return map;
    }

    public RoResource findGraphForTriple(int at) {
        // return graph if there is only one
        if (iriGraphsMap.size() == 1 && bNodeGraphsMap.isEmpty()) {
            return iriGraphsMap.entrySet().iterator().next().getKey();
        } else if (iriGraphsMap.isEmpty() && bNodeGraphsMap.size() == 1) {
            return bNodeGraphsMap.entrySet().iterator().next().getKey();
        }

        for (Entry<RoIri, RoaringBitmap> en : iriGraphsMap.entrySet()) {
            if (en.getValue().contains(at)) {
                return en.getKey();
            }
        }
        for (Entry<RoBnode, RoaringBitmap> en : bNodeGraphsMap.entrySet()) {
            if (en.getValue().contains(at)) {
                return en.getKey();
            }
        }
        return null;
    }

    public Stream<Statement> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    @Override
    public Iterator<Statement> iterator() {
        return read(bySubject, objectColumn, present, SCAN_BATCH_SIZE);
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
        if (subject == null && object == null && objects == null) {
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
        Iterator<Statement> iterator = read(table, column, filter, LOOKUP_BATCH_SIZE);
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }

    private static FilterPredicate and(FilterPredicate left, FilterPredicate right) {
        return left == null ? right : right == null ? left : FilterPredicate.and(left, right);
    }

    /**
     * Reads the subject and object columns of a table, the rows matching the
     * filter if there is one.
     */
    private Iterator<Statement> read(ParquetTable table, String column, FilterPredicate filter, int batchSize) {
        Optional<ColumnReaders> batches = filter == null
                ? table.columns(batchSize, SUBJECT, column)
                : table.columns(filter, batchSize, SUBJECT, column);
        return batches.<Iterator<Statement>>map(this::newIterator).orElseGet(Collections::emptyIterator);
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
        return read(bySubject, objectColumn, and(present, FilterPredicate.eq(objectColumn, object.id())),
                SCAN_BATCH_SIZE);
    }


    protected abstract RoResourceValueListIterator newIterator(ColumnReaders rows);

    protected static abstract class AbstractBuilder<S extends RoResource, O extends RoValue> {

        protected final File file;
        protected final RoIri predicate;
        protected final Map<RoIri, RoaringBitmap> iriGraphsMap = new HashMap<>();
        protected final Map<RoBnode, RoaringBitmap> bnodeGraphsMap = new HashMap<>();
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

        protected RoaringBitmap bitmapForContext(final RoResource context) {
            if (context instanceof RoIri) {
                return bitmapForContext((RoIri) context);
            } else if (context instanceof RoBnode) {
                return bitmapForContext((RoBnode) context);
            } else {
                return null;
            }
        }

        private RoaringBitmap bitmapForContext(final RoIri context) {
            RoaringBitmap filter = iriGraphsMap.get(context);
            if (filter == null) {
                filter = new RoaringBitmap();
                iriGraphsMap.put(context, filter);
            }
            return filter;
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
         * Writes the rows per named graph, if the list has triples in named
         * graphs; otherwise removes the file an earlier load left.
         */
        protected void saveContextBitmaps() throws IOException {
            List<Graph> graphs = new ArrayList<>();
            for (Entry<RoIri, RoaringBitmap> en : iriGraphsMap.entrySet()) {
                graphs.add(new Graph(true, en.getKey().getLongId(), en.getValue()));
            }
            for (Entry<RoBnode, RoaringBitmap> en : bnodeGraphsMap.entrySet()) {
                graphs.add(new Graph(false, en.getKey().getLongId(), en.getValue()));
            }
            writeGraphs(file, graphs);
        }

        private RoaringBitmap bitmapForContext(final RoBnode context) {
            RoaringBitmap filter = bnodeGraphsMap.get(context);
            if (filter == null) {
                filter = new RoaringBitmap();
                bnodeGraphsMap.put(context, filter);
            }
            return filter;
        }

        /**
         * Writes the copy of the list sorted by object, from the list. It
         * has no contexts: those are read from the list.
         */
        private void writeByObject() throws IOException {
            SortedTripleWriter sorted = new SortedTripleWriter(byObjectFile(file), hardwood, true);
            try (ParquetTable list = ParquetTable.open(hardwood, file)) {
                Optional<ColumnReaders> batches = list.columns(SCAN_BATCH_SIZE, SUBJECT, OBJECT);
                if (batches.isPresent()) {
                    try (ColumnReaders rows = batches.get()) {
                        while (rows.nextBatch()) {
                            long[] subjects = rows.getColumnReader(SUBJECT).getLongs();
                            long[] objects = rows.getColumnReader(OBJECT).getLongs();
                            for (int i = 0; i < subjects.length; i++) {
                                sorted.add(subjects[i], objects[i], SortedTripleWriter.NO_CONTEXT);
                            }
                        }
                    }
                }
            }
            sorted.finish((context, row) -> {
            });
        }

        /**
         * Writes the list sorted by subject and the rows of each context.
         */
        protected void save() throws IOException {
            triples.finish((context, row) -> bitmapForContext(contexts.get(context)).add((int) row));
            writeByObject();
            saveContextBitmaps();
        }
    }

    protected abstract class RoResourceValueListIterator
            implements Iterator<Statement>, AutoCloseable {

        private final ColumnReaders batches;
        private boolean open = true;
        private long[] subjects;
        private long[] objects;
        private int next;

        protected RoResourceValueListIterator(ColumnReaders batches) {
            this.batches = batches;
        }

        @Override
        public Statement next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            S subject = getSubject(subjects[next]);
            O object = getObject(objects[next]);
            next++;
            return statement(subject, predicate, object, null);
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
                batches.close();
                open = false;
            }
        }

        @Override
        public boolean hasNext() {
            while (open && (subjects == null || next == subjects.length)) {
                if (batches.nextBatch()) {
                    // the subject column comes first in every file, the
                    // object column second, whatever its name
                    subjects = batches.getColumnReader(0).getLongs();
                    objects = batches.getColumnReader(1).getLongs();
                    next = 0;
                } else {
                    batches.close();
                    open = false;
                }
            }
            return open;
        }
    }
}
