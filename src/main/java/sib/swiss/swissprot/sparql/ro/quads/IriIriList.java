package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.TypeDescription;

import org.eclipse.rdf4j.model.Statement;
import org.roaringbitmap.RoaringBitmap;
import sib.swiss.swissprot.sparql.ro.RoDictionaries;
import static sib.swiss.swissprot.sparql.ro.quads.RoResourceRoValueList.SUBJECT;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;

import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;

public class IriIriList extends RoResourceRoValueList<RoIri, RoIri> {

    public IriIriList(File file, RoIri predicate, RoDictionaries dictionaries, Configuration configuration) throws IOException {
        this(file, predicate, getRoIriGraphsMap(file, dictionaries.getIriDict()), getRoBnodeGraphsMap(file), dictionaries, configuration);
    }

    public IriIriList(File file, RoIri predicate, Map<RoIri, RoaringBitmap> iriGraphsMap, Map<RoBnode, RoaringBitmap> bnodeGraphsMap, RoDictionaries dictionaries, Configuration configuration) throws IOException {
        super(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, configuration);
    }

    @Override
    public Iterator<Statement> iterator() {
        return new IriIriListIterator(getSchema());
    }

    @Override
    protected RoIri getSubject(VectorizedRowBatch batch, int index) {
        return new RoIri(((LongColumnVector) batch.cols[0]).vector[index], dictionaries.getIriDict());
    }

    @Override
    protected RoIri getObject(VectorizedRowBatch batch, int index) {
        return new RoIri(((LongColumnVector) batch.cols[1]).vector[index], dictionaries.getIriDict());
    }

    private static TypeDescription getSchema() {
        return TypeDescription
                .createStruct()
                .addField(SUBJECT, TypeDescription.createLong())
                .addField(OBJECT, TypeDescription.createLong());
    }

    public static class Builder extends AbstractBuilder<RoIri, RoIri> {

        public Builder(File file, RoIri predicate,
                RoDictionaries dictionaries, Configuration configuration)
                throws IOException {
            super(file, predicate, dictionaries, configuration, IriIriList::getSchema);
        }

        public IriIriList build() throws IOException {
            save();
            return new IriIriList(file, predicate, iriGraphsMap, bnodeGraphsMap, dictionaries, configuration);
        }

    }

    private class IriIriListIterator extends RoResourceValueListIterator {

        private IriIriListIterator(TypeDescription schema) {
            super(schema);
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
