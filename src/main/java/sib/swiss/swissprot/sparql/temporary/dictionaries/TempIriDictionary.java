package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Collator;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.model.IRI;
import org.roaringbitmap.RoaringBitmap;

import sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools;
import sib.swiss.swissprot.sparql.ro.FileNameEncoderFunctions;
import sib.swiss.swissprot.sparql.ro.dictionaries.BasicRoIriNamespaceDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriNamespaceDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriPrefixFollowedByNumber;

public class TempIriDictionary extends TempDictionary {

	private class DataDistribution {
		private String longestCommonPrefix;
		private boolean hasCommonPrefix;
		private int minLength;
		private int maxLength;
		private boolean allNumericAfterLongestCommonPrefix = true;

		public String measure(String s) {
			minLength = Math.min(minLength, s.length());
			maxLength = Math.max(maxLength, s.length());
			char[] a = s.toCharArray();
			if (longestCommonPrefix == null) {
				for (int i = a.length; i >= 0; i--) {
					char c = a[i];
					if (!Character.isDigit(c) && c < 256) { // Only ascii
															// codepoints for
															// now
						String prefix = s.substring(0, i);
						if (longestCommonPrefix == null)
							longestCommonPrefix = prefix;
						else if (!prefix.equals(longestCommonPrefix))
							hasCommonPrefix = false;
						return s;
					}
				}
			} else if (allNumericAfterLongestCommonPrefix && hasCommonPrefix
					&& s.startsWith(longestCommonPrefix)) {
				try {
					Integer.parseInt(s.substring(longestCommonPrefix.length()));
				} catch (NumberFormatException e) {
					allNumericAfterLongestCommonPrefix = false;
					hasCommonPrefix = false;
				}
			}
			return s;
		}

		public boolean allNumericWithPrefix() {
			return hasCommonPrefix && allNumericAfterLongestCommonPrefix
					&& minLength == maxLength;
		}
	}

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
			DataDistribution data = new DataDistribution();
			{
				final List<String> tempNodes = readTempFileIntoMemory(tempFile,
						data);
				writeNamespacesToDisk(tempNodes, lengthString, offsetsFile,
						data);
			}
			if (data.allNumericWithPrefix()) {
				map.put(key, new RoIriPrefixFollowedByNumber(offsetsFile,
						lengthString, namespace, key));
			} else {
				long[] offsetMap = readOffsetMapIntoMemory(offsetsFile);
				map.put(key,
						new BasicRoIriNamespaceDictionary(offsetMap,
								ByteBuffersBackedByFilesTools
										.openByteBuffer(lengthString.toPath()),
								namespace, key));
			}
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

	private List<String> readTempFileIntoMemory(File tempFile,
			DataDistribution data) throws IOException {

		return Files.lines(tempFile.toPath(), StandardCharsets.UTF_8)
				.sorted(Collator.getInstance(Locale.US)).distinct()
				.peek(s -> data.measure(s)).collect(Collectors.toList());
	}

	private void writeNamespacesToDisk(final List<String> tempNodes,
			File lengthString, File offsetsFile, DataDistribution data2)
			throws IOException, FileNotFoundException {
		if (data2.allNumericAfterLongestCommonPrefix) {
			writeNamespaceWithPrefixedNumbersToDisk(tempNodes, lengthString,
					offsetsFile, data2);
		} else {
			writeNamespaceWithLongIdsToDisk(tempNodes, lengthString,
					offsetsFile);
		}
	}

	private void writeNamespaceWithLongIdsToDisk(final List<String> tempNodes,
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

	private void writeNamespaceWithPrefixedNumbersToDisk(
			final List<String> tempNodes, File lengthString, File offsetsFile,
			DataDistribution data2) throws IOException, FileNotFoundException {
		RoaringBitmap map = new RoaringBitmap();
		int start = data2.longestCommonPrefix.length();
		for (int i = 0; i < tempNodes.size(); i++) {
			map.add(Integer.parseInt(tempNodes.get(i).substring(start)));
		}
		try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(
				lengthString))) {
			map.serialize(dos);
		}
		List<String> info = Arrays.asList(new String[] {
				String.valueOf(data2.longestCommonPrefix.length()),
				data2.longestCommonPrefix, String.valueOf(data2.minLength),
				String.valueOf(data2.maxLength) });
		Files.write(offsetsFile.toPath(), info, StandardCharsets.UTF_8);
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
