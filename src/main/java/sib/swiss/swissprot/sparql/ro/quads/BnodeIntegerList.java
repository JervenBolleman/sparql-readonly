package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import dev.hardwood.Hardwood;
import dev.hardwood.reader.ColumnReaders;

import org.roaringbitmap.RoaringBitmap;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import sib.swiss.swissprot.sparql.ro.Term;

import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;

public class BnodeIntegerList extends RoResourceRoValueList<RoBnode, RoIntegerLiteral> {

    public BnodeIntegerList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
    }

    public BnodeIntegerList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, hardwood);
    }

    @Override
    protected RoBnode getSubject(long id) {
        return new RoBnode(id);
    }

    @Override
    protected RoIntegerLiteral getObject(long id) {
        return new RoIntegerLiteral(id);
    }

    @Override
    public Term.Kind subjectKind() {
        return Term.Kind.BNODE;
    }

    @Override
    public Term.Kind objectKind() {
        return Term.Kind.INT;
    }

    @Override
    protected RoResourceValueListIterator newIterator(ColumnReaders rows) {
        return new BnodeIntegerListIterator(rows);
    }

    public static class Builder extends AbstractBuilder<RoBnode, RoIntegerLiteral> {

        public Builder(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public BnodeIntegerList build() throws IOException {
            save();
            return new BnodeIntegerList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
        }

    }

    private class BnodeIntegerListIterator
            extends RoResourceValueListIterator {

        private BnodeIntegerListIterator(ColumnReaders rows) {
            super(rows);
        }

    }
}
