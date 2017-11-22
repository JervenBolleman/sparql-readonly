package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.eclipse.rdf4j.model.Statement;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;

import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;

public class BnodeStringLiteralList extends RoResourceRoValueList {


    public BnodeStringLiteralList(File file, RoIri predicate,
            RoDictionaries dictionaries)
            throws IOException {
        super(file, predicate, dictionaries);
    }

    @Override
    public Iterator<Statement> iterator() {
        return new BnodeStringListIterator();
    }

    public static class Builder extends AbstractBuilder {

        public Builder(File file, RoIri predicate, RoDictionaries dictionaries)
                throws IOException {
            super(file, predicate, dictionaries);
        }

        public BnodeStringLiteralList build() throws IOException {
            save();
            return new BnodeStringLiteralList(file, predicate, dictionaries);
        }
    }

    private class BnodeStringListIterator
            extends ResourceValueListIterator<RoBnode, RoLiteral> {

        @Override
        protected RoBnode getSubjectFromLong(long id) {
            return new RoBnode(id, dictionaries.getBnodeDict());
        }

        @Override
        protected RoLiteral getObjectFromLong(long id) {
            return dictionaries.getLiteralDict().get(id);
        }
    }
}
