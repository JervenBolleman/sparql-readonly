package sib.swiss.swissprot.sparql.ro.quads;

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

import org.eclipse.rdf4j.model.Statement;
import org.roaringbitmap.RoaringBitmap;

import sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
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
	protected final RoNamespaces namespaces;
	protected final RoIriDictionary iriDictionary;

	protected static long triplesInFile(File file) {
		return file.length() / (Long.BYTES * 2);
	}

	protected RoResourceRoValueList(File file, RoIri predicate,
			Map<RoIri, RoaringBitmap> iriGraphsMap,
			Map<RoBnode, RoaringBitmap> bNodeGraphsMap, RoNamespaces namespaces,
			RoIriDictionary dictionary) throws IOException {
		super();
		this.predicate = predicate;
		this.iriGraphsMap = iriGraphsMap;
		this.bNodeGraphsMap = bNodeGraphsMap;
		this.namespaces = namespaces;
		this.iriDictionary = dictionary;
		this.triples = ByteBuffersBackedByFilesTools
				.openLongBuffer(file.toPath());
		this.numberOfTriplesInList = triplesInFile(file);

	}

	public RoResourceRoValueList(File file, RoIri predicate)
			throws FileNotFoundException, IOException {
		this(file, predicate, getNamespaces(file), getRoIriDicts(file));
	}

	private static RoIriDictionary getRoIriDicts(File file) {
		// TODO Auto-generated method stub
		return null;
	}

	public RoResourceRoValueList(File file, RoIri predicate,
			RoNamespaces roNamespaces, RoIriDictionary dict)
			throws FileNotFoundException, IOException {
		this(file, predicate, getIriGraphsMap(file, dict),
				getRoBnodeGraphsMap(file, roNamespaces), roNamespaces, dict);
	}

	private static RoNamespaces getNamespaces(File file)
			throws FileNotFoundException, IOException {

		File roNamespacesFile = getRoNamespacesFile(file);
		RoNamespaces roNamespaces = new RoNamespaces();
		try (ObjectInputStream in = new ObjectInputStream(
				new FileInputStream(roNamespacesFile))) {
			String prefix = in.readUTF();
			String namespace = in.readUTF();
			roNamespaces.add(prefix, namespace);

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

	private static File getIriGraphsFile(File file) {
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

	private static Map<RoBnode, RoaringBitmap> getRoBnodeGraphsMap(File file,
			RoNamespaces roNamespaces)
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

	private static File getRoNamespacesFile(File file) {
		return new File(file.getParentFile(), file.getName() + "namespaces");
	}

	public RoResource findGraphForTriple(int at) {
		// return graph if there is only one
		if (iriGraphsMap.size() == 1 && bNodeGraphsMap.isEmpty())
			return iriGraphsMap.entrySet().iterator().next().getKey();
		else if (iriGraphsMap.isEmpty() && bNodeGraphsMap.size() == 1)
			return bNodeGraphsMap.entrySet().iterator().next().getKey();

		for (Entry<RoIri, RoaringBitmap> en : iriGraphsMap.entrySet()) {
			if (en.getValue().contains(at))
				return en.getKey();
		}
		for (Entry<RoBnode, RoaringBitmap> en : bNodeGraphsMap.entrySet()) {
			if (en.getValue().contains(at))
				return en.getKey();
		}
		return null;
	}

	protected static abstract class AbstractBuilder {
		protected final File file;
		protected final RoIri predicate;
		protected final RoIriDictionary iriDictionary;
		protected final Map<RoIri, RoaringBitmap> iriGraphsMap = new HashMap<>();
		protected final Map<RoBnode, RoaringBitmap> bNodeGraphsMap = new HashMap<>();
		protected final DataOutputStream das;
		protected long numberOfTriplesInList;
		protected final RoNamespaces namespaces;

		protected AbstractBuilder(File file, RoIri predicate,
				RoNamespaces namespaces, RoIriDictionary iriDictionary)
				throws FileNotFoundException {
			super();
			this.file = file;
			this.predicate = predicate;
			this.iriDictionary = iriDictionary;
			this.namespaces = namespaces;
			das = new DataOutputStream(new FileOutputStream(file, true));
			numberOfTriplesInList = triplesInFile(file);
		}

		protected RoaringBitmap bitmapForContext(final RoResource context) {
			if (context instanceof RoIri)
				return bitmapForContext((RoIri) context);
			else
				return bitmapForContext((RoBnode) context);
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

			bitmapForContext(context).add((int) numberOfTriplesInList);
		}

		public void add(RoResource subject, RoLiteral object,
				RoResource context) throws IOException {

			das.writeLong(subject.getLongId());
			das.writeLong(object.getLongId());
			numberOfTriplesInList++;

			bitmapForContext(context).add((int) numberOfTriplesInList);
		}

		protected void saveContextBitmaps()
				throws FileNotFoundException, IOException {
			writeMapToDisk(getIriGraphsFile(file), iriGraphsMap);
			writeMapToDisk(getBnodeGraphsFile(file), bNodeGraphsMap);
		}

		private void writeMapToDisk(File graphs,
				Map<? extends RoResource, RoaringBitmap> map)
				throws IOException, FileNotFoundException {
			try (ObjectOutputStream out = new ObjectOutputStream(
					new FileOutputStream(graphs))) {
				out.write(map.size());
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
			RoaringBitmap filter = bNodeGraphsMap.get(context);
			if (filter == null) {
				filter = new RoaringBitmap();
				bNodeGraphsMap.put(context, filter);
			}
			return filter;
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
			return at < numberOfTriplesInList;
		}

		protected abstract S getSubjectFromLong(long id);

		protected abstract O getObjectFromLong(long id);
	}
}