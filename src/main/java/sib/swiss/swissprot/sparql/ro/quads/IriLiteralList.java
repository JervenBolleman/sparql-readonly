package sib.swiss.swissprot.sparql.ro.quads;

import static sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools.getLongAtIndexInLongBuffers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.rdf4j.model.Statement;
import org.roaringbitmap.RoaringBitmap;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;

import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoResource;
import sib.swiss.swissprot.sparql.ro.values.RoSimpleLiteral;

public class IriLiteralList extends RoResourceRoValueList {

    public IriLiteralList(File file, RoIri predicate,
            RoDictionaries dictionaries) throws IOException {
        super(file, predicate, dictionaries);
    }

    @Override
    public Iterator<Statement> iterator() {
        return new IriLiteralListIterator();
    }

    public static class Builder extends AbstractBuilder {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries)
                throws IOException {
            super(file, predicate, dictionaries);
        }

        public IriLiteralList build() throws IOException {
            save();
            return new IriLiteralList(file, predicate, dictionaries);
        }
    }

    private class IriLiteralListIterator implements Iterator<Statement> {

        private int at = 0;

        @Override
        public boolean hasNext() {
            return at < numberOfTriplesInList;
        }

        @Override
        public Statement next() {
            final RoResource graph = findGraphForTriple(at);
            long subjectId = getLongAtIndexInLongBuffers(at++, triples);
            long objectId = getLongAtIndexInLongBuffers(at++, triples);
            return new RoContextStatement(new RoIri(subjectId, dictionaries.getIriDict()),
                    predicate, new RoSimpleLiteral(objectId, dictionaries.getLiteralDict()), graph);

        }
    }
}
