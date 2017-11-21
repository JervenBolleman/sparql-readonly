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
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.RDFHandlerException;

import sib.swiss.swissprot.sparql.ro.RoPredicateStore.Builder;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoBnodeDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;
import sib.swiss.swissprot.sparql.ro.values.RoValue;

public class PredicateListBuildingHandler implements RDFHandler {

    private final RoStore roStore;
    private final RoBnodeDictionary bnodeDict;
    private final RoIriDictionary iriDict;
    private final RoLiteralDict literalDict;
    private final Map<RoIri, RoPredicateStore.Builder> builders = new HashMap<>();
    private final File predicateListsDir;
    private final RoNamespaces roNamespaces;

    public PredicateListBuildingHandler(RoStore roStore,
            RoBnodeDictionary bnodeDict, RoIriDictionary iriDict,
            RoLiteralDict literalDict, RoNamespaces roNamespaces,
            File predicateListsDir) {
        this.roStore = roStore;
        this.bnodeDict = bnodeDict;
        this.iriDict = iriDict;
        this.literalDict = literalDict;
        this.roNamespaces = roNamespaces;
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
        roNamespaces.add(prefix, uri);

    }

    @Override
    public void handleStatement(Statement st) throws RDFHandlerException {
        try {
            Optional<RoIri> found = iriDict.find(st.getPredicate());
            if (!found.isPresent()) {
                throw new RDFHandlerException("all IRIs should be in the iriDict" + st.getPredicate().stringValue());
            }
            final RoIri predicate = found.get();
            Builder builder = builders.get(predicate);
            if (builder == null) {
                builder = new RoPredicateStore.Builder(RoPredicateStore.initDirectory(predicateListsDir, predicate),
                        predicate, literalDict, roNamespaces, iriDict, bnodeDict);
                builders.put(predicate, builder);
            }
            RoResource subject = find(st.getSubject()).get();
            RoValue object = find(st.getObject()).get();
            if (st.getContext() != null) {
                RoResource context = find(st.getContext()).get();
                builder.add(subject, object, context);
            } else
                builder.add(subject, object, null);
        } catch (IOException e) {
            throw new RDFHandlerException(e);
        }

    }

    private Optional<? extends RoResource> find(Resource subject) {
        if (subject instanceof IRI) {
            return iriDict.find((IRI) subject);
        } else if (subject instanceof BNode) {
            return bnodeDict.find((BNode) subject);
        } else {
            return Optional.empty();
        }
    }

    private Optional<? extends RoValue> find(Value subject) {
        if (subject instanceof Resource) {
            return find((Resource) subject);
        } else {
            return literalDict.find((Literal) subject);
        }
    }

    @Override
    public void handleComment(String comment) throws RDFHandlerException {
        // TODO Auto-generated method stub

    }

}
