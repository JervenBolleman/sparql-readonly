package sib.swiss.swissprot.sparql.ro.quads;

import static sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools.getLongAtIndexInLongBuffers;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import dev.hardwood.Hardwood;
import dev.hardwood.reader.ColumnReaders;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.Statement;
import org.roaringbitmap.RoaringBitmap;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import sib.swiss.swissprot.sparql.ro.Term;

import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoBooleanLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoResource;

public class BnodeBooleanList extends RoResourceRoValueList<RoBnode, RoBooleanLiteral> {

    public BnodeBooleanList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, hardwood);
    }

    public BnodeBooleanList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);

    }

    @Override
    protected RoBnode getSubject(long id) {
        return new RoBnode(id);
    }

    @Override
    protected RoBooleanLiteral getObject(long id) {
        return new RoBooleanLiteral(id == 1L);
    }

    @Override
    public Term.Kind subjectKind() {
        return Term.Kind.BNODE;
    }

    @Override
    public Term.Kind objectKind() {
        return Term.Kind.BOOLEAN;
    }

    @Override
    protected RoResourceValueListIterator newIterator(ColumnReaders rows) {
        return new BnodeBooleanListIterator(rows);
    }

    public static class Builder extends AbstractBuilder<RoBnode, RoBooleanLiteral> {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries, Hardwood hardwood)
                throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public BnodeBooleanList build() throws IOException {
            save();
            return new BnodeBooleanList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
        }

    }

    private class BnodeBooleanListIterator extends RoResourceValueListIterator {

        private BnodeBooleanListIterator(ColumnReaders rows) {
            super(rows);
        }

        @Override
        public Statement statement(RoBnode subject, RoIri predicate, RoBooleanLiteral object, RoResource graph) {
            if (graph == null || graph instanceof BNode) {
                return new RoContextStatement(
                        subject, predicate,
                        object, graph);
            } else {
                return new OnlyRoIriContextStatement(subject.getLongId(),
                        predicate.getLongId(),
                        object.getLongId(),
                        graph.getLongId(),
                        dictionaries.getIriDict()
                );
            }
        }
    }

}
