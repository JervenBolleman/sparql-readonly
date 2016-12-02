package sib.swiss.swissprot.sparql.ro;

import org.eclipse.rdf4j.model.Namespace;

public class RoNamespace implements Namespace {
	private static final long serialVersionUID = 1L;
	private final String prefix;
	private final String name;

	public RoNamespace(String prefix, String namespace) {
		this.prefix = prefix;
		this.name = namespace;
	}

	@Override
	public int compareTo(Namespace o) {
		int compare = name.compareTo(o.getName());
		if (compare == 0)
			compare = prefix.compareTo(getPrefix());
		return compare;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getPrefix() {
		return prefix;
	}

	static RoNamespace fromSavedString(String s) {
		int firstColon = s.indexOf(':');
		String prefix = s.substring(0, firstColon);
		String namespace = s.substring(firstColon + 1);
		return new RoNamespace(prefix, namespace);
	}
}
