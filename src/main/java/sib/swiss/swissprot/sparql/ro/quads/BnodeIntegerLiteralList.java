package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.rdf4j.model.Statement;
import org.roaringbitmap.RoaringBitmap;

import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class BnodeIntegerLiteralList extends RoResourceRoValueList {

	public BnodeIntegerLiteralList(File file, RoIri predicate)
			throws FileNotFoundException, IOException {
		super(file, predicate);
	}

	public BnodeIntegerLiteralList(File file, RoIri predicate,
			Map<RoBnode, RoaringBitmap> bNodeGraphsMap,
			Map<RoIri, RoaringBitmap> iriGraphsMap, RoNamespaces roNamespaces,
			RoIriDictionary iriDictionary) throws IOException {
		super(file, predicate, iriGraphsMap, bNodeGraphsMap, roNamespaces,
				iriDictionary);
	}

	@Override
	public Iterator<Statement> iterator() {
		return new BnodeIntegerListIterator();
	}

	public static class Builder extends AbstractBuilder {
		public Builder(File file, RoIri predicate, RoNamespaces roNamespaces,
				RoIriDictionary iriDictionary) throws IOException {
			super(file, predicate, roNamespaces, iriDictionary);
		}

		public BnodeIntegerLiteralList build() throws IOException {
			das.close();
			return new BnodeIntegerLiteralList(file, predicate, bNodeGraphsMap,
					iriGraphsMap, namespaces, iriDictionary);
		}
	}

	private class BnodeIntegerListIterator
			extends ResourceValueListIterator<RoBnode, RoIntegerLiteral> {

		@Override
		protected RoBnode getSubjectFromLong(long id) {
			return new RoBnode(id);
		}

		@Override
		protected RoIntegerLiteral getObjectFromLong(long id) {
			return new RoIntegerLiteral(id);
		}
	}

}
