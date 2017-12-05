package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.TypeDescription;

import org.eclipse.rdf4j.model.Statement;
import org.roaringbitmap.RoaringBitmap;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;

import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;

public class BnodeIntegerList extends RoResourceRoValueList<RoBnode, RoIntegerLiteral> {

    public BnodeIntegerList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Configuration configuration) throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, configuration);
    }

    public BnodeIntegerList(File file, RoIri predicate, RoDictionaries dictionaries, Configuration configuration) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, configuration);
    }

    @Override
    protected RoBnode getSubject(VectorizedRowBatch batch, int index) {
        return new RoBnode(((LongColumnVector) batch.cols[0]).vector[index]);
    }

    @Override
    protected RoIntegerLiteral getObject(VectorizedRowBatch batch, int index) {
        return new RoIntegerLiteral(((LongColumnVector) batch.cols[1]).vector[index]);
    }

    private static TypeDescription getSchema() {
        return TypeDescription
                .createStruct()
                .addField(SUBJECT, TypeDescription.createLong())
                .addField(OBJECT, TypeDescription.createLong());
    }

    @Override
    public Iterator<Statement> iterator() {
        return new BnodeIntegerListIterator(getSchema());
    }

    public static class Builder extends AbstractBuilder<RoBnode, RoIntegerLiteral> {

        public Builder(File file, RoIri predicate, RoDictionaries dictionaries, Configuration configuration) throws IOException {
            super(file, predicate, dictionaries, configuration, BnodeIntegerList::getSchema);
        }

        public BnodeIntegerList build() throws IOException {
            save();
            return new BnodeIntegerList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, configuration);
        }

    }

    private class BnodeIntegerListIterator
            extends RoResourceValueListIterator {

        private BnodeIntegerListIterator(TypeDescription schema) {
            super(schema);
        }

    }
}
