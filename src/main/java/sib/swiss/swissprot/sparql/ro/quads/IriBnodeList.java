package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import dev.hardwood.Hardwood;
import dev.hardwood.reader.ColumnReaders;

import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import sib.swiss.swissprot.sparql.ro.Term;

import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class IriBnodeList extends RoResourceRoValueList<RoIri, RoBnode> {


    public IriBnodeList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, dictionaries, hardwood);
    }

    @Override
    protected RoIri getSubject(long id) {
        return new RoIri(id, dictionaries.getIriDict());
    }

    @Override
    protected RoBnode getObject(long id) {
        return new RoBnode(id);
    }

    public static class Builder extends AbstractBuilder<RoIri, RoBnode> {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries, Hardwood hardwood)
                throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public IriBnodeList build() throws IOException {
            save();
            return new IriBnodeList(file, predicate, dictionaries, hardwood);
        }

    }

    private class IriBnodeListIterator extends RoResourceValueListIterator {

        private IriBnodeListIterator(ColumnReaders rows) {
            super(rows);
        }
    }

    @Override
    public Term.Kind subjectKind() {
        return Term.Kind.IRI;
    }

    @Override
    public Term.Kind objectKind() {
        return Term.Kind.BNODE;
    }

    @Override
    protected RoResourceValueListIterator newIterator(ColumnReaders rows) {
        return new IriBnodeListIterator(rows);
    }

}
