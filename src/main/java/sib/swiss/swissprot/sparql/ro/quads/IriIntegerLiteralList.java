package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import dev.hardwood.Hardwood;
import dev.hardwood.reader.RowReader;

import org.roaringbitmap.RoaringBitmap;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import static sib.swiss.swissprot.sparql.ro.quads.RoResourceRoValueList.SUBJECT;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;

import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class IriIntegerLiteralList extends RoResourceRoValueList<RoIri, RoIntegerLiteral> {

    public IriIntegerLiteralList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Hardwood hardwood)
            throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
    }

    public IriIntegerLiteralList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, hardwood);
    }

    @Override
    protected RoIri getSubject(RowReader row) {
        return new RoIri(row.getLong(SUBJECT), dictionaries.getIriDict());
    }

    @Override
    protected RoIntegerLiteral getObject(RowReader row) {
        return new RoIntegerLiteral(row.getLong(OBJECT));
    }

    @Override
    protected RoResourceValueListIterator newIterator(RowReader rows) {
        return new IriIntegerListIterator(rows);
    }

    public static class Builder extends AbstractBuilder<RoIri, RoIntegerLiteral> {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries, Hardwood hardwood)
                throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public IriIntegerLiteralList build() throws IOException {
            save();
            return new IriIntegerLiteralList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
        }

    }

    private class IriIntegerListIterator extends RoResourceValueListIterator {

        private IriIntegerListIterator(RowReader rows) {
            super(rows);
        }
    }
}
