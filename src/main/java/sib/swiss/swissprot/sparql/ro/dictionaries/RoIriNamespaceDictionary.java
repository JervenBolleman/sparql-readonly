package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.util.Optional;

public interface RoIriNamespaceDictionary {
	public Optional<String> getLocalNameFromId(long id) throws IOException;

	public String getNamespace();

	public int getNamespaceId();
}
