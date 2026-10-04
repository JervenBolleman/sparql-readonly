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
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import dev.hardwood.Hardwood;
import dev.hardwood.metadata.PhysicalType;
import dev.hardwood.metadata.RepetitionType;
import dev.hardwood.reader.FilterPredicate;
import dev.hardwood.reader.RowReader;
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

    protected static final String SUBJECT = "subject";
    protected static final String OBJECT = "object";
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

    protected final RoDictionaries dictionaries;

    protected long triplesInFile() {
        return triples.numberOfRows();
    }

    protected abstract S getSubject(RowReader row);

    protected abstract O getObject(RowReader row);

    protected RoResourceRoValueList(File file, RoIri predicate,
            Map<RoIri, RoaringBitmap> iriGraphsMap,
            Map<RoBnode, RoaringBitmap> bNodeGraphsMap,
            RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super();
        this.predicate = predicate;
        this.iriGraphsMap = iriGraphsMap;
        this.bNodeGraphsMap = bNodeGraphsMap;
        this.triples = ParquetTable.open(hardwood, file);
        this.dictionaries = dictionaries;
    }

    @Override
    public void close() throws IOException {
        triples.close();
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
        return triples.rows()
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
        FilterPredicate filter = null;
        if (subject != null) {
            filter = FilterPredicate.eq(SUBJECT, subject.id());
        }
        if (object != null) {
            FilterPredicate onObject = FilterPredicate.eq(OBJECT, object.id());
            filter = filter == null ? onObject : FilterPredicate.and(filter, onObject);
        }
        if (filter == null) {
            return stream();
        }
        Iterator<Statement> iterator = triples.rows(filter)
                .<Iterator<Statement>>map(this::newIterator)
                .orElseGet(Collections::emptyIterator);
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }

    protected abstract RoResourceValueListIterator newIterator(RowReader rows);

    protected static abstract class AbstractBuilder<S extends RoResource, O extends RoValue> {

        protected final File file;
        protected final RoIri predicate;
        protected final Map<RoIri, RoaringBitmap> iriGraphsMap = new HashMap<>();
        protected final Map<RoBnode, RoaringBitmap> bnodeGraphsMap = new HashMap<>();
        private static final int BATCH_SIZE = 64 * 1024;
        // Most lists of a predicate stay empty: their writer and batch only
        // take memory once a triple arrives, and an empty list has no file.
        private ParquetTable.Writer writer;
        protected long numberOfTriplesInList;
        protected final RoDictionaries dictionaries;
        protected final Hardwood hardwood;
        private long[] subjects;
        private long[] objects;
        private int inBatch;

        protected AbstractBuilder(File file, RoIri predicate,
                RoDictionaries dictionaries, Hardwood hardwood)
                throws FileNotFoundException, IOException {
            super();
            this.file = file;
            this.predicate = predicate;
            this.dictionaries = dictionaries;
            this.hardwood = hardwood;
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
            if (subjects == null) {
                subjects = new long[BATCH_SIZE];
                objects = new long[BATCH_SIZE];
            } else if (inBatch == BATCH_SIZE) {
                flush();
            }
            numberOfTriplesInList++;
            subjects[inBatch] = subject.getLongId();
            objects[inBatch] = object.getLongId();
            inBatch++;
            final RoaringBitmap bitmapForContext = bitmapForContext(context);
            if (bitmapForContext != null) {
                bitmapForContext.add((int) numberOfTriplesInList);
            }
            //else it is in the default context
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

        private void flush() throws IOException {
            if (inBatch == 0) {
                return;
            }
            if (writer == null) {
                writer = new ParquetTable.Writer(file, SCHEMA, ParquetTable.LIST_CONFIG);
            }
            final long[] s = Arrays.copyOf(subjects, inBatch);
            final long[] o = Arrays.copyOf(objects, inBatch);
            writer.writeBatch(b -> b.longs(SUBJECT, s).longs(OBJECT, o));
            inBatch = 0;
        }

        protected void save() throws IOException {
            flush();
            if (writer != null) {
                writer.close();
            } else if (file.exists() && !file.delete()) {
                // an empty list has no file, not one of an earlier load
                throw new IOException("Could not delete " + file);
            }
            subjects = null;
            objects = null;
            saveContextBitmaps();
            saveNamespace(dictionaries.getIriDict().getNamespaces(), file);
        }
    }

    protected abstract class RoResourceValueListIterator
            implements Iterator<Statement> {

        private final RowReader rows;
        private boolean open = true;

        protected RoResourceValueListIterator(RowReader rows) {
            this.rows = rows;
        }

        @Override
        public Statement next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            rows.next();
            S subject = getSubject(rows);
            O object = getObject(rows);
            return statement(subject, predicate, object, null);
        }

        public Statement statement(S subject, RoIri predicate, O object, RoResource graph) {
            return new RoContextStatement(subject, predicate,
                    object, graph);
        }

        @Override
        public boolean hasNext() {
            if (open && !rows.hasNext()) {
                rows.close();
                open = false;
            }
            return open;
        }
    }
}
