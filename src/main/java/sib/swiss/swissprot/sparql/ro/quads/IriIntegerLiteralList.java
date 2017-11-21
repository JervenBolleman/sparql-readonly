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
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;

public class IriIntegerLiteralList extends RoResourceRoValueList {

    public IriIntegerLiteralList(File file, RoIri predicate)
            throws FileNotFoundException, IOException {
        super(file, predicate);
    }

    public IriIntegerLiteralList(File file, RoIri predicate,
            Map<RoBnode, RoaringBitmap> bNodeGraphsMap,
            Map<RoIri, RoaringBitmap> iriGraphsMap, RoNamespaces roNamespaces,
            RoIriDictionary iriDictionary) throws IOException {
        super(file, predicate, iriGraphsMap, bNodeGraphsMap, roNamespaces,
                iriDictionary, null, null);
    }

    @Override
    public Iterator<Statement> iterator() {
        return new IriIntegerListIterator();
    }

    public static class Builder extends AbstractBuilder {

        public Builder(File file, RoIri predicate,
                RoIriDictionary iriDictionary, RoNamespaces namespaces)
                throws IOException {
            super(file, predicate, namespaces, iriDictionary, null, null);
        }

        public IriIntegerLiteralList build() throws IOException {
            save();
            return new IriIntegerLiteralList(file, predicate, bnodeGraphsMap,
                    iriGraphsMap, namespaces, iriDictionary);
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
            return new RoContextStatement(new RoIri(subjectId, iriDictionary),
                    predicate, new RoIntegerLiteral(objectId), graph);

        }
    }
}
