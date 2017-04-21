package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.rdf4j.model.IRI;

import sib.swiss.swissprot.sparql.ro.values.RoIri;

public interface RoIriNamespaceDictionary {
	public Optional<String> getLocalNameFromId(long id) throws IOException;

	public String getNamespace();

	public int getNamespaceId();

	public Optional<RoIri> find(IRI predicate);

	public Stream<IRI> values();
}
