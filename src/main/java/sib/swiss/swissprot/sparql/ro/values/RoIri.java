package sib.swiss.swissprot.sparql.ro.values;

import java.io.IOException;
import java.util.Optional;

import org.eclipse.rdf4j.model.IRI;

import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;

public class RoIri implements IRI, RoResource {

    private static final long serialVersionUID = 1L;
    private final RoIriDictionary dict;
    private final long id;

    public RoIri(long id, RoIriDictionary dict) {
        super();
        this.id = id;
        this.dict = dict;
    }

    @Override
    public String stringValue() {
        return getNamespace() + getLocalName();
    }

    @Override
    public String getNamespace() {

        return dict.getNamespaces().getFromId(id).getName();
    }

    @Override
    public String getLocalName() {
        try {
            final Optional<String> localNameFromId = dict
                    .getLocalNameFromId(id);
            if (localNameFromId.isPresent()) {
                return localNameFromId.get();
            } else {
                throw new RuntimeException("No local name for " + id);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long getLongId() {
        return id;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof RoIri) {
            return id == ((RoIri) object).id;
        } else {
            return super.equals(object);
        }
    }
}
