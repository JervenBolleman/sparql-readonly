package sib.swiss.swissprot.sparql.ro.values;

import java.io.IOException;
import java.util.Optional;

import org.eclipse.rdf4j.model.IRI;

import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;

public class RoIri implements IRI {
	private static final long serialVersionUID = 1L;
	private static RoIriDictionary dict;
	private static RoNamespaces namespaces;
	private final long id;

	public RoIri(long id) {
		super();
		this.id = id;
	}

	@Override
	public String stringValue() {
		return getNamespace() + getLocalName();
	}

	@Override
	public String getNamespace() {

		return namespaces.getFromId(id).getName();
	}

	@Override
	public String getLocalName() {
		try {
			final Optional<String> localNameFromId = dict
					.getLocalNameFromId(id);
			if (localNameFromId.isPresent())
				return localNameFromId.get();
			else
				throw new RuntimeException("No local name for " + id);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public long getId() {
		return id;
	}
}
