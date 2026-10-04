package sib.swiss.swissprot.sparql.temporary.dictionaries;

import static sib.swiss.swissprot.sparql.ro.dictionaries.RoBigIntegerDict.INT_LONG_VALUE;
import static sib.swiss.swissprot.sparql.ro.dictionaries.RoDictionary.SECOND_BYTE_TRUE;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.hardwood.Hardwood;
import org.eclipse.rdf4j.model.impl.IntegerLiteral;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoBigIntegerDict;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoDictionary;

public class TempIntegerDictionary extends TempDictionary {

	private final Set<BigInteger> set = new HashSet<>();

	public TempIntegerDictionary(File out, Hardwood hardwood) {
		super(out, hardwood);
	}

	public void add(IntegerLiteral subject) throws IOException {
		add(subject.integerValue());
	}

	public void add(BigInteger value) throws IOException {
		try {
			if ((SECOND_BYTE_TRUE & value.longValueExact()) == SECOND_BYTE_TRUE) {
				set.add(value);
			}
		} catch (ArithmeticException e) {
			set.add(value);
		}
	}

	@Override
	public void close() throws IOException {
		try (ObjectOutputStream dos = new ObjectOutputStream(
				new FileOutputStream(new File(out, "temp")))) {
			dos.writeInt(set.size());
			for (BigInteger bi : set) {
				dos.writeObject(bi);
			}
		}
	}

	public RoBigIntegerDict load() throws IOException {
		File file = new File(out, RoBigIntegerDict.PATH_NAME);
		if (file.exists()) {
			file.delete();
		}
		File f = new File(out, "temp");
		if (f.exists()) {
			try (ParquetTable.Writer writer = new ParquetTable.Writer(file, RoBigIntegerDict.SCHEMA)) {
				writeIntegers(f, writer);
			} catch (ClassNotFoundException ex) {
				Logger.getLogger(TempIntegerDictionary.class.getName())
						.log(Level.SEVERE, null, ex);
			}
		}
		return new RoBigIntegerDict(ParquetTable.open(hardwood, file));
	}

	private void writeIntegers(File f, ParquetTable.Writer writer)
			throws ClassNotFoundException, IOException {
		try (ObjectInputStream in = new ObjectInputStream(
				new FileInputStream(f))) {
			int size = in.readInt();
			if (size == 0) {
				return;
			}
			long[] ids = new long[size];
			byte[][] values = new byte[size][];
			for (int i = 0; i < size; i++) {
				BigInteger read = (BigInteger) in.readObject();
				ids[i] = i;
				values[i] = read.toString().getBytes(StandardCharsets.US_ASCII);
			}
			writer.writeBatch(b -> b.longs(RoDictionary.ID, ids)
					.bytes(INT_LONG_VALUE, values));
		}
	}
}
