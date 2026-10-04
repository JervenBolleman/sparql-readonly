package sib.swiss.swissprot.sparql.ro.values;

import java.io.IOException;
import java.util.Optional;

import org.eclipse.rdf4j.model.IRI;

import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;

public class RoIri implements IRI, RoResource {

	private static final long serialVersionUID = 1L;
	private final RoIriDictionary dict;
	private final long id;
	/**
	 * The hash of the IRI string, as {@link IRI#hashCode()} requires. Zero
	 * until known, computing it needs a dictionary lookup.
	 */
	private int hash;

	public RoIri(long id, RoIriDictionary dict) {
		super();
		this.id = id;
		assert dict != null;
		this.dict = dict;
	}

	/**
	 * For an IRI found in the dictionary, saves looking up its string to hash
	 * it.
	 *
	 * @param found the IRI that has this id
	 */
	public RoIri(long id, RoIriDictionary dict, IRI found) {
		this(id, dict);
		this.hash = found.stringValue().hashCode();
	}

	@Override
	public String stringValue() {
		return getNamespace() + getLocalName();
	}

	@Override
	public String getNamespace() {
		final RoNamespaces namespaces = dict.getNamespaces();
		return namespaces.getFromId(id).getName();
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
		} else if (object instanceof IRI) {
			return object.equals(this);
		} else {
			return super.equals(object);
		}
	}

	@Override
	public int hashCode() {
		int h = hash;
		if (h == 0) {
			h = stringValue().hashCode();
			hash = h;
		}
		return h;
	}

	@Override
	public String toString() {
		return this.getNamespace() + this.getLocalName();
	}
}
