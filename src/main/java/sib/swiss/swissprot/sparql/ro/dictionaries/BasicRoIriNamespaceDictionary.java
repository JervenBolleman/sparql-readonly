package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.rdf4j.model.IRI;

import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class BasicRoIriNamespaceDictionary extends RoDictionary<RoIri, IRI>
		implements RoIriNamespaceDictionary {

	private final RoNamespace roNamespace;

	public BasicRoIriNamespaceDictionary(long[] offSetMap,
			ByteBuffer[] backingFile, RoNamespace roNamespace) {
		super(offSetMap, backingFile);
		this.roNamespace = roNamespace;
	}

	@Override
	public Optional<String> getLocalNameFromId(long id) throws IOException {
		int withoutMask = (int) id;
		long offset = getOffset(withoutMask);
		if (offset > -1)
			return Optional.of(readStringAt(offset));
		else
			return Optional.empty();

	}

	@Override
	public String getNamespace() {
		return roNamespace.getName();
	}

	@Override
	public int getNamespaceId() {
		return roNamespace.getId();
	}

	@Override
	public Optional<RoIri> find(IRI predicate) {
		if (predicate.getNamespace().equals(roNamespace.getName())) {

		}
		return Optional.empty();
	}

	@Override
	public Stream<IRI> values() {
		// TODO Auto-generated method stub
		return Stream.empty();
	}
}