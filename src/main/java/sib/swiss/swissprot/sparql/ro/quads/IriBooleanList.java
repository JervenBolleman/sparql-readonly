package sib.swiss.swissprot.sparql.ro.quads;

import static sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools.getLongAtIndexInLongBuffers;

import java.io.File;
import java.io.IOException;
import dev.hardwood.Hardwood;
import dev.hardwood.reader.ColumnReaders;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import sib.swiss.swissprot.sparql.ro.Term;
import static sib.swiss.swissprot.sparql.ro.quads.RoResourceRoValueList.SUBJECT;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;

import sib.swiss.swissprot.sparql.ro.values.RoBooleanLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoResource;

public class IriBooleanList extends RoResourceRoValueList<RoIri, RoBooleanLiteral> {


    public IriBooleanList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, dictionaries, hardwood);
    }

    @Override
    protected RoIri getSubject(long id) {
        return new RoIri(id, dictionaries.getIriDict());
    }

    @Override
    protected RoBooleanLiteral getObject(long id) {
        return new RoBooleanLiteral(id == 1L);
    }

    @Override
    public Term.Kind subjectKind() {
        return Term.Kind.IRI;
    }

    @Override
    public Term.Kind objectKind() {
        return Term.Kind.BOOLEAN;
    }

    @Override
    protected RoResourceValueListIterator newIterator(ColumnReaders rows) {
        return new IriBooleanListIterator(rows);
    }

    public static class Builder extends AbstractBuilder<RoIri, RoBooleanLiteral> {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries, Hardwood hardwood)
                throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public IriBooleanList build() throws IOException {
            save();
            return new IriBooleanList(file, predicate, dictionaries, hardwood);
        }

    }

    private class IriBooleanListIterator extends RoResourceValueListIterator {

        private IriBooleanListIterator(ColumnReaders rows) {
            super(rows);
        }
    }
}
