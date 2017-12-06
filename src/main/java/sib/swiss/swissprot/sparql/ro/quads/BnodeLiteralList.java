package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
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
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoStringLiteral;

public class BnodeLiteralList extends RoResourceRoValueList<RoBnode, RoLiteral> {

    public BnodeLiteralList(File file, RoIri predicate,
            RoDictionaries dictionaries, Configuration configuration)
            throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, configuration);
    }

    private BnodeLiteralList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Configuration configuration) throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, configuration);
    }

    @Override
    protected RoBnode getSubject(VectorizedRowBatch batch, int index) {
        return new RoBnode(((LongColumnVector) batch.cols[SUBECT_COL]).vector[index]);
    }

    @Override
    protected RoStringLiteral getObject(VectorizedRowBatch batch, int index) {
        return new RoStringLiteral(((LongColumnVector) batch.cols[OBJECT_COL]).vector[index], dictionaries.getLiteralDict());
    }

    private static TypeDescription getSchema() {
        return TypeDescription
                .createStruct()
                .addField(SUBJECT, TypeDescription.createLong())
                .addField(OBJECT, TypeDescription.createLong());
    }

    @Override
    public Iterator<Statement> iterator() {
        if (triples.getNumberOfRows() == 0) {
            Collections.emptyIterator();
        }
        return new BnodeStringListIterator(getSchema());
    }

    public static class Builder extends AbstractBuilder<RoBnode, RoLiteral> {

        public Builder(File file, RoIri predicate, RoDictionaries dictionaries, Configuration configuration)
                throws IOException {
            super(file, predicate, dictionaries, configuration, BnodeLiteralList::getSchema);
        }

        public BnodeLiteralList build() throws IOException {
            save();
            return new BnodeLiteralList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, configuration);
        }
    }

    private class BnodeStringListIterator
            extends RoResourceValueListIterator {

        private BnodeStringListIterator(TypeDescription schema) {
            super(schema);
        }

    }
}
