package sib.swiss.swissprot.sparql.ro.quads;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.OrcFile;
import org.apache.orc.Reader;
import org.apache.orc.RecordReader;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;

import org.eclipse.rdf4j.model.Statement;
import org.roaringbitmap.RoaringBitmap;

import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;
import sib.swiss.swissprot.sparql.ro.values.RoValue;

public abstract class RoResourceRoValueList<S extends RoResource, O extends RoValue> implements Iterable<Statement> {

    protected static final String SUBJECT = "subject";
    protected static final String OBJECT = "object";
    protected static final int SUBECT_COL = 0;
    protected static final int OBJECT_COL = 1;
    protected final Map<RoIri, RoaringBitmap> iriGraphsMap;
    protected final Map<RoBnode, RoaringBitmap> bNodeGraphsMap;
    protected final RoIri predicate;
    protected final Reader triples;

    protected final RoDictionaries dictionaries;

    protected long triplesInFile() {
        return triples.getNumberOfRows();
    }

    protected abstract S getSubject(VectorizedRowBatch batch, int index);

    protected abstract O getObject(VectorizedRowBatch batch, int index);

    protected RoResourceRoValueList(File dir, RoIri predicate,
            Map<RoIri, RoaringBitmap> iriGraphsMap,
            Map<RoBnode, RoaringBitmap> bNodeGraphsMap,
            RoDictionaries dictionaries, Configuration conf) throws IOException {
        super();
        this.predicate = predicate;
        this.iriGraphsMap = iriGraphsMap;
        this.bNodeGraphsMap = bNodeGraphsMap;
        if (!dir.exists()) {
            dir.createNewFile();
        }

        final Path path = new Path(dir.getParent(), dir.getName());
        this.triples = OrcFile.createReader(path, OrcFile.readerOptions(conf));
        this.dictionaries = dictionaries;
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

    protected static abstract class AbstractBuilder<S extends RoResource, O extends RoValue> {

        protected final File file;
        protected final RoIri predicate;
        protected final Map<RoIri, RoaringBitmap> iriGraphsMap = new HashMap<>();
        protected final Map<RoBnode, RoaringBitmap> bnodeGraphsMap = new HashMap<>();
        protected final Writer writer;
        protected long numberOfTriplesInList;
        protected final RoDictionaries dictionaries;
        protected final Configuration configuration;
        private final VectorizedRowBatch batch;
        private static final int OBJECT_COL = 1;
        private static final int SUBJECT_COL = 0;

        protected AbstractBuilder(File file, RoIri predicate,
                RoDictionaries dictionaries, Configuration configuration, Supplier<TypeDescription> schemaF)
                throws FileNotFoundException, IOException {
            super();
            this.file = file;
            this.predicate = predicate;
            final TypeDescription schema = schemaF.get();
            this.writer = OrcFile.createWriter(new Path(file.getAbsolutePath()),
                    OrcFile.writerOptions(configuration)
                            .setSchema(schema));
            this.dictionaries = dictionaries;
            this.configuration = configuration;
            this.batch = schema.createRowBatch();
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
            if (batch.size == batch.getMaxSize()) {
                writer.addRowBatch(batch);
                batch.reset();
            }
            numberOfTriplesInList++;
            addSubject(batch, subject, numberOfTriplesInList);
            addObject(batch, object, numberOfTriplesInList);
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

        protected void save() throws IOException {
            writer.addRowBatch(batch);
            writer.close();
            saveContextBitmaps();
            saveNamespace(dictionaries.getIriDict().getNamespaces(), file);
        }

        protected final void addSubject(VectorizedRowBatch batch, S subject, long numberOfTriplesInList1) {
            LongColumnVector col = (LongColumnVector) batch.cols[SUBJECT_COL];
            col.vector[batch.size] = subject.getLongId();
        }

        protected void addObject(VectorizedRowBatch batch, O object, long numberOfTriplesInList1) {
            LongColumnVector col = (LongColumnVector) batch.cols[OBJECT_COL];
            col.vector[batch.size++] = object.getLongId();
        }
    }

    protected abstract class RoResourceValueListIterator
            implements Iterator<Statement> {

        private final RecordReader rows;
        protected final VectorizedRowBatch batch;
        protected int inbatch;

        protected RoResourceValueListIterator(TypeDescription schema) {
            try {
                this.rows = triples.rows();
            } catch (IOException ex) {
                Logger.getLogger(RoResourceRoValueList.class.getName()).log(Level.SEVERE, null, ex);
                throw new RuntimeException(ex);
            }
            this.batch = schema.createRowBatch();
            this.inbatch = 0;
        }

        @Override
        public Statement next() {
            S subject = getSubject(batch, inbatch);
            O object = getObject(batch, inbatch);
            this.inbatch++;
            return statement(subject, predicate, object, null);
        }

        public Statement statement(S subject, RoIri predicate, O object, RoResource graph) {
            return new RoContextStatement(subject, predicate,
                    object, graph);
        }

        @Override
        public boolean hasNext() {

            try {
                if (batch.size == 0) {
                    rows.nextBatch(batch);
                }
                if (batch != null) {
                    return inbatch < batch.count();
                }
                inbatch = 0;
                return rows.nextBatch(batch);
            } catch (IOException ex) {
                return false;
            }
        }
    }
}
