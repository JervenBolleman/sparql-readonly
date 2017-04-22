package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.rdf4j.model.IRI;

import sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools;
import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class RoIriDictionary {
	private final Map<Integer, RoIriNamespaceDictionary> idToIriDictionaryMap;
	private final RoNamespaces namespaces;

	public Optional<String> getLocalNameFromId(long id) throws IOException {
		int namespaceId = (int) (id >>> 32);
		RoIriNamespaceDictionary namespace = idToIriDictionaryMap
				.get(namespaceId);
		return namespace.getLocalNameFromId(id);
	}

	public RoIriDictionary(
			Map<Integer, RoIriNamespaceDictionary> idToIriDictionary,
			RoNamespaces namespaces) {
		super();
		this.idToIriDictionaryMap = idToIriDictionary;
		this.namespaces = namespaces;
	}

	public RoIriDictionary(RoNamespaces namespaces) {
		super();
		this.idToIriDictionaryMap = new HashMap<>();
		this.namespaces = namespaces;
	}

	public Optional<RoIri> find(IRI iri) {
		final String namespace = iri.getNamespace();
		final RoNamespace key = namespaces.get(namespace);
		if (this.idToIriDictionaryMap.containsKey(key.getId())) {
			return this.idToIriDictionaryMap.get(key.getId()).find(iri);
		} else {
			return Optional.empty();
		}
	}

	public RoNamespaces getNamespaces() {

		return namespaces;

	}

	public RoIriNamespaceDictionary addPredixFollowedByBNumberDictionary(
			File offsetsFile, File lengthString, RoNamespace roNamespace)
			throws IOException {
		RoIriPrefixFollowedByNumberDictionary temp = new RoIriPrefixFollowedByNumberDictionary(
				offsetsFile, lengthString, roNamespace, this);
		idToIriDictionaryMap.put(idToIriDictionaryMap.size(), temp);
		return temp;
	}

	public RoIriNamespaceDictionary addBasicRoIriNamespaceDictionary(
			File offsetsFile, File lengthString, RoNamespace roNamespace)
			throws FileNotFoundException, IOException {
		long[] offsetMap = readOffsetMapIntoMemory(offsetsFile);
		BasicRoIriNamespaceDictionary temp = new BasicRoIriNamespaceDictionary(
				offsetMap, ByteBuffersBackedByFilesTools
						.openByteBuffer(lengthString.toPath()),
				roNamespace);
		idToIriDictionaryMap.put(idToIriDictionaryMap.size(), temp);
		return temp;

	}

	private long[] readOffsetMapIntoMemory(File offsetsFile)
			throws FileNotFoundException, IOException {
		long[] map = new long[(int) (offsetsFile.length() / Long.BYTES)];
		int key = 0;
		try (InputStream reader = new FileInputStream(offsetsFile)) {
			byte[] singleOffset = new byte[Long.BYTES];
			LongBuffer singleOffsetAsLong = ByteBuffer.wrap(singleOffset)
					.asLongBuffer();
			for (int i = 0; i < Long.BYTES; i++) {
				final int read = reader.read();
				if (read == -1) {
					return map;
				}
				singleOffset[i] = (byte) read;

			}
			map[key] = singleOffsetAsLong.get(0);
			key++;
		}
		return map;
	}

	public static RoIriDictionary load(RoNamespaces namespaces2) {
		// TODO Auto-generated method stub
		return null;
	}

	public Stream<IRI> values() {
		return idToIriDictionaryMap.values().stream()
				.flatMap(RoIriNamespaceDictionary::values);

	}

}
