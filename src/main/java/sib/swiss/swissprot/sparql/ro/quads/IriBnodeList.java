package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import dev.hardwood.Hardwood;
import dev.hardwood.reader.RowReader;

import org.roaringbitmap.RoaringBitmap;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;

import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class IriBnodeList extends RoResourceRoValueList<RoIri, RoBnode> {

    public IriBnodeList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
    }

    public IriBnodeList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, hardwood);
    }

    @Override
    protected RoIri getSubject(RowReader row) {
        return new RoIri(row.getLong(SUBJECT), dictionaries.getIriDict());
    }

    @Override
    protected RoBnode getObject(RowReader row) {
        return new RoBnode(row.getLong(OBJECT));
    }

    public static class Builder extends AbstractBuilder<RoIri, RoBnode> {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries, Hardwood hardwood)
                throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public IriBnodeList build() throws IOException {
            save();
            return new IriBnodeList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
        }

    }

    private class IriBnodeListIterator extends RoResourceValueListIterator {

        private IriBnodeListIterator(RowReader rows) {
            super(rows);
        }
    }

    @Override
    protected RoResourceValueListIterator newIterator(RowReader rows) {
        return new IriBnodeListIterator(rows);
    }

}
