package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import dev.hardwood.Hardwood;
import dev.hardwood.reader.ColumnReaders;

import org.eclipse.rdf4j.model.Statement;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import sib.swiss.swissprot.sparql.ro.Term;
import static sib.swiss.swissprot.sparql.ro.quads.RoResourceRoValueList.SUBJECT;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;

import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;

public class IriIriList extends RoResourceRoValueList<RoIri, RoIri> {

    public IriIriList(File file, RoIri predicate, RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
        super(file, predicate, dictionaries, hardwood);
    }


    @Override
    public Term.Kind subjectKind() {
        return Term.Kind.IRI;
    }

    @Override
    public Term.Kind objectKind() {
        return Term.Kind.IRI;
    }

    @Override
    protected RoResourceValueListIterator newIterator(ColumnReaders rows) {
        return new IriIriListIterator(rows);
    }

    @Override
    protected RoIri getSubject(long id) {
        return new RoIri(id, dictionaries.getIriDict());
    }

    @Override
    protected RoIri getObject(long id) {
        return new RoIri(id, dictionaries.getIriDict());
    }

    public static class Builder extends AbstractBuilder<RoIri, RoIri> {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries, Hardwood hardwood)
                throws IOException {
            super(file, predicate, dictionaries, hardwood);
        }

        public IriIriList build() throws IOException {
            save();
            return new IriIriList(file, predicate, dictionaries, hardwood);
        }

    }

    private class IriIriListIterator extends RoResourceValueListIterator {

        private IriIriListIterator(ColumnReaders rows) {
            super(rows);
        }

        @Override
        public Statement statement(RoIri subject, RoIri predicate, RoIri object, RoResource graph) {
            long subjectId = subject.getLongId();
            long objectId = object.getLongId();
            if (graph instanceof RoBnode) {
                return new RoContextStatement(
                        new RoIri(subjectId, dictionaries.getIriDict()), predicate,
                        new RoIri(objectId, dictionaries.getIriDict()), graph);
            } else if (graph instanceof RoIri) {
                return new OnlyRoIriContextStatement(subjectId,
                        predicate.getLongId(), objectId, graph.getLongId(),
                        dictionaries.getIriDict());
            } else {
                return new OnlyRoIriStatement(subjectId, predicate.getLongId(), objectId, dictionaries.getIriDict());
            }
        }

    }
}
