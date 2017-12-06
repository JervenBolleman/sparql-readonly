package sib.swiss.swissprot.sparql.ro.quads;

import static sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools.getLongAtIndexInLongBuffers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.TypeDescription;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.Statement;
import org.roaringbitmap.RoaringBitmap;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;

import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoBooleanLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoResource;

public class BnodeBooleanList extends RoResourceRoValueList<RoBnode, RoBooleanLiteral> {

    public BnodeBooleanList(File file, RoIri predicate, RoDictionaries dictionaries, Configuration configuration) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, configuration);
    }

    public BnodeBooleanList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Configuration configuration) throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, configuration);

    }

    @Override
    protected RoBnode getSubject(VectorizedRowBatch batch, int index) {
        return new RoBnode(((LongColumnVector) batch.cols[SUBECT_COL]).vector[index]);
    }

    @Override
    protected RoBooleanLiteral getObject(VectorizedRowBatch batch, int index) {
        return new RoBooleanLiteral(((LongColumnVector) batch.cols[OBJECT_COL]).vector[index] == 1L);
    }

    private static TypeDescription getSchema() {
        return TypeDescription
                .createStruct()
                .addField(SUBJECT, TypeDescription.createLong())
                .addField(OBJECT, TypeDescription.createBoolean());
    }

    @Override
    public Iterator<Statement> iterator() {
        if (triples.getNumberOfRows() == 0) {
            Collections.emptyIterator();
        }
        return new BnodeBooleanListIterator(getSchema());
    }

    public static class Builder extends AbstractBuilder<RoBnode, RoBooleanLiteral> {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries, Configuration configuration)
                throws IOException {
            super(file, predicate, dictionaries, configuration, BnodeBooleanList::getSchema);
        }

        public BnodeBooleanList build() throws IOException {
            save();
            return new BnodeBooleanList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, configuration);
        }

    }

    private class BnodeBooleanListIterator extends RoResourceValueListIterator {

        private BnodeBooleanListIterator(TypeDescription schema) {
            super(schema);
        }

        @Override
        public Statement statement(RoBnode subject, RoIri predicate, RoBooleanLiteral object, RoResource graph) {
            if (graph == null || graph instanceof BNode) {
                return new RoContextStatement(
                        subject, predicate,
                        object, graph);
            } else {
                return new OnlyRoIriContextStatement(subject.getLongId(),
                        predicate.getLongId(),
                        object.getLongId(),
                        graph.getLongId(),
                        dictionaries.getIriDict()
                );
            }
        }
    }

}
