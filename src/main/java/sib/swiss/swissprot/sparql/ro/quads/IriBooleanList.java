package sib.swiss.swissprot.sparql.ro.quads;

import static sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools.getLongAtIndexInLongBuffers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.Statement;
import org.roaringbitmap.RoaringBitmap;

import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;

public class IriBooleanList extends RoResourceRoValueList
        implements Iterable<Statement> {

    public IriBooleanList(File file, RoIri predicate,
            Map<RoBnode, RoaringBitmap> bNodeGraphsMap,
            Map<RoIri, RoaringBitmap> iriGraphsMap, RoNamespaces roNamespaces,
            RoIriDictionary iriDictionary) throws IOException {
        super(file, predicate, iriGraphsMap, bNodeGraphsMap, roNamespaces,
                iriDictionary, null);

    }

    public IriBooleanList(File file, RoIri predicate)
            throws FileNotFoundException, IOException {
        super(file, predicate);
    }

    @Override
    public Iterator<Statement> iterator() {
        return new IriIriListIterator();
    }

    public static class Builder extends AbstractBuilder {

        public Builder(File file, RoIri predicate,
                RoIriDictionary iriDictionary, RoNamespaces namespaces)
                throws IOException {
            super(file, predicate, namespaces, iriDictionary, null);
        }

        public IriBooleanList build() throws IOException {
            das.close();
            saveContextBitmaps();
            saveNamespace(namespaces, file);
            return new IriBooleanList(file, predicate, bNodeGraphsMap, iriGraphsMap,
                    namespaces, iriDictionary);
        }
    }

    private class IriIriListIterator implements Iterator<Statement> {

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
                        new RoIri(subjectId, iriDictionary), predicate,
                        new RoIri(objectId, iriDictionary), graph);
            } else {
                return new OnlyRoIriContextStatement(subjectId,
                        predicate.getLongId(), objectId, graph.getLongId(),
                        iriDictionary);
            }
        }

    }
    
    
}
