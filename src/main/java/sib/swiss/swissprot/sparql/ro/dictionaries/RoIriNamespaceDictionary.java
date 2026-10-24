package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.rdf4j.model.IRI;

import sib.swiss.swissprot.sparql.ro.values.RoIri;

public interface RoIriNamespaceDictionary extends Closeable {
	public Optional<String> getLocalNameFromId(long id) throws IOException;

	public String getNamespace();

	public long getNamespaceId();

	public Optional<RoIri> find(IRI predicate);

	public Stream<IRI> values();

	@Override
	public default void close() throws IOException {
	}
}
