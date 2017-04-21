package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator.OfInt;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.rdf4j.model.IRI;
import org.roaringbitmap.IntIterator;
import org.roaringbitmap.buffer.ImmutableRoaringBitmap;

import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.values.RoIri;

public class RoIriPrefixFollowedByNumberDictionary
		implements RoIriNamespaceDictionary {

	private final class OfIntImplementation implements OfInt {
		final long max = bitmap.getLongCardinality();
		final IntIterator intIterator = bitmap.getIntIterator();

		@Override
		public long estimateSize() {
			return max;
		}

		@Override
		public int characteristics() {
			return ORDERED | SORTED | DISTINCT | IMMUTABLE | SIZED;
		}

		@Override
		public OfInt trySplit() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public boolean tryAdvance(IntConsumer action) {
			if (intIterator.hasNext()) {
				action.accept(intIterator.next());
				return true;
			} else {
				return false;
			}
		}

		@Override
		public Comparator<? super Integer> getComparator() {
			return Integer::compare;
		}
	}

	private final RoNamespace namespace;
	private final String prefix;
	private final int minLength;
	private final int maxLength;
	private final ImmutableRoaringBitmap bitmap;
	private final RoIriDictionary roIriDictionary;

	protected RoIriPrefixFollowedByNumberDictionary(File infoFile,
			File bitmapFile, RoNamespace namespace,
			RoIriDictionary roIriDictionary) throws IOException {

		this.namespace = namespace;
		this.roIriDictionary = roIriDictionary;

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
		try (final RandomAccessFile ad = new RandomAccessFile(bitmapFile,
				"r")) {
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
		return namespace.getName();
	}

	@Override
	public int getNamespaceId() {
		return namespace.getId();
	}

	@Override
	public Optional<RoIri> find(IRI predicate) {
		if (predicate.getNamespace().equals(namespace)) {
			if (predicate.getLocalName().startsWith(prefix)) {
				try {
					int id = Integer.valueOf(predicate.getLocalName()
							.substring(prefix.length()));
					if (bitmap.contains(id)) {
						return Optional.of(
								new RoIri(nameSpacedId(id), roIriDictionary));
					}
				} catch (NumberFormatException e) {
					return Optional.empty();
				}
			}
		}
		return Optional.empty();
	}

	private long nameSpacedId(int id) {
		return id | (namespace.getId() << 32);
	}

	@Override
	public Stream<IRI> values() {
		return StreamSupport.intStream(new OfIntImplementation(), false)
				.mapToLong(this::nameSpacedId)
				.mapToObj(id -> new RoIri(id, roIriDictionary));
	}
}
