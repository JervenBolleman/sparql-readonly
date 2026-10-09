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
 * of compressed local names all such namespaces share, see
 * {@link CompressedValues}. The local id of a name is its id there, less the
 * id where the names of the namespace start.
 */
public class BasicRoIriNamespaceDictionary implements RoIriNamespaceDictionary {


	private final CompressedValues iris;
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
	public BasicRoIriNamespaceDictionary(CompressedValues iris, HashIndex index, RoNamespace roNamespace, long start,
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
		if (RoNamespaces.namespaceOf(id) != roNamespace.getId()) {
			return Optional.empty();
		}
		long name = CompressedValues.idAt(start) + RoNamespaces.localOf(id);
		if (name >= CompressedValues.idAt(end)) {
			return Optional.empty();
		}
		return Optional.of(iris.string(name));
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
		long first = CompressedValues.idAt(start);
		long last = CompressedValues.idAt(end);
		long name = index.find(HashIndex.hash(localName, roNamespace.getId()),
				at -> at >= first && at < last && iris.equalsAt(at, localName));
		if (name < 0) {
			return Optional.empty();
		}
		return Optional.of(new RoIri(nameSpacedId(name - first), roIriDictionary, iri));
	}

	private long nameSpacedId(long id) {
		return RoNamespaces.iriId(roNamespace.getId(), id);
	}

	@Override
	public Stream<IRI> values() {
		return Stream.empty();
	}
}
