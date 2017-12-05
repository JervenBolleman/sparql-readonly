package sib.swiss.swissprot.sparql.ro.quads;

import static sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools.getLongAtIndexInLongBuffers;

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
import sib.swiss.swissprot.sparql.ro.values.RoResource;

public class BnodeIriList extends RoResourceRoValueList<RoBnode, RoIri> {

    public BnodeIriList(File file, RoIri predicate, RoDictionaries dictionaries, Configuration configuration) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, configuration);
    }

    public BnodeIriList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Configuration configuration) throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, configuration);
    }

    @Override
    protected RoBnode getSubject(VectorizedRowBatch batch, int index) {
        return new RoBnode(((LongColumnVector) batch.cols[0]).vector[index]);
    }

    @Override
    protected RoIri getObject(VectorizedRowBatch batch, int index) {
        return new RoIri(((LongColumnVector) batch.cols[1]).vector[index], dictionaries.getIriDict());
    }

    private static TypeDescription getSchema() {
        return TypeDescription
                .createStruct()
                .addField(SUBJECT, TypeDescription.createLong())
                .addField(OBJECT, TypeDescription.createLong());
    }

    public static class Builder extends AbstractBuilder<RoBnode, RoIri> {

        public Builder(File file, RoIri predicate, RoDictionaries dictionaries, Configuration configuration) throws IOException {
            super(file, predicate, dictionaries, configuration, BnodeIriList::getSchema);
        }

        public BnodeIriList build() throws IOException {
            save();
            return new BnodeIriList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, configuration);
        }

    }

    private class BnodeIriListIterator extends RoResourceValueListIterator {

        private BnodeIriListIterator(TypeDescription schema) {
            super(schema);
        }
    }

    @Override
    public Iterator<Statement> iterator() {
        return new BnodeIriListIterator(getSchema());
    }

}
