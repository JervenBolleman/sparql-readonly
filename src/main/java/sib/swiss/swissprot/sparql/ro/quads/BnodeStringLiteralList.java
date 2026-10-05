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
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoStringLiteral;

public class BnodeStringLiteralList extends RoResourceRoValueList<RoBnode, RoStringLiteral> {

    public BnodeStringLiteralList(File file, RoIri predicate,
            RoDictionaries dictionaries, Hardwood hardwood)
            throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, hardwood);
    }

    private BnodeStringLiteralList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
    }

    @Override
    protected RoBnode getSubject(long id) {
        return new RoBnode(id);
    }

    @Override
    protected RoStringLiteral getObject(long id) {
        return new RoStringLiteral(id, dictionaries.getLiteralDict());
    }

    @Override
    public Term.Kind subjectKind() {
        return Term.Kind.BNODE;
    }

    @Override
    public Term.Kind objectKind() {
        return Term.Kind.LITERAL;
    }

    @Override
    protected RoResourceValueListIterator newIterator(ColumnReaders rows) {
        return new BnodeStringListIterator(rows);
    }

    public static class Builder extends AbstractBuilder<RoBnode, RoStringLiteral> {

        public Builder(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood)
                throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public BnodeStringLiteralList build() throws IOException {
            save();
            return new BnodeStringLiteralList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
        }

    }

    private class BnodeStringListIterator
            extends RoResourceValueListIterator {

        private BnodeStringListIterator(ColumnReaders rows) {
            super(rows);
        }

    }
}
