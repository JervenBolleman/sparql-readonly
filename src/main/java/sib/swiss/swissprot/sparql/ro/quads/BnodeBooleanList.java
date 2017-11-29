package sib.swiss.swissprot.sparql.ro.quads;

import static sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools.getLongAtIndexInLongBuffers;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.Statement;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;

import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;

public class BnodeBooleanList extends RoResourceRoValueList
        implements Iterable<Statement> {

    public BnodeBooleanList(File file, RoIri predicate,
            RoDictionaries dictionaries) throws IOException {
        super(file, predicate, dictionaries);

    }

    @Override
    public Iterator<Statement> iterator() {
        return new BnodeBooleanListIterator();
    }

    public static class Builder extends AbstractBuilder {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries)
                throws IOException {
            super(file, predicate, dictionaries);
        }

        public BnodeBooleanList build() throws IOException {
            save();
            return new BnodeBooleanList(file, predicate, dictionaries);
        }
    }

    private class BnodeBooleanListIterator implements Iterator<Statement> {

        private int at = 0;

        @Override
        public boolean hasNext() {
            return at < numberOfTriplesInList;
        }

        @Override
        public Statement next() {
            final RoResource graph = findGraphForTriple(at);
            long subjectId = getLongAtIndexInLongBuffers(at++, triples); // increment
            // after
            // use
            long objectId = getLongAtIndexInLongBuffers(at++, triples);

            if (graph instanceof BNode) {
                return new RoContextStatement(
                        new RoBnode(subjectId), predicate,
                        new RoIri(objectId, dictionaries.getIriDict()), graph);
            } else {
                return new OnlyRoIriContextStatement(subjectId,
                        predicate.getLongId(), objectId, graph.getLongId(),
                        dictionaries.getIriDict());
            }
        }

    }

}
