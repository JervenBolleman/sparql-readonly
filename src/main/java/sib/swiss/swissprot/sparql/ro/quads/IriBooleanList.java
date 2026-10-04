package sib.swiss.swissprot.sparql.ro.quads;

import static sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools.getLongAtIndexInLongBuffers;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import dev.hardwood.Hardwood;
import dev.hardwood.reader.RowReader;
import org.roaringbitmap.RoaringBitmap;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import sib.swiss.swissprot.sparql.ro.Term;
import static sib.swiss.swissprot.sparql.ro.quads.RoResourceRoValueList.SUBJECT;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;

import sib.swiss.swissprot.sparql.ro.values.RoBooleanLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoResource;

public class IriBooleanList extends RoResourceRoValueList<RoIri, RoBooleanLiteral> {

    public IriBooleanList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
    }

    public IriBooleanList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, hardwood);
    }

    @Override
    protected RoIri getSubject(RowReader row) {
        return new RoIri(row.getLong(SUBJECT), dictionaries.getIriDict());
    }

    @Override
    protected RoBooleanLiteral getObject(RowReader row) {
        return new RoBooleanLiteral(row.getLong(OBJECT) == 1L);
    }

    @Override
    public Term.Kind subjectKind() {
        return Term.Kind.IRI;
    }

    @Override
    public Term.Kind objectKind() {
        return Term.Kind.BOOLEAN;
    }

    @Override
    protected RoResourceValueListIterator newIterator(RowReader rows) {
        return new IriBooleanListIterator(rows);
    }

    public static class Builder extends AbstractBuilder<RoIri, RoBooleanLiteral> {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries, Hardwood hardwood)
                throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public IriBooleanList build() throws IOException {
            save();
            return new IriBooleanList(file, predicate, iriGraphsMap, bnodeGraphsMap,
                    dictionaries, hardwood);
        }

    }

    private class IriBooleanListIterator extends RoResourceValueListIterator {

        private IriBooleanListIterator(RowReader rows) {
            super(rows);
        }
    }
}
