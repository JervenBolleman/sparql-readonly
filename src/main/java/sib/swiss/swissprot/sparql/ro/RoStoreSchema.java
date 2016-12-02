package sib.swiss.swissprot.sparql.ro;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

public class RoStoreSchema {
	private static final String ROSPARQL_NAMESPACE = "http://sib.swiss/swiss-prot/sparql/readonly/config#";
	public static final IRI FILE = SimpleValueFactory.getInstance().createIRI(
			ROSPARQL_NAMESPACE, "file");
}
