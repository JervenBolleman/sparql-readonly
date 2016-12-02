package sib.swiss.swissprot.sparql.ro.values;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

public class RoValueFactory extends SimpleValueFactory {

	@Override
	public IRI createIRI(String iri) {
		// TODO Auto-generated method stub
		return super.createIRI(iri);
	}

	@Override
	public IRI createIRI(String namespace, String localName) {
		// TODO Auto-generated method stub
		return super.createIRI(namespace, localName);
	}

	@Override
	public BNode createBNode(String nodeID) {
		// TODO Auto-generated method stub
		return super.createBNode(nodeID);
	}

	@Override
	public Literal createLiteral(String value) {
		// TODO Auto-generated method stub
		return super.createLiteral(value);
	}

	@Override
	public Literal createLiteral(String value, String language) {
		// TODO Auto-generated method stub
		return super.createLiteral(value, language);
	}

	@Override
	public Literal createLiteral(String value, IRI datatype) {
		// TODO Auto-generated method stub
		return super.createLiteral(value, datatype);
	}

	@Override
	public Statement createStatement(Resource subject, IRI predicate,
			Value object) {
		// TODO Auto-generated method stub
		return super.createStatement(subject, predicate, object);
	}

	@Override
	public Statement createStatement(Resource subject, IRI predicate,
			Value object, Resource context) {
		// TODO Auto-generated method stub
		return super.createStatement(subject, predicate, object, context);
	}

}
