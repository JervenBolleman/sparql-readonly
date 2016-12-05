package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.roaringbitmap.buffer.ImmutableRoaringBitmap;

public class RoIriPrefixFollowedByNumber implements RoIriNamespaceDictionary {

	private final String namespace;
	private final int namespaceId;
	private final String prefix;
	private final int minLength;
	private final int maxLength;
	private final ImmutableRoaringBitmap bitmap;

	public RoIriPrefixFollowedByNumber(File infoFile, File bitmapFile,
			String namespace, int namespaceId) throws IOException {
		this.namespace = namespace;
		this.namespaceId = namespaceId;

		List<String> info = Files.readAllLines(infoFile.toPath());
		int prefixLength = Integer.parseInt(info.get(0));
		int i = 1;
		String tempprefix = info.get(i);
		while (i < info.size() && tempprefix.length() < prefixLength) {
			tempprefix += info.get(++i);
		}
		prefix = tempprefix;
		minLength = Integer.parseInt(info.get(++i));
		maxLength = Integer.parseInt(info.get(++i));
		assert minLength == maxLength;
		try (final RandomAccessFile ad = new RandomAccessFile(bitmapFile, "r")) {
			final MappedByteBuffer bb = ad.getChannel().map(MapMode.READ_ONLY,
					0, ad.length());
			bitmap = new ImmutableRoaringBitmap(bb);
		}
	}

	@Override
	public Optional<String> getLocalNameFromId(long id) throws IOException {

		if (bitmap.contains((int) id)) {
			String value = Integer.toString((int) id);
			String padding;
			if (value.length() + prefix.length() == minLength)
				padding = "";
			else {
				int pl = minLength - (value.length() + prefix.length());
				char[] pa = new char[pl];
				Arrays.fill(pa, '0');
				padding = new String(pa);
			}
			return Optional.of(prefix + padding + value);
		} else
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
