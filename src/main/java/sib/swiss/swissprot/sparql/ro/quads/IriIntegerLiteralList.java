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
import static sib.swiss.swissprot.sparql.ro.quads.RoResourceRoValueList.SUBJECT;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;

import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;

public class IriIntegerLiteralList extends RoResourceRoValueList<RoIri, RoIntegerLiteral> {

    public IriIntegerLiteralList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Configuration configuration)
            throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, configuration);
    }

    public IriIntegerLiteralList(File file, RoIri predicate, RoDictionaries dictionaries, Configuration configuration) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, configuration);
    }

    @Override
    protected RoIri getSubject(VectorizedRowBatch batch, int index) {
        return new RoIri(((LongColumnVector) batch.cols[0]).vector[index], dictionaries.getIriDict());
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
        return new IriIntegerListIterator(getSchema());
    }

    public static class Builder extends AbstractBuilder<RoIri, RoIntegerLiteral> {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries, Configuration configuration)
                throws IOException {
            super(file, predicate, dictionaries, configuration, IriIntegerLiteralList::getSchema);
        }

        public IriIntegerLiteralList build() throws IOException {
            save();
            return new IriIntegerLiteralList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, configuration);
        }

    }

    private class IriIntegerListIterator extends RoResourceValueListIterator {

        private IriIntegerListIterator(TypeDescription schema) {
            super(schema);
        }
    }
}
