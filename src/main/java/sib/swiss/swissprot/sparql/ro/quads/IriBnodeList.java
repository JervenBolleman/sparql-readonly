package sib.swiss.swissprot.sparql.ro.quads;

import static sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools.getLongAtIndexInLongBuffers;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.eclipse.rdf4j.model.Statement;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;

import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;

public class IriBnodeList extends RoResourceRoValueList {

    public IriBnodeList(File file, RoIri predicate,
            RoDictionaries dictionaries) throws IOException {
        super(file, predicate, dictionaries);
    }

    public static class Builder extends AbstractBuilder {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries)
                throws IOException {
            super(file, predicate, dictionaries);
        }

        public IriBnodeList build() throws IOException {
            save();
            return new IriBnodeList(file, predicate, dictionaries);
        }
    }

    private class IriBnodeListIterator implements Iterator<Statement> {

        private int at = 0;

        @Override
        public boolean hasNext() {
            return at < numberOfTriplesInList;
        }

        @Override
        public Statement next() {
            final RoResource graph = findGraphForTriple(at);
            long subjectId = getLongAtIndexInLongBuffers(at, triples);
            long objectId = getLongAtIndexInLongBuffers(at++, triples);
            at++;
            return new RoContextStatement(new RoIri(subjectId, dictionaries.getIriDict()),
                    predicate, new RoBnode(objectId), graph);

        }

    }

    @Override
    public Iterator<Statement> iterator() {
        return new IriBnodeListIterator();
    }

}
