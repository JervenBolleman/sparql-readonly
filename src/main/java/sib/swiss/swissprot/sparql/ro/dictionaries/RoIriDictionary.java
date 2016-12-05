package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class RoIriDictionary {
	private final Map<Integer, RoIriNamespaceDictionary> namepacemap;

	public Optional<String> getLocalNameFromId(long id) throws IOException {
		int namespaceId = (int) id >>> 32;
		return namepacemap.get(namespaceId).getLocalNameFromId(id);
	}

	public RoIriDictionary(Map<Integer, RoIriNamespaceDictionary> namepacemap) {
		super();
		this.namepacemap = namepacemap;
	}
}
