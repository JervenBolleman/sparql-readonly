package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.nio.ByteBuffer;

public class RoIriNamespaceDictionary extends RoDictionary {

	private final String namespace;
	private final int namespaceId;

	public RoIriNamespaceDictionary(long[] offSetMap, ByteBuffer[] backingFile,
			String namespace, int namespaceId) {
		super(offSetMap, backingFile);
		this.namespace = namespace;
		this.namespaceId = namespaceId;
	}

	public String getLocalNameFromId(long id) throws IOException {
		int withoutMask = (int) id;
		long offset = getOffset(withoutMask);
		return readStringAt(offset);
	}

	public String getNamespace() {
		return namespace;
	}

	public int getNamespaceId() {
		return namespaceId;
	}
}
