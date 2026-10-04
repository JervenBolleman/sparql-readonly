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

public class BnodeBnodeList extends RoResourceRoValueList<RoBnode, RoBnode> {

    public BnodeBnodeList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, hardwood);
    }

    public BnodeBnodeList(File file, RoIri predicate,
            Map<RoIri, RoaringBitmap> iriGraphsMap,
            Map<RoBnode, RoaringBitmap> bNodeGraphsMap,
            RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, iriGraphsMap, bNodeGraphsMap, dictionaries, hardwood);
    }

    @Override
    protected RoBnode getSubject(RowReader row) {
        return new RoBnode(row.getLong(SUBJECT));
    }

    @Override
    protected RoBnode getObject(RowReader row) {
        return new RoBnode(row.getLong(OBJECT));
    }

    public static class Builder extends AbstractBuilder<RoBnode, RoBnode> {

        public Builder(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public BnodeBnodeList build() throws IOException {
            save();
            return new BnodeBnodeList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
        }
    }

    private class BnodeBnodeListIterator extends RoResourceValueListIterator {

        private BnodeBnodeListIterator(RowReader rows) {
            super(rows);
        }

    }

    @Override
    protected RoResourceValueListIterator newIterator(RowReader rows) {
        return new BnodeBnodeListIterator(rows);
    }

}
