package sib.swiss.swissprot.sparql.ro.quads;

import static sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools.getLongAtIndexInLongBuffers;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.rdf4j.model.Statement;
import org.roaringbitmap.RoaringBitmap;

import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;

public class BnodeBnodeList extends RoResourceRoValueList {

	public BnodeBnodeList(File file, RoIri predicate,
			Map<RoBnode, RoaringBitmap> bNodeGraphsMap,
			Map<RoIri, RoaringBitmap> iriGraphsMap, RoNamespaces roNamespaces,
			RoIriDictionary iriDictionary) throws IOException {
		super(file, predicate, iriGraphsMap, bNodeGraphsMap, roNamespaces,
				iriDictionary);
	}

	public BnodeBnodeList(File file, RoIri predicate) throws IOException {
		super(file, predicate);
	}

	public static class Builder extends AbstractBuilder {
		public Builder(File file, RoIri predicate, RoNamespaces roNamespaces,
				RoIriDictionary iriDictionary) throws IOException {
			super(file, predicate, roNamespaces, iriDictionary);
		}

		public BnodeBnodeList build() throws IOException {
			das.close();
			saveContextBitmaps();
			return new BnodeBnodeList(file, predicate, bNodeGraphsMap,
					iriGraphsMap, namespaces, iriDictionary);
		}
	}

	private class BnodeBnodeListIterator implements Iterator<Statement> {
		private int at = 0;

		@Override
		public boolean hasNext() {
			return at < numberOfTriplesInList;
		}

		@Override
		public Statement next() {
			final RoResource graph = findGraphForTriple(at);
			long subjectId = getLongAtIndexInLongBuffers(at, triples);
			long objectId = getLongAtIndexInLongBuffers(at++, triples);
			at++;
			return new RoContextStatement(new RoBnode(subjectId), predicate,
					new RoBnode(objectId), graph);

		}

	}

	@Override
	public Iterator<Statement> iterator() {
		return new BnodeBnodeListIterator();
	}

}
