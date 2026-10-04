package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import dev.hardwood.Hardwood;
import dev.hardwood.reader.RowReader;

import org.roaringbitmap.RoaringBitmap;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;

import sib.swiss.swissprot.sparql.ro.values.RoBigIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

/**
 * Objects are xsd:integer, small ones are stored as their value, the others
 * by their id in the integer dictionary.
 *
 * @see sib.swiss.swissprot.sparql.ro.dictionaries.RoBigIntegerDict
 */
public class BnodeBigIntegerList extends RoResourceRoValueList<RoBnode, RoBigIntegerLiteral> {

    public BnodeBigIntegerList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
    }

    public BnodeBigIntegerList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, hardwood);
    }

    @Override
    protected RoBnode getSubject(RowReader row) {
        return new RoBnode(row.getLong(SUBJECT));
    }

    @Override
    protected RoBigIntegerLiteral getObject(RowReader row) {
        try {
            return dictionaries.getIntDict().getFromId(row.getLong(OBJECT));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected RoResourceValueListIterator newIterator(RowReader rows) {
        return new BnodeBigIntegerListIterator(rows);
    }

    public static class Builder extends AbstractBuilder<RoBnode, RoBigIntegerLiteral> {

        public Builder(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public BnodeBigIntegerList build() throws IOException {
            save();
            return new BnodeBigIntegerList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, hardwood);
        }
    }

    private class BnodeBigIntegerListIterator extends RoResourceValueListIterator {

        private BnodeBigIntegerListIterator(RowReader rows) {
            super(rows);
        }
    }
}
