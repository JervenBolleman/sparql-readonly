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

public class IriLiteralList extends RoResourceRoValueList<RoIri, RoLiteral> {

    public IriLiteralList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
    }

    public IriLiteralList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, hardwood);
    }

    @Override
    public Term.Kind subjectKind() {
        return Term.Kind.IRI;
    }

    @Override
    public Term.Kind objectKind() {
        return Term.Kind.LITERAL;
    }

    @Override
    protected RoResourceValueListIterator newIterator(RowReader rows) {
        return new IriLiteralListIterator(rows);
    }

    @Override
    protected RoIri getSubject(RowReader row) {
        return new RoIri(row.getLong(SUBJECT), dictionaries.getIriDict());
    }

    @Override
    protected RoLiteral getObject(RowReader row) {
        return new RoSimpleLiteral(row.getLong(OBJECT), dictionaries.getLiteralDict());
    }

    public static class Builder extends AbstractBuilder<RoIri, RoLiteral> {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries, Hardwood hardwood)
                throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public IriLiteralList build() throws IOException {
            save();
            return new IriLiteralList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
        }

    }

    private class IriLiteralListIterator extends RoResourceValueListIterator {

        IriLiteralListIterator(RowReader rows) {
            super(rows);
        }
    }
}
