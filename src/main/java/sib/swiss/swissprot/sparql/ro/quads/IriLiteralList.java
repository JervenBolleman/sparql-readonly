package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import dev.hardwood.Hardwood;
import dev.hardwood.reader.ColumnReaders;

import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import sib.swiss.swissprot.sparql.ro.Term;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;

import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoSimpleLiteral;

public class IriLiteralList extends RoResourceRoValueList<RoIri, RoLiteral> {


    public IriLiteralList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, dictionaries, hardwood);
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
    protected RoResourceValueListIterator newIterator(ColumnReaders rows) {
        return new IriLiteralListIterator(rows);
    }

    @Override
    protected RoIri getSubject(long id) {
        return new RoIri(id, dictionaries.getIriDict());
    }

    @Override
    protected RoLiteral getObject(long id) {
        return new RoSimpleLiteral(id, dictionaries.getLiteralDict());
    }

    public static class Builder extends AbstractBuilder<RoIri, RoLiteral> {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries, Hardwood hardwood)
                throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public IriLiteralList build() throws IOException {
            save();
            return new IriLiteralList(file, predicate, dictionaries, hardwood);
        }

    }

    private class IriLiteralListIterator extends RoResourceValueListIterator {

        IriLiteralListIterator(ColumnReaders rows) {
            super(rows);
        }
    }
}
