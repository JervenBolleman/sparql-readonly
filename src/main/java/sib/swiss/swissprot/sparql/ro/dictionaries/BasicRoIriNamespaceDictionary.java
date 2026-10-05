package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.util.Optional;
import java.util.stream.Stream;

import dev.hardwood.reader.FilterPredicate;
import org.eclipse.rdf4j.model.IRI;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

/**
 * The local names of one namespace, as rows of the IRI file all such
 * namespaces share: namespace id, local id, local name, sorted by namespace,
 * then name. The local id is the row of the name within its namespace.
 */
public class BasicRoIriNamespaceDictionary implements RoIriNamespaceDictionary {

	private static final int BITS_IN_A_LONG = Long.BYTES * 8;

	private final ParquetTable iris;
	private final RoNamespace roNamespace;
	private final RoIriDictionary roIriDictionary;

	/**
	 * @param iris the shared IRI file, closed by the RoIriDictionary
	 */
	public BasicRoIriNamespaceDictionary(ParquetTable iris, RoNamespace roNamespace,
			RoIriDictionary roIriDictionary) {
		this.iris = iris;
		this.roNamespace = roNamespace;
		this.roIriDictionary = roIriDictionary;
	}

	private FilterPredicate inNamespace() {
		return FilterPredicate.eq(RoIriDictionary.NAMESPACE, roNamespace.getId());
	}

	@Override
	public Optional<String> getLocalNameFromId(long id) {
		if ((id >>> (BITS_IN_A_LONG - RoNamespaces.BITS_FOR_NAMESPACES)) != roNamespace.getId()) {
			return Optional.empty();
		}
		long local = id & 0xFFFF_FFFFL;
		return iris.first(FilterPredicate.and(inNamespace(), FilterPredicate.eq(RoDictionary.ID, local)),
				c -> c.getColumnReader(RoIriDictionary.IRI_VALUE).getStrings()[0], RoIriDictionary.IRI_VALUE);
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
		String localName = RoIriDictionary.storedLocalName(iri);
		return iris.first(FilterPredicate.and(inNamespace(), FilterPredicate.eq(RoIriDictionary.IRI_VALUE, localName)),
				c -> c.getColumnReader(RoDictionary.ID).getLongs()[0], RoDictionary.ID)
				.map(local -> new RoIri(nameSpacedId(local), roIriDictionary, iri));
	}

	private long nameSpacedId(long id) {
		return id | (roNamespace.getId() << RoNamespaces.BITS_FOR_NAMESPACES);
	}

	@Override
	public Stream<IRI> values() {
		return Stream.empty();
	}
}
