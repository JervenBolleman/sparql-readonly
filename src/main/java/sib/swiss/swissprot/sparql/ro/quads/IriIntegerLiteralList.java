package sib.swiss.swissprot.sparql.ro.quads;

import static sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools.getLongAtIndexInLongBuffers;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.eclipse.rdf4j.model.Statement;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;

import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;

public class IriIntegerLiteralList extends RoResourceRoValueList {

    public IriIntegerLiteralList(File file, RoIri predicate,
            RoDictionaries dictionaries) throws IOException {
        super(file, predicate, dictionaries);
    }

    @Override
    public Iterator<Statement> iterator() {
        return new IriIntegerListIterator();
    }

    public static class Builder extends AbstractBuilder {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries)
                throws IOException {
            super(file, predicate, dictionaries);
        }

        public IriIntegerLiteralList build() throws IOException {
            save();
            return new IriIntegerLiteralList(file, predicate, dictionaries);
        }
    }

    private class IriIntegerListIterator implements Iterator<Statement> {

        private int at = 0;

        @Override
        public boolean hasNext() {
            return at < (numberOfTriplesInList * 2);
        }

        @Override
        public Statement next() {
            final RoResource graph = findGraphForTriple(at);
            long subjectId = getLongAtIndexInLongBuffers(at++, triples);
            long objectId = getLongAtIndexInLongBuffers(at++, triples);
            return new RoContextStatement(new RoIri(subjectId, dictionaries.getIriDict()),
                    predicate, new RoIntegerLiteral(objectId), graph);

        }
    }
}
