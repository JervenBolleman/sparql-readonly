package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

import dev.hardwood.reader.FilterPredicate;
import org.eclipse.rdf4j.model.IRI;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class BasicRoIriNamespaceDictionary extends RoDictionary<RoIri, IRI>
		implements RoIriNamespaceDictionary {

	private static final int BITS_IN_A_LONG = Long.BYTES * 8;

	private final RoNamespace roNamespace;
	private final RoIriDictionary roIriDictionary;

	public BasicRoIriNamespaceDictionary(ParquetTable table, RoNamespace roNamespace,
			RoIriDictionary roIriDictionary) {
		super(table, RoIriDictionary.IRI_VALUE);
		this.roNamespace = roNamespace;
		this.roIriDictionary = roIriDictionary;
	}

	@Override
	public Optional<String> getLocalNameFromId(long id) throws IOException {
		if ((id >>> (BITS_IN_A_LONG
				- RoNamespaces.BITS_FOR_NAMESPACES)) == roNamespace.getId()) {
			final int idWithoutNamespaceId = (int) id;
			return Optional.of(readStringAt(idWithoutNamespaceId));
		} else {
			return Optional.empty();
		}
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
		String namespace = RoIriDictionary.storedNamespace(iri);
		String localName = RoIriDictionary.storedLocalName(iri);
		if (namespace.equals(roNamespace.getName())) {
			return findId(FilterPredicate.eq(RoIriDictionary.IRI_VALUE, localName))
					.map(id -> new RoIri(nameSpacedId(id), roIriDictionary, iri));
		}
		return Optional.empty();
	}

	private long nameSpacedId(long id) {
		return id | (roNamespace.getId() << RoNamespaces.BITS_FOR_NAMESPACES);
	}

	@Override
	public Stream<IRI> values() {
		// TODO Auto-generated method stub
		return Stream.empty();
	}
}
