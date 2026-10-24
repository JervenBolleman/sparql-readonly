package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import dev.hardwood.Hardwood;
import dev.hardwood.reader.ColumnReaders;

import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import sib.swiss.swissprot.sparql.ro.Term;

import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class BnodeBnodeList extends RoResourceRoValueList<RoBnode, RoBnode> {

    public BnodeBnodeList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, dictionaries, hardwood);
    }


    @Override
    protected RoBnode getSubject(long id) {
        return new RoBnode(id);
    }

    @Override
    protected RoBnode getObject(long id) {
        return new RoBnode(id);
    }

    public static class Builder extends AbstractBuilder<RoBnode, RoBnode> {

        public Builder(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public BnodeBnodeList build() throws IOException {
            save();
            return new BnodeBnodeList(file, predicate, dictionaries, hardwood);
        }
    }

    private class BnodeBnodeListIterator extends RoResourceValueListIterator {

        private BnodeBnodeListIterator(ColumnReaders rows) {
            super(rows);
        }

    }

    @Override
    public Term.Kind subjectKind() {
        return Term.Kind.BNODE;
    }

    @Override
    public Term.Kind objectKind() {
        return Term.Kind.BNODE;
    }

    @Override
    protected RoResourceValueListIterator newIterator(ColumnReaders rows) {
        return new BnodeBnodeListIterator(rows);
    }

}
