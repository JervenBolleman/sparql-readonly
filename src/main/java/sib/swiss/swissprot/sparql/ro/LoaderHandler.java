package sib.swiss.swissprot.sparql.ro;

import java.io.IOException;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.RDFHandlerException;

import sib.swiss.swissprot.sparql.temporary.dictionaries.TempBNodeDictionary;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempIriDictionary;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempLitalDictionary;

public class LoaderHandler implements RDFHandler {

	private final RoStore roStore;
	private final TempIriDictionary iris;
	private final TempLitalDictionary literals;
	private final TempBNodeDictionary bnodes;

	public LoaderHandler(RoStore roStore, TempBNodeDictionary bnodes,
			TempIriDictionary iris, TempLitalDictionary dict) {
		this.roStore = roStore;
		this.bnodes = bnodes;
		this.iris = iris;
		this.literals = dict;
	}

	@Override
	public void startRDF() throws RDFHandlerException {
		// TODO Auto-generated method stub

	}

	@Override
	public void endRDF() throws RDFHandlerException {
		// TODO Auto-generated method stub

	}

	@Override
	public void handleNamespace(String prefix, String uri)
			throws RDFHandlerException {
		RoNamespace ns = new RoNamespace(prefix, uri);
		roStore.getNamespaces().put(prefix, ns);
	}

	@Override
	public void handleStatement(Statement st) throws RDFHandlerException {
		roStore.getPredicateStore(st.getPredicate());
		try {
			addResource(st.getSubject());
			addIri(st.getPredicate());
			addObject(st.getObject());
			addResource(st.getContext());
		} catch (IOException e) {
			throw new RDFHandlerException(e);
		}
	}

	@Override
	public void handleComment(String comment) throws RDFHandlerException {
		// TODO Auto-generated method stub

	}

	public void addResource(Resource subject) throws IOException {
		if (subject instanceof IRI)
			addIri((IRI) subject);
		else if (subject instanceof BNode)
			addBnode((BNode) subject);

	}

	void addBnode(BNode subject) throws IOException {
		bnodes.add(subject);

	}

	void addIri(IRI subject) throws IOException {
		iris.add(subject);
	}

	void addObject(Value object) throws IOException {
		if (object instanceof Resource)
			addResource((Resource) object);
		if (object instanceof Literal)
			addLiteral((Literal) object);

	}

	void addLiteral(Literal object) throws IOException {
		literals.add(object);
	}
}
