package sib.swiss.swissprot.sparql.ro.quads;

import static sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools.getLongAtIndexInLongBuffers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.rdf4j.model.Statement;
import org.roaringbitmap.RoaringBitmap;

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

    public IriLiteralList(File file, RoIri predicate)
            throws FileNotFoundException, IOException {
        super(file, predicate);
    }

    public IriLiteralList(File file, RoIri predicate,
            Map<RoBnode, RoaringBitmap> bNodeGraphsMap,
            Map<RoIri, RoaringBitmap> iriGraphsMap, RoNamespaces roNamespaces,
            RoIriDictionary iriDictionary, RoLiteralDict literalDictionary) throws IOException {
        super(file, predicate, iriGraphsMap, bNodeGraphsMap, roNamespaces,
                iriDictionary, literalDictionary, null);

    }

    @Override
    public Iterator<Statement> iterator() {
        return new IriLiteralListIterator();
    }

    public static class Builder extends AbstractBuilder {

        public Builder(File file, RoIri predicate,
                RoIriDictionary iriDictionary, RoNamespaces namespaces, RoLiteralDict literalDict)
                throws IOException {
            super(file, predicate, namespaces, iriDictionary, literalDict, null);
        }

        public IriLiteralList build() throws IOException {
            save();
            return new IriLiteralList(file, predicate, bnodeGraphsMap,
                    iriGraphsMap, namespaces, iriDictionary, literalDictionary);
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
            return new RoContextStatement(new RoIri(subjectId, iriDictionary),
                    predicate, new RoSimpleLiteral(objectId, literalDict), graph);

        }
    }
}
