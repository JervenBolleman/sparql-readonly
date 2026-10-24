package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import dev.hardwood.Hardwood;
import dev.hardwood.reader.ColumnReaders;

import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import sib.swiss.swissprot.sparql.ro.Term;

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


    public BnodeBigIntegerList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, dictionaries, hardwood);
    }

    @Override
    protected RoBnode getSubject(long id) {
        return new RoBnode(id);
    }

    @Override
    protected RoBigIntegerLiteral getObject(long id) {
        try {
            return dictionaries.getIntDict().getFromId(id);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Term.Kind subjectKind() {
        return Term.Kind.BNODE;
    }

    @Override
    public Term.Kind objectKind() {
        return Term.Kind.INTEGER;
    }

    @Override
    protected RoResourceValueListIterator newIterator(ColumnReaders rows) {
        return new BnodeBigIntegerListIterator(rows);
    }

    public static class Builder extends AbstractBuilder<RoBnode, RoBigIntegerLiteral> {

        public Builder(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public BnodeBigIntegerList build() throws IOException {
            save();
            return new BnodeBigIntegerList(file, predicate, dictionaries, hardwood);
        }
    }

    private class BnodeBigIntegerListIterator extends RoResourceValueListIterator {

        private BnodeBigIntegerListIterator(ColumnReaders rows) {
            super(rows);
        }
    }
}
