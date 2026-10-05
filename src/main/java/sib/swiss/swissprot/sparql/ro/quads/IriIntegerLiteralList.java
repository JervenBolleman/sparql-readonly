package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import dev.hardwood.Hardwood;
import dev.hardwood.reader.ColumnReaders;

import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import sib.swiss.swissprot.sparql.ro.Term;
import static sib.swiss.swissprot.sparql.ro.quads.RoResourceRoValueList.SUBJECT;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;

import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class IriIntegerLiteralList extends RoResourceRoValueList<RoIri, RoIntegerLiteral> {


    public IriIntegerLiteralList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, dictionaries, hardwood);
    }

    @Override
    protected RoIri getSubject(long id) {
        return new RoIri(id, dictionaries.getIriDict());
    }

    @Override
    protected RoIntegerLiteral getObject(long id) {
        return new RoIntegerLiteral(id);
    }

    @Override
    public Term.Kind subjectKind() {
        return Term.Kind.IRI;
    }

    @Override
    public Term.Kind objectKind() {
        return Term.Kind.INT;
    }

    @Override
    protected RoResourceValueListIterator newIterator(ColumnReaders rows) {
        return new IriIntegerListIterator(rows);
    }

    public static class Builder extends AbstractBuilder<RoIri, RoIntegerLiteral> {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries, Hardwood hardwood)
                throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public IriIntegerLiteralList build() throws IOException {
            save();
            return new IriIntegerLiteralList(file, predicate, dictionaries, hardwood);
        }

    }

    private class IriIntegerListIterator extends RoResourceValueListIterator {

        private IriIntegerListIterator(ColumnReaders rows) {
            super(rows);
        }
    }
}
