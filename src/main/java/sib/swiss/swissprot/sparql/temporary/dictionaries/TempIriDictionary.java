package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.IRI;

import sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools;
import sib.swiss.swissprot.sparql.ro.FileNameEncoderFunctions;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriNamespaceDictionary;

import com.google.common.io.Files;

public class TempIriDictionary extends TempDictionary {

	private final Map<String, FileOutputStream> namespaces = new HashMap<>();

	public TempIriDictionary(File out) {
		super(out);
	}

	public void add(IRI subject) throws IOException {
		final String namespace = subject.getNamespace();
		FileOutputStream fos = namespaces.get(namespace);
		if (fos == null) {
			fos = new FileOutputStream(new File(out, "temp-"
					+ FileNameEncoderFunctions.encodeNamespace(namespace)));
			namespaces.put(namespace, fos);
		}
		fos.write(StandardCharsets.UTF_8.encode(subject.getLocalName()).array());
		fos.write('\n');
	}

	@Override
	public void close() throws IOException {
		for (FileOutputStream fos : namespaces.values())
			fos.close();
	}

	public RoIriDictionary load() throws IOException {
		final List<NamespaceFilePair> bigestNamespaceFirst = namespaces
				.keySet()
				.stream()
				.map(s -> new NamespaceFilePair(s))
				.sorted((f1, f2) -> Long.compare(f1.file.length(),
						f2.file.length())).collect(Collectors.toList());
		Map<Integer, RoIriNamespaceDictionary> map = new HashMap<>();
		int key = 1;
		for (NamespaceFilePair namespaceTempFile : bigestNamespaceFirst) {

			File tempFile = namespaceTempFile.file;
			String namespace = namespaceTempFile.namespace;
			File lengthString = new File(out, key + "-iris");
			File offsetsFile = new File(key + "-offsets");
			{
				final List<String> tempNodes = readTempFileIntoMemory(tempFile);
				writeNamespacesToDisk(tempNodes, lengthString, offsetsFile);
			}
			long[] offsetMap = readOffsetMapIntoMemory(offsetsFile);
			map.put(key,
					new RoIriNamespaceDictionary(offsetMap,
							ByteBuffersBackedByFilesTools
									.openByteBuffer(lengthString.toPath()),
							namespace, key));
			key = key + 1;
		}
		return new RoIriDictionary(map);
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

	private List<String> readTempFileIntoMemory(File tempFile)
			throws IOException {
		return Files.readLines(tempFile, StandardCharsets.UTF_8).stream()
				.sorted(Collator.getInstance(Locale.US)).distinct()
				.collect(Collectors.toList());
	}

	private void writeNamespacesToDisk(final List<String> tempNodes,
			File lengthString, File offsetsFile) throws IOException,
			FileNotFoundException {
		long pos = 0;
		try (FileOutputStream dataOs = new FileOutputStream(lengthString);
				FileOutputStream offsetsOs = new FileOutputStream(offsetsFile)) {
			for (int i = 0; i < tempNodes.size(); i++) {
				String node = tempNodes.get(i);
				final byte[] data = stringAsUtf8ByteArray(node);
				final byte[] length = new byte[Integer.BYTES];
				ByteBuffer.wrap(length).asIntBuffer().put(data.length);
				dataOs.write(length);
				dataOs.write(data);
				long newPos = pos + data.length;
				final byte[] position = new byte[Long.BYTES];
				ByteBuffer.wrap(position).asLongBuffer().put(pos);
				offsetsOs.write(position);
				pos = newPos;
			}
		}
	}

	private final class NamespaceFilePair {
		private final String namespace;
		private final File file;

		public NamespaceFilePair(String s) {
			this.namespace = s;
			this.file = new File(out,
					FileNameEncoderFunctions.encodeNamespace(s));
		}
	}

}
