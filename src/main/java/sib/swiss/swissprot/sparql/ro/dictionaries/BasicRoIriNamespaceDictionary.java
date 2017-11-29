package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.hadoop.hive.ql.io.sarg.PredicateLeaf;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgument;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgumentFactory;
import org.apache.orc.Reader;
import org.apache.orc.RecordReader;

import org.eclipse.rdf4j.model.IRI;

import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class BasicRoIriNamespaceDictionary extends RoDictionary<RoIri, IRI>
        implements RoIriNamespaceDictionary {

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
        if (nameSpacedId(id) == (long) roNamespace.getId()) {
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
    public int getNamespaceId() {
        return (int) roNamespace.getId();
    }

    @Override
    public Optional<RoIri> find(IRI predicate) {
        if (predicate.getNamespace().equals(roNamespace.getName())) {
            try {
                final Reader.Options options = new Reader.Options();
                SearchArgument equals = SearchArgumentFactory.newBuilder().equals(LOCAL_NAME_COLUMN, PredicateLeaf.Type.STRING, predicate.getLocalName()).build();
                options.searchArgument(equals, new String[]{LOCAL_NAME_COLUMN});
                RecordReader rows = reader.rows(options);
                VectorizedRowBatch batch = schema.createRowBatch();
                final long id = rows.getRowNumber();
                boolean nextBatch = rows.nextBatch(batch);
                while (nextBatch) {
                    for (int i = 0; i < batch.size; i++) {
                        BytesColumnVector bcv = (BytesColumnVector) batch.cols[0];
                        if (bcv.toString(i).equals(predicate.getLocalName())) {
                            final RoIri roIri = new RoIri((id + i)|getNamespaceId(), roIriDictionary);
                            return Optional.of(roIri);
                        }
                    }
                }
                return Optional.empty();

            } catch (IOException ex) {
                Logger.getLogger(BasicRoIriNamespaceDictionary.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return Optional.empty();
    }

    private long nameSpacedId(long id) {
        return id >>> 32;
    }

    @Override
    public Stream<IRI> values() {
        // TODO Auto-generated method stub
        return Stream.empty();
    }
}
