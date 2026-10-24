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
public class IriBigIntegerList extends RoResourceRoValueList<RoIri, RoBigIntegerLiteral> {


    public IriBigIntegerList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, dictionaries, hardwood);
    }

    @Override
    protected RoIri getSubject(long id) {
        return new RoIri(id, dictionaries.getIriDict());
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
        return Term.Kind.IRI;
    }

    @Override
    public Term.Kind objectKind() {
        return Term.Kind.INTEGER;
    }

    @Override
    protected RoResourceValueListIterator newIterator(ColumnReaders rows) {
        return new IriBigIntegerListIterator(rows);
    }

    public static class Builder extends AbstractBuilder<RoIri, RoBigIntegerLiteral> {

        public Builder(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public IriBigIntegerList build() throws IOException {
            save();
            return new IriBigIntegerList(file, predicate, dictionaries, hardwood);
        }
    }

    private class IriBigIntegerListIterator extends RoResourceValueListIterator {

        private IriBigIntegerListIterator(ColumnReaders rows) {
            super(rows);
        }
    }
}
