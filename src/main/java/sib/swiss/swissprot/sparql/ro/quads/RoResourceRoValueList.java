package sib.swiss.swissprot.sparql.ro.quads;

import java.io.DataInputStream;
import static sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools.getLongAtIndexInLongBuffers;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.rdf4j.model.Statement;
import org.roaringbitmap.RoaringBitmap;

import sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoBnodeDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoResource;

public abstract class RoResourceRoValueList implements Iterable<Statement> {

    protected final Map<RoIri, RoaringBitmap> iriGraphsMap;
    protected final Map<RoBnode, RoaringBitmap> bNodeGraphsMap;
    protected final RoIri predicate;
    protected final LongBuffer[] triples;
    protected final long numberOfTriplesInList;
    protected final RoDictionaries dictionaries;

    protected static long triplesInFile(File file) {
        return file.length() / (Long.BYTES * 2);
    }

    protected RoResourceRoValueList(File file, RoIri predicate,
            Map<RoIri, RoaringBitmap> iriGraphsMap,
            Map<RoBnode, RoaringBitmap> bNodeGraphsMap,
            RoDictionaries dictionaries) throws IOException {
        super();
        this.predicate = predicate;
        this.iriGraphsMap = iriGraphsMap;
        this.bNodeGraphsMap = bNodeGraphsMap;
        this.triples = ByteBuffersBackedByFilesTools
                .openLongBuffer(file.toPath());
        this.numberOfTriplesInList = triplesInFile(file);
        this.dictionaries = dictionaries;
    }
//
//    public RoResourceRoValueList(File file, RoIri predicate)
//            throws FileNotFoundException, IOException {
//        this(file, predicate, getNamespaces(file), getRoIriDicts(file), getRoLiteralDicts(file), getRoBnodeDictionary(file));
//    }

    private static RoBnodeDictionary getRoBnodeDictionary(File file) {
        // TODO Auto-generated method stub
        return null;
    }

    private static RoIriDictionary getRoIriDicts(File file) {
        // TODO Auto-generated method stub
        return null;
    }

    private static RoLiteralDict getRoLiteralDicts(File file) {
        // TODO Auto-generated method stub
        return null;
    }

    public RoResourceRoValueList(File file, RoIri predicate,
            RoDictionaries dictionaries1)
            throws FileNotFoundException, IOException {
        this(file, predicate, getIriGraphsMap(file, dictionaries1.getIriDict()),
                getRoBnodeGraphsMap(file), dictionaries1);
    }

    private static RoNamespaces getNamespaces(File file)
            throws FileNotFoundException, IOException {

        File roNamespacesFile = getRoNamespacesFile(file);
        RoNamespaces roNamespaces = new RoNamespaces();
        try (DataInputStream in = new DataInputStream(
                new FileInputStream(roNamespacesFile))) {
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                RoNamespace namespace;
                if (in.readBoolean()) {
                    String prefix = in.readUTF();
                    String namespaceV = in.readUTF();
                    long id = in.readLong();
                    namespace = new RoNamespace(prefix, namespaceV, id);
                } else {
                    String namespaceV = in.readUTF();
                    long id = in.readLong();
                    namespace = new RoNamespace(null, namespaceV, id);
                }
                roNamespaces.add(namespace);
            }
        }
        return roNamespaces;
    }

    private static Map<RoIri, RoaringBitmap> getIriGraphsMap(File file,
            RoIriDictionary dict) throws FileNotFoundException, IOException {
        File graphs = getIriGraphsFile(file);

        try (ObjectInputStream in = new ObjectInputStream(
                new FileInputStream(graphs))) {
            int numberOfGraphs = in.readInt();
            long[] graphIds = new long[numberOfGraphs];
            RoaringBitmap[] roaringBitmaps = new RoaringBitmap[numberOfGraphs];
            readData(in, numberOfGraphs, graphIds, roaringBitmaps);

            Map<RoIri, RoaringBitmap> map = new HashMap<>();
            for (int i = 0; i < numberOfGraphs; i++) {
                map.put(new RoIri(graphIds[i], dict), roaringBitmaps[i]);
            }
            return map;
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    protected static File getIriGraphsFile(File file) {
        return new File(file.getParentFile(), file.getName() + "irigraphs");
    }

    private static void readData(ObjectInputStream in, int numberOfGraphs,
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

    private static Map<RoBnode, RoaringBitmap> getRoBnodeGraphsMap(File file)
            throws FileNotFoundException, IOException {
        File graphs = getBnodeGraphsFile(file);
        try (ObjectInputStream in = new ObjectInputStream(
                new FileInputStream(graphs))) {
            int numberOfGraphs = in.readInt();
            long[] graphIds = new long[numberOfGraphs];
            RoaringBitmap[] roaringBitmaps = new RoaringBitmap[numberOfGraphs];
            readData(in, numberOfGraphs, graphIds, roaringBitmaps);

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

    protected static abstract class AbstractBuilder {

        protected final File file;
        protected final RoIri predicate;
        protected final Map<RoIri, RoaringBitmap> iriGraphsMap = new HashMap<>();
        protected final Map<RoBnode, RoaringBitmap> bnodeGraphsMap = new HashMap<>();
        protected final DataOutputStream das;
        protected long numberOfTriplesInList;
        protected final RoDictionaries dictionaries;

        protected AbstractBuilder(File file, RoIri predicate,
                RoDictionaries dictionaries)
                throws FileNotFoundException {
            super();
            this.file = file;
            this.predicate = predicate;
            das = new DataOutputStream(new FileOutputStream(file, true));
            numberOfTriplesInList = triplesInFile(file);
            this.dictionaries = dictionaries;
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

        public void add(RoResource subject, RoResource object,
                RoResource context) throws IOException {

            das.writeLong(subject.getLongId());
            das.writeLong(object.getLongId());
            numberOfTriplesInList++;
            final RoaringBitmap bitmapForContext = bitmapForContext(context);
            if (bitmapForContext != null) {
                bitmapForContext.add((int) numberOfTriplesInList);
            }
            //else it is in the default context
        }

        public void add(RoResource subject, RoLiteral object,
                RoResource context) throws IOException {

            das.writeLong(subject.getLongId());
            das.writeLong(object.getLongId());
            numberOfTriplesInList++;
            final RoaringBitmap bitmapForContext = bitmapForContext(context);
            if (bitmapForContext != null) {
                bitmapForContext.add((int) numberOfTriplesInList);
            }
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
            das.close();
            saveContextBitmaps();
            saveNamespace(dictionaries.getIriDict().getNamespaces(), file);
        }
    }

    protected abstract class ResourceValueListIterator<S, O>
            implements Iterator<Statement> {

        private int at = 0;

        @Override
        public Statement next() {
            final RoResource graph = findGraphForTriple(at);
            long subjectId = getLongAtIndexInLongBuffers(at++, triples); // increment
            // after
            // use
            long objectId = getLongAtIndexInLongBuffers(at++, triples);

            return new RoContextStatement(new RoBnode(subjectId), predicate,
                    new RoIntegerLiteral(objectId), graph);
        }

        @Override
        public boolean hasNext() {
            return at < numberOfTriplesInList * 2;
        }

        protected abstract S getSubjectFromLong(long id);

        protected abstract O getObjectFromLong(long id);
    }
}
