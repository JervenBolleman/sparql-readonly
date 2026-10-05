package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.rdf4j.model.IRI;

import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

/**
 * The local names of one namespace, sorted, one after the other in the file
 * of local names all such namespaces share, see {@link LinearValues}. The
 * local id of a name is its offset from the first name of the namespace.
 */
public class BasicRoIriNamespaceDictionary implements RoIriNamespaceDictionary {

	private static final int BITS_IN_A_LONG = Long.BYTES * 8;

	private final LinearValues iris;
	private final HashIndex index;
	private final RoNamespace roNamespace;
	private final RoIriDictionary roIriDictionary;
	private final long start;
	private final long end;

	/**
	 * @param iris the shared file of local names, closed by the
	 * RoIriDictionary, as is its index
	 * @param start the offset of the first name of the namespace
	 * @param end the offset after its last name
	 */
	public BasicRoIriNamespaceDictionary(LinearValues iris, HashIndex index, RoNamespace roNamespace, long start,
			long end, RoIriDictionary roIriDictionary) {
		this.iris = iris;
		this.index = index;
		this.roNamespace = roNamespace;
		this.start = start;
		this.end = end;
		this.roIriDictionary = roIriDictionary;
	}

	@Override
	public Optional<String> getLocalNameFromId(long id) {
		if ((id >>> (BITS_IN_A_LONG - RoNamespaces.BITS_FOR_NAMESPACES)) != roNamespace.getId()) {
			return Optional.empty();
		}
		long offset = start + (id & 0xFFFF_FFFFL);
		if (offset >= end) {
			return Optional.empty();
		}
		return Optional.of(iris.string(offset));
	}

	@Override
	public String getNamespace() {
		return roNamespace.getName();
	}

	@Override
	public long getNamespaceId() {
		return roNamespace.getId();
	}

	@Override
	public Optional<RoIri> find(IRI iri) {
		if (!RoIriDictionary.storedNamespace(iri).equals(roNamespace.getName())) {
			return Optional.empty();
		}
		byte[] localName = RoIriDictionary.storedLocalName(iri).getBytes(StandardCharsets.UTF_8);
		long offset = index.find(HashIndex.hash(localName, roNamespace.getId()),
				at -> at >= start && at < end && iris.equalsAt(at, localName));
		if (offset < 0) {
			return Optional.empty();
		}
		return Optional.of(new RoIri(nameSpacedId(offset - start), roIriDictionary, iri));
	}

	private long nameSpacedId(long id) {
		return id | (roNamespace.getId() << RoNamespaces.BITS_FOR_NAMESPACES);
	}

	@Override
	public Stream<IRI> values() {
		return Stream.empty();
	}
}
