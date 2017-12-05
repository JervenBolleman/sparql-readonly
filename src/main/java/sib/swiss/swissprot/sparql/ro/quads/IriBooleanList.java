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
import static sib.swiss.swissprot.sparql.ro.quads.RoResourceRoValueList.SUBJECT;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;

import sib.swiss.swissprot.sparql.ro.values.RoBooleanLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoResource;

public class IriBooleanList extends RoResourceRoValueList<RoIri, RoBooleanLiteral> {

    public IriBooleanList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Configuration configuration) throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, configuration);
    }

    public IriBooleanList(File file, RoIri predicate, RoDictionaries dictionaries, Configuration configuration) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, configuration);
    }

    @Override
    protected RoIri getSubject(VectorizedRowBatch batch, int index) {
        return new RoIri(((LongColumnVector) batch.cols[0]).vector[index], dictionaries.getIriDict());
    }

    @Override
    protected RoBooleanLiteral getObject(VectorizedRowBatch batch, int index) {
        return new RoBooleanLiteral(((LongColumnVector) batch.cols[1]).vector[index] == 1L);
    }

    private static TypeDescription getSchema() {
        return TypeDescription
                .createStruct()
                .addField(SUBJECT, TypeDescription.createLong())
                .addField(OBJECT, TypeDescription.createLong());
    }

    @Override
    public Iterator<Statement> iterator() {
        return new IriBooleanListIterator(getSchema());
    }

    public static class Builder extends AbstractBuilder<RoIri, RoBooleanLiteral> {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries, Configuration configuration)
                throws IOException {
            super(file, predicate, dictionaries, configuration, IriBooleanList::getSchema);
        }

        public IriBooleanList build() throws IOException {
            save();
            return new IriBooleanList(file, predicate, iriGraphsMap, bnodeGraphsMap,
                    dictionaries, configuration);
        }

    }

    private class IriBooleanListIterator extends RoResourceValueListIterator {

        private IriBooleanListIterator(TypeDescription schema) {
            super(schema);
        }
    }
}
