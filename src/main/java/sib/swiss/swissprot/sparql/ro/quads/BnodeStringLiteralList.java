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
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;

public class BnodeStringLiteralList extends RoResourceRoValueList {

	private final RoLiteralDict dict;

	public BnodeStringLiteralList(File file, RoIri predicate,
			RoLiteralDict dict) throws FileNotFoundException, IOException {
		super(file, predicate);
		this.dict = dict;
	}

	public BnodeStringLiteralList(File file, RoIri predicate,
			Map<RoBnode, RoaringBitmap> bNodeGraphsMap,
			Map<RoIri, RoaringBitmap> iriGraphsMap, RoLiteralDict dict,
			RoNamespaces roNamespaces, RoIriDictionary iriDictionary)
			throws IOException {
		super(file, predicate, iriGraphsMap, bNodeGraphsMap, roNamespaces,
				iriDictionary,null);
		this.dict = dict;
	}

	@Override
	public Iterator<Statement> iterator() {
		return new BnodeStringListIterator();
	}

	public static class Builder extends AbstractBuilder {

		public Builder(File file, RoIri predicate, RoLiteralDict dict,
				RoNamespaces roNamespaces, RoIriDictionary iriDictionary)
				throws IOException {
			super(file, predicate, roNamespaces, iriDictionary, dict);
		}

		public BnodeStringLiteralList build() throws IOException {
			das.close();
			saveContextBitmaps();
			return new BnodeStringLiteralList(file, predicate, bNodeGraphsMap,
					iriGraphsMap, literalDictionary, namespaces, iriDictionary);
		}
	}

	private class BnodeStringListIterator
			extends ResourceValueListIterator<RoBnode, RoLiteral> {

		@Override
		protected RoBnode getSubjectFromLong(long id) {
			return new RoBnode(id);
		}

		@Override
		protected RoLiteral getObjectFromLong(long id) {
			return dict.get(id);
		}
	}
}
