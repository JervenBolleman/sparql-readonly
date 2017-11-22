package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.eclipse.rdf4j.model.Statement;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;

import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class BnodeIntegerList extends RoResourceRoValueList {
    
    public BnodeIntegerList(File file, RoIri predicate,
            RoDictionaries dictionaries) throws IOException {
        super(file, predicate, dictionaries);
    }

    @Override
    public Iterator<Statement> iterator() {
        return new BnodeIntegerListIterator();
    }

    public static class Builder extends AbstractBuilder {

        public Builder(File file, RoIri predicate, RoDictionaries dictionaries) throws IOException {
            super(file, predicate, dictionaries);
        }

        public BnodeIntegerList build() throws IOException {
            save();
            return new BnodeIntegerList(file, predicate, dictionaries);
        }
    }

    private class BnodeIntegerListIterator
            extends ResourceValueListIterator<RoBnode, RoIntegerLiteral> {

        @Override
        protected RoBnode getSubjectFromLong(long id) {
            return new RoBnode(id);
        }

        @Override
        protected RoIntegerLiteral getObjectFromLong(long id) {
            return new RoIntegerLiteral(id);
        }
    }

}
