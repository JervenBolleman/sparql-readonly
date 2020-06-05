package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.hadoop.hive.ql.io.sarg.PredicateLeaf;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgument;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgumentFactory;
import org.apache.orc.Reader;
import org.apache.orc.RecordReader;
import org.apache.orc.StringColumnStatistics;
import org.eclipse.rdf4j.model.IRI;

import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class BasicRoIriNamespaceDictionary extends RoDictionary<RoIri, IRI>
		implements RoIriNamespaceDictionary {

	private static final int BITS_IN_A_LONG = Long.BYTES * 8;
	public static final String LOCAL_NAME_COLUMN = "local_name_value";

	private final RoNamespace roNamespace;
	private final RoIriDictionary roIriDictionary;

	public BasicRoIriNamespaceDictionary(Reader reader, RoNamespace roNamespace,
			RoIriDictionary roIriDictionary) {
		super(reader);
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
		String namespace = iri.getNamespace();
		String localName = iri.getLocalName();
		if (iri.getLocalName().isEmpty()) {
			namespace = namespace.substring(0,
					namespace.lastIndexOf("/", namespace.length() - 2) + 1);
			localName = iri.getNamespace().substring(namespace.length());
		}
		if (namespace.equals(roNamespace.getName())) {
			try {
				// There is a column of colunms statistics
				StringColumnStatistics statistics = (StringColumnStatistics) reader
						.getStatistics()[RoDictionary.VALUE_COL + 1];
				String searchLocal = iri.getLocalName();
				if (statistics.getMinimum().compareTo(searchLocal) > 0
						&& (statistics.getMaximum()
								.compareTo(searchLocal) < 0)) {
					return Optional.empty();
				}
				Reader.Options options = new Reader.Options();

				SearchArgument equals = SearchArgumentFactory.newBuilder()
						.equals(LOCAL_NAME_COLUMN, PredicateLeaf.Type.STRING,
								localName)
						.build();
				options = options
						.searchArgument(equals,
								new String[] { null, LOCAL_NAME_COLUMN,
										RoDictionary.ID })
						.include(new boolean[] { true, true, true });
				return searchInRows(options, localName);

			} catch (IOException ex) {
				Logger.getLogger(BasicRoIriNamespaceDictionary.class.getName())
						.log(Level.SEVERE, null, ex);
			}
		}
		return Optional.empty();
	}

	private Optional<RoIri> searchInRows(final Reader.Options options,
			String localName) throws IOException {
		try (RecordReader rows = reader.rows(options)) {
			VectorizedRowBatch batch = schema.createRowBatch();

			boolean nextBatch = rows.nextBatch(batch);
			while (nextBatch) {

				final LongColumnVector name = (LongColumnVector) batch.cols[RoDictionary.ID_COL];
				final BytesColumnVector bcv = (BytesColumnVector) batch.cols[RoDictionary.VALUE_COL];

				for (int i = 0; i < batch.size; i++) {
					String local = new String(bcv.vector[i], bcv.start[i],
							bcv.length[i]);
					if (local.equals(localName)) {
						long id = name.vector[i];
						final RoIri roIri = new RoIri(nameSpacedId(id),
								roIriDictionary);
						return Optional.of(roIri);
					}
				}
				nextBatch = rows.nextBatch(batch);
			}
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
