package sib.swiss.swissprot.sparql.ro.quads;

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
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
    protected final ParquetTable triples;
    /**
     * The same triples sorted by object, for lookups of objects.
     */
    private final ParquetTable byObject;

    protected final RoDictionaries dictionaries;

    protected long triplesInFile() {
        return triples.numberOfRows();
    }

    protected abstract S getSubject(long id);

    protected abstract O getObject(long id);

    protected RoResourceRoValueList(File file, RoIri predicate,
            Map<RoIri, RoaringBitmap> iriGraphsMap,
            Map<RoBnode, RoaringBitmap> bNodeGraphsMap,
            RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super();
        this.predicate = predicate;
        this.iriGraphsMap = iriGraphsMap;
        this.bNodeGraphsMap = bNodeGraphsMap;
        this.triples = ParquetTable.open(hardwood, file);
        this.byObject = ParquetTable.open(hardwood, byObjectFile(file));
        this.dictionaries = dictionaries;
    }

    @Override
    public void close() throws IOException {
        triples.close();
        byObject.close();
    }

    static File byObjectFile(File file) {
        return new File(file.getParentFile(), file.getName() + "-by-object");
    }

    protected static File getIriGraphsFile(File file) {
        return new File(file.getParentFile(), file.getName() + "irigraphs");
    }

    private static void readData(ObjectInputStream in,
            long[] graphIds, RoaringBitmap[] maps)
            throws IOException, ClassNotFoundException {

        for (int i = 0; i < graphIds.length; i++) {
            graphIds[i] = in.readLong();
        }
        for (int i = 0; i < graphIds.length; i++) {
            RoaringBitmap map = (RoaringBitmap) in.readObject();
            maps[i] = map;
        }
    }

    protected static Map<RoIri, RoaringBitmap> getRoIriGraphsMap(File file, RoIriDictionary iriDict)
            throws FileNotFoundException, IOException {
        File graphs = getBnodeGraphsFile(file);
        try (ObjectInputStream in = new ObjectInputStream(
                new FileInputStream(graphs))) {
            int numberOfGraphs = in.readInt();
            long[] graphIds = new long[numberOfGraphs];
            RoaringBitmap[] roaringBitmaps = new RoaringBitmap[numberOfGraphs];
            readData(in, graphIds, roaringBitmaps);

            Map<RoIri, RoaringBitmap> map = new HashMap<>();
            for (int i = 0; i < numberOfGraphs; i++) {
                map.put(new RoIri(graphIds[i], iriDict), roaringBitmaps[i]);
            }
            return map;
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    protected static Map<RoBnode, RoaringBitmap> getRoBnodeGraphsMap(File file)
            throws FileNotFoundException, IOException {
        File graphs = getBnodeGraphsFile(file);
        try (ObjectInputStream in = new ObjectInputStream(
                new FileInputStream(graphs))) {
            int numberOfGraphs = in.readInt();
            long[] graphIds = new long[numberOfGraphs];
            RoaringBitmap[] roaringBitmaps = new RoaringBitmap[numberOfGraphs];
            readData(in, graphIds, roaringBitmaps);

            Map<RoBnode, RoaringBitmap> map = new HashMap<>();
            for (int i = 0; i < numberOfGraphs; i++) {
                map.put(new RoBnode(graphIds[i]), roaringBitmaps[i]);
            }
            return map;
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    private static File getBnodeGraphsFile(File file) {
        return new File(file.getParentFile(), file.getName() + "bnodegraphs");
    }

    protected static File getRoNamespacesFile(File file) {
        final File file1 = new File(file.getParentFile(), file.getName() + "namespaces");
        return file1;
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
        return triples.columns(SCAN_BATCH_SIZE, SUBJECT, OBJECT)
                .<Iterator<Statement>>map(this::newIterator)
                .orElseGet(Collections::emptyIterator);
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
     * @param objects a further condition on the object column, null for none
     */
    public Stream<Statement> stream(Term subject, Term object, FilterPredicate objects) {
        FilterPredicate filter = objects;
        if (subject != null) {
            FilterPredicate onSubject = FilterPredicate.eq(SUBJECT, subject.id());
            filter = filter == null ? onSubject : FilterPredicate.and(filter, onSubject);
        }
        if (object != null) {
            FilterPredicate onObject = FilterPredicate.eq(OBJECT, object.id());
            filter = filter == null ? onObject : FilterPredicate.and(filter, onObject);
        }
        if (filter == null) {
            return stream();
        }
        // sorted on the column that is filtered, so that statistics skip
        ParquetTable table = subject == null && !byObject.isEmpty() ? byObject : triples;
        Iterator<Statement> iterator = table.columns(filter, LOOKUP_BATCH_SIZE, SUBJECT, OBJECT)
                .<Iterator<Statement>>map(this::newIterator)
                .orElseGet(Collections::emptyIterator);
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
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

        protected void saveNamespace(RoNamespaces namespaces, File file) throws IOException {
            File roNamespacesFile = getRoNamespacesFile(file);
            try (DataOutputStream dasN = new DataOutputStream(new FileOutputStream(roNamespacesFile))) {
                dasN.writeInt(namespaces.size());

                for (RoNamespace en : namespaces) {
                    if (en.getPrefix() != null) {
                        dasN.writeBoolean(true);
                        dasN.writeUTF(en.getPrefix());
                    } else {
                        dasN.writeBoolean(false);
                    }
                    dasN.writeUTF(en.getName());
                    dasN.writeLong(en.getId());
                }
            }
        }

        protected void saveContextBitmaps()
                throws FileNotFoundException, IOException {
            writeMapToDisk(getIriGraphsFile(file), iriGraphsMap);
            writeMapToDisk(getBnodeGraphsFile(file), bnodeGraphsMap);
        }

        private void writeMapToDisk(File graphs,
                Map<? extends RoResource, RoaringBitmap> map)
                throws IOException, FileNotFoundException {
            try (ObjectOutputStream out = new ObjectOutputStream(
                    new FileOutputStream(graphs))) {
                out.writeInt(map.size());
                for (Entry<? extends RoResource, RoaringBitmap> en : map
                        .entrySet()) {
                    out.writeLong(en.getKey().getLongId());
                }
                for (Entry<? extends RoResource, RoaringBitmap> en : map
                        .entrySet()) {
                    out.writeObject(en.getValue());
                }
            }
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
            saveNamespace(dictionaries.getIriDict().getNamespaces(), file);
        }
    }

    protected abstract class RoResourceValueListIterator
            implements Iterator<Statement> {

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

        @Override
        public boolean hasNext() {
            while (open && (subjects == null || next == subjects.length)) {
                if (batches.nextBatch()) {
                    subjects = batches.getColumnReader(SUBJECT).getLongs();
                    objects = batches.getColumnReader(OBJECT).getLongs();
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
