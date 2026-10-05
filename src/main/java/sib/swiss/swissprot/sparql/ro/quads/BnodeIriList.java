package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import dev.hardwood.Hardwood;
import dev.hardwood.reader.ColumnReaders;

import org.roaringbitmap.RoaringBitmap;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import sib.swiss.swissprot.sparql.ro.Term;

import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class BnodeIriList extends RoResourceRoValueList<RoBnode, RoIri> {

    public BnodeIriList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, hardwood);
    }

    public BnodeIriList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
    }

    @Override
    protected RoBnode getSubject(long id) {
        return new RoBnode(id);
    }

    @Override
    protected RoIri getObject(long id) {
        return new RoIri(id, dictionaries.getIriDict());
    }

    public static class Builder extends AbstractBuilder<RoBnode, RoIri> {

        public Builder(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public BnodeIriList build() throws IOException {
            save();
            return new BnodeIriList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
        }

    }

    private class BnodeIriListIterator extends RoResourceValueListIterator {

        private BnodeIriListIterator(ColumnReaders rows) {
            super(rows);
        }
    }

    @Override
    public Term.Kind subjectKind() {
        return Term.Kind.BNODE;
    }

    @Override
    public Term.Kind objectKind() {
        return Term.Kind.IRI;
    }

    @Override
    protected RoResourceValueListIterator newIterator(ColumnReaders rows) {
        return new BnodeIriListIterator(rows);
    }

}
