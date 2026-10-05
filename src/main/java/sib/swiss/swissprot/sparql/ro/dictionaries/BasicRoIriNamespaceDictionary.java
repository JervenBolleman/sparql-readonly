package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import dev.hardwood.reader.ColumnReaders;
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
	/**
	 * Local names read at a time, as many as a row group holds.
	 */
	private static final int BLOCK_SIZE = 4096;

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
		long start = local - local % BLOCK_SIZE;
		String[] block = roIriDictionary.localNameBlock(nameSpacedId(start), key -> read(start));
		int at = (int) (local - start);
		return at < block.length ? Optional.ofNullable(block[at]) : Optional.empty();
	}

	/**
	 * Reads the local names of a block of local ids: IRIs are mostly read
	 * along with others of nearby ids.
	 */
	private String[] read(long start) {
		String[] names = new String[0];
		FilterPredicate inBlock = FilterPredicate.and(inNamespace(), FilterPredicate.gtEq(RoDictionary.ID, start),
				FilterPredicate.lt(RoDictionary.ID, start + BLOCK_SIZE));
		Optional<ColumnReaders> batches = iris.columns(inBlock, BLOCK_SIZE, RoDictionary.ID, RoIriDictionary.IRI_VALUE);
		if (batches.isPresent()) {
			try (ColumnReaders rows = batches.get()) {
				while (rows.nextBatch()) {
					long[] ids = rows.getColumnReader(RoDictionary.ID).getLongs();
					String[] values = rows.getColumnReader(RoIriDictionary.IRI_VALUE).getStrings();
					for (int i = 0; i < ids.length; i++) {
						int at = (int) (ids[i] - start);
						if (at >= names.length) {
							// a namespace with few names has a short block
							names = Arrays.copyOf(names, Math.min(BLOCK_SIZE, Math.max(at + 1, names.length * 2)));
						}
						names[at] = values[i];
					}
				}
			}
		}
		return names;
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
