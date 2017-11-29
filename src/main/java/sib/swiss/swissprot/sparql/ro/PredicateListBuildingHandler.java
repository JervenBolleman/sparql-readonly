package sib.swiss.swissprot.sparql.ro;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.IntegerLiteral;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.RDFHandlerException;

import sib.swiss.swissprot.sparql.ro.RoPredicateStore.Builder;
import sib.swiss.swissprot.sparql.ro.values.RoBooleanLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;
import sib.swiss.swissprot.sparql.ro.values.RoValue;

public class PredicateListBuildingHandler implements RDFHandler {

    private final RoStore roStore;
    private final Map<RoIri, RoPredicateStore.Builder> builders = new HashMap<>();
    private final File predicateListsDir;
    private final RoDictionaries dictionaries;

    public PredicateListBuildingHandler(RoStore roStore,
            RoDictionaries dictionaries,
            File predicateListsDir) {
        this.roStore = roStore;
        this.dictionaries = dictionaries;
        this.predicateListsDir = predicateListsDir;
    }

    @Override
    public void startRDF() throws RDFHandlerException {
        // TODO Auto-generated method stub

    }

    @Override
    public void endRDF() throws RDFHandlerException {
        for (Builder builder : builders.values()) {
            try {
                roStore.addPredicateStore(builder.build());
            } catch (IOException e) {
                throw new RDFHandlerException(e);
            }
        }
    }

    @Override
    public void handleNamespace(String prefix, String uri)
            throws RDFHandlerException {
        dictionaries.getIriDict().getNamespaces().add(prefix, uri);

    }

    @Override
    public void handleStatement(Statement st) throws RDFHandlerException {
        try {
            Optional<RoIri> found = dictionaries.getIriDict().find(st.getPredicate());
            if (!found.isPresent()) {
                throw new RDFHandlerException("all IRIs should be in the iriDict" + st.getPredicate().stringValue());
            }
            final RoIri predicate = found.get();
            Builder builder = builders.get(predicate);
            if (builder == null) {
                builder = new RoPredicateStore.Builder(RoPredicateStore.initDirectory(predicateListsDir, predicate),
                        predicate, dictionaries);
                builders.put(predicate, builder);
            }
            RoResource subject = find(st.getSubject()).get();
            RoValue object = find(st.getObject()).get();
            if (st.getContext() != null) {
                RoResource context = find(st.getContext()).get();
                builder.add(subject, object, context);
            } else {
                builder.add(subject, object, null);
            }
        } catch (IOException e) {
            throw new RDFHandlerException(e);
        }

    }

    private Optional<? extends RoResource> find(Resource subject) {
        if (subject instanceof IRI) {
            return dictionaries.getIriDict().find((IRI) subject);
        } else if (subject instanceof BNode) {
            return dictionaries.getBnodeDict().find((BNode) subject);
        } else {
            return Optional.empty();
        }
    }

    private Optional<? extends RoValue> find(Value subject) {
        if (subject instanceof Resource) {
            return find((Resource) subject);
        } else {
            final Literal literal = (Literal) subject;
            if (subject instanceof IntegerLiteral) {
                return dictionaries.getIntDict().find((IntegerLiteral) subject);
            } else if (XMLSchema.INTEGER.equals(literal.getDatatype())) {
                return dictionaries.getIntDict().find(literal.integerValue());
            } else if (XMLSchema.INT.equals(literal.getDatatype())) {
                return Optional.of(new RoIntegerLiteral(literal.intValue()));
            } else if (XMLSchema.BOOLEAN.equals(literal.getDatatype())) {
                return Optional.of(new RoBooleanLiteral(literal.booleanValue()));
            } else {
                return dictionaries.getLiteralDict().find(literal);
            }
        }
    }

    @Override
    public void handleComment(String comment) throws RDFHandlerException {
        // TODO Auto-generated method stub

    }

}
