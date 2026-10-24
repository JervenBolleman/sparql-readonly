package sib.swiss.swissprot.sparql.temporary.dictionaries;

import static sib.swiss.swissprot.sparql.ro.dictionaries.RoBigIntegerDict.INT_LONG_VALUE;
import static sib.swiss.swissprot.sparql.ro.dictionaries.RoDictionary.SECOND_BYTE_TRUE;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;

import dev.hardwood.Hardwood;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoBigIntegerDict;

/**
 * Collects the integers that are not their own id, see
 * {@link RoBigIntegerDict}. They are stored sorted, their id is their row with
 * the high bit set.
 */
public class TempIntegerDictionary extends TempDictionary {

	private final ProvisionalGroup<BigInteger> values;

	public TempIntegerDictionary(File out, Hardwood hardwood) {
		this(out, hardwood, SpillContext.unbounded(hardwood));
	}

	public TempIntegerDictionary(File out, Hardwood hardwood, SpillContext spill) {
		super(out, hardwood);
		this.values = new ProvisionalGroup<>(0, ProvisionalGroup.BIG_INTEGERS, spill);
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
	public long add(BigInteger value) throws IOException {
		return values.add(value);
	}

	public long finalId(long provisionalId) {
		return values.finalId(provisionalId);
	}

	@Override
	public void close() {
	}

	public RoBigIntegerDict load() throws IOException {
		File file = new File(out, RoBigIntegerDict.PATH_NAME);
		if (file.exists()) {
			file.delete();
		}
		if (values.provisionalIds() > 0) {
			try (IdValueWriter writer = new IdValueWriter(file, RoBigIntegerDict.SCHEMA, INT_LONG_VALUE)) {
				values.finish(value -> writer.add(value.toString()) | SECOND_BYTE_TRUE);
			}
		} else {
			values.finish(value -> 0);
		}
		return new RoBigIntegerDict(ParquetTable.open(hardwood, file));
	}
}
