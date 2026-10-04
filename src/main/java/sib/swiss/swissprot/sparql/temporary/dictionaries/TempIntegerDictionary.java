package sib.swiss.swissprot.sparql.temporary.dictionaries;

import static sib.swiss.swissprot.sparql.ro.dictionaries.RoBigIntegerDict.INT_LONG_VALUE;
import static sib.swiss.swissprot.sparql.ro.dictionaries.RoDictionary.SECOND_BYTE_TRUE;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import dev.hardwood.Hardwood;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoBigIntegerDict;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoDictionary;

/**
 * Collects the integers that are not their own id, see
 * {@link RoBigIntegerDict}. They are stored in the order they are first seen,
 * so the provisional id is the final one without the high bit.
 */
public class TempIntegerDictionary extends TempDictionary {

	private final ProvisionalGroup<BigInteger> values = new ProvisionalGroup<>(0);

	public TempIntegerDictionary(File out, Hardwood hardwood) {
		super(out, hardwood);
	}

	/**
	 * @return true if the integer is its own id, a long without the high bit
	 */
	public static boolean isInline(BigInteger value) {
		return value.signum() >= 0 && value.bitLength() < Long.SIZE;
	}

	/**
	 * @param value an integer that is not {@link #isInline(BigInteger)}
	 * @return its provisional id, see {@link #finalId(long)}
	 */
	public long add(BigInteger value) {
		return values.add(value);
	}

	public long finalId(long provisionalId) {
		return provisionalId | SECOND_BYTE_TRUE;
	}

	public BigInteger value(long provisionalId) {
		return values.value(ProvisionalGroup.indexInGroup(provisionalId));
	}

	@Override
	public void close() {
	}

	public RoBigIntegerDict load() throws IOException {
		File file = new File(out, RoBigIntegerDict.PATH_NAME);
		if (file.exists()) {
			file.delete();
		}
		int size = values.size();
		if (size > 0) {
			long[] ids = new long[size];
			byte[][] strings = new byte[size][];
			for (int i = 0; i < size; i++) {
				ids[i] = i;
				strings[i] = values.value(i).toString().getBytes(StandardCharsets.US_ASCII);
			}
			try (ParquetTable.Writer writer = new ParquetTable.Writer(file, RoBigIntegerDict.SCHEMA,
					ParquetTable.LOOKUP_CONFIG)) {
				writer.writeBatch(b -> b.longs(RoDictionary.ID, ids).bytes(INT_LONG_VALUE, strings));
			}
		}
		return new RoBigIntegerDict(ParquetTable.open(hardwood, file));
	}
}
