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
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class BnodeBnodeList extends RoResourceRoValueList<RoBnode, RoBnode> {

    public BnodeBnodeList(File file, RoIri predicate, RoDictionaries dictionaries, Configuration configuration) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, configuration);
    }

    public BnodeBnodeList(File file, RoIri predicate,
            Map<RoIri, RoaringBitmap> iriGraphsMap,
            Map<RoBnode, RoaringBitmap> bNodeGraphsMap,
            RoDictionaries dictionaries, Configuration configuration) throws IOException {
        super(file, predicate, iriGraphsMap, bNodeGraphsMap, dictionaries, configuration);
    }

    @Override
    protected RoBnode getSubject(VectorizedRowBatch batch, int index) {
        return new RoBnode(((LongColumnVector) batch.cols[0]).vector[index]);
    }

    @Override
    protected RoBnode getObject(VectorizedRowBatch batch, int index) {
        return new RoBnode(((LongColumnVector) batch.cols[1]).vector[index]);
    }

    private static TypeDescription getSchema() {
        return TypeDescription
                .createStruct()
                .addField(SUBJECT, TypeDescription.createLong())
                .addField(OBJECT, TypeDescription.createLong());
    }

    public static class Builder extends AbstractBuilder<RoBnode, RoBnode> {

        public Builder(File file, RoIri predicate, RoDictionaries dictionaries, Configuration configuration) throws IOException {
            super(file, predicate, dictionaries, configuration, BnodeBnodeList::getSchema);
        }

        public BnodeBnodeList build() throws IOException {
            save();
            return new BnodeBnodeList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, configuration);
        }
    }

    private class BnodeBnodeListIterator extends RoResourceValueListIterator {

        private BnodeBnodeListIterator(TypeDescription schema) {
            super(schema);
        }

    }

    @Override
    public Iterator<Statement> iterator() {
        return new BnodeBnodeListIterator(getSchema());
    }

}
