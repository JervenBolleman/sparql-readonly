package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import dev.hardwood.Hardwood;
import dev.hardwood.reader.RowReader;

import org.roaringbitmap.RoaringBitmap;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import sib.swiss.swissprot.sparql.ro.Term;

import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoSimpleLiteral;

public class BnodeLiteralList extends RoResourceRoValueList<RoBnode, RoLiteral> {

    public BnodeLiteralList(File file, RoIri predicate,
            RoDictionaries dictionaries, Hardwood hardwood)
            throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, hardwood);
    }

    private BnodeLiteralList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
    }

    @Override
    protected RoBnode getSubject(RowReader row) {
        return new RoBnode(row.getLong(SUBJECT));
    }

    @Override
    protected RoLiteral getObject(RowReader row) {
        return new RoSimpleLiteral(row.getLong(OBJECT), dictionaries.getLiteralDict());
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
    protected RoResourceValueListIterator newIterator(RowReader rows) {
        return new BnodeStringListIterator(rows);
    }

    public static class Builder extends AbstractBuilder<RoBnode, RoLiteral> {

        public Builder(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood)
                throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public BnodeLiteralList build() throws IOException {
            save();
            return new BnodeLiteralList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
        }
    }

    private class BnodeStringListIterator
            extends RoResourceValueListIterator {

        private BnodeStringListIterator(RowReader rows) {
            super(rows);
        }

    }
}
