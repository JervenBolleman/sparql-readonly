package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

public class BasicRoIriNamespaceDictionary extends RoDictionary implements
		RoIriNamespaceDictionary {

	private final String namespace;
	private final int namespaceId;

	public BasicRoIriNamespaceDictionary(long[] offSetMap,
			ByteBuffer[] backingFile, String namespace, int namespaceId) {
		super(offSetMap, backingFile);
		this.namespace = namespace;
		this.namespaceId = namespaceId;
	}

	@Override
	public Optional<String> getLocalNameFromId(long id) throws IOException {
		int withoutMask = (int) id;
		long offset = getOffset(withoutMask);
		if (offset > -1)
			return Optional.of(readStringAt(offset));
		else
			return Optional.empty();

	}

	@Override
	public String getNamespace() {
		return namespace;
	}

	@Override
	public int getNamespaceId() {
		return namespaceId;
	}
}
