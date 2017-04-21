package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;

import sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoSimpleLiteral;

public class RoLiteralDict extends RoDictionary<RoLiteral, Literal> {

	private static IRI[] datatypes = new IRI[] { XMLSchema.ANYURI,
			XMLSchema.BASE64BINARY, XMLSchema.BOOLEAN, XMLSchema.BYTE,
			XMLSchema.DATE, XMLSchema.DATETIME, XMLSchema.DAYTIMEDURATION,
			XMLSchema.DECIMAL, XMLSchema.DOUBLE, XMLSchema.DURATION,
			XMLSchema.ENTITIES, XMLSchema.ENTITY, XMLSchema.FLOAT,
			XMLSchema.GDAY, XMLSchema.GMONTH, XMLSchema.GMONTHDAY,
			XMLSchema.GYEAR, XMLSchema.GYEARMONTH, XMLSchema.HEXBINARY,
			XMLSchema.ID, XMLSchema.IDREF, XMLSchema.IDREFS, XMLSchema.INT,
			XMLSchema.INTEGER, XMLSchema.LANGUAGE, XMLSchema.LONG,
			XMLSchema.NAME, XMLSchema.NCNAME, XMLSchema.NEGATIVE_INTEGER,
			XMLSchema.NMTOKEN, XMLSchema.NMTOKENS,
			XMLSchema.NON_NEGATIVE_INTEGER, XMLSchema.NON_POSITIVE_INTEGER,
			XMLSchema.NORMALIZEDSTRING, XMLSchema.NOTATION,
			XMLSchema.POSITIVE_INTEGER, XMLSchema.QNAME, XMLSchema.SHORT,
			XMLSchema.STRING, XMLSchema.TIME, XMLSchema.TOKEN,
			XMLSchema.UNSIGNED_BYTE, XMLSchema.UNSIGNED_INT,
			XMLSchema.UNSIGNED_LONG, XMLSchema.UNSIGNED_SHORT,
			XMLSchema.YEARMONTHDURATION };

	protected RoLiteralDict(long[] offSetMap, ByteBuffer[] buffers) {
		super(offSetMap, buffers);
	}

	@Override
	public Optional<RoLiteral> find(Literal value) {
		// TODO Auto-generated method stub
		return null;
	}

	public String stringValue(long id) {
		long offset = offSetMap[(int) id];
		try {
			byte[] bytes = ByteBuffersBackedByFilesTools.readByteArrayAt(offset,
					buffers);
			try (ObjectInputStream in = new ObjectInputStream(
					new ByteArrayInputStream(bytes))) {
				return (String) in.readObject();
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public IRI dataType(long id) {
		long offset = offSetMap[(int) id];
		try {
			byte[] bytes = ByteBuffersBackedByFilesTools.readByteArrayAt(offset,
					buffers);
			try (ObjectInputStream in = new ObjectInputStream(
					new ByteArrayInputStream(bytes))) {
				in.readObject();
				return datatypes[in.readInt()];
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public RoLiteral get(long id) {
		return new RoSimpleLiteral(id, this);
	}

	public Optional<String> language(long id) {
		long offset = offSetMap[(int) id];
		try {
			byte[] bytes = ByteBuffersBackedByFilesTools.readByteArrayAt(offset,
					buffers);
			try (ObjectInputStream in = new ObjectInputStream(
					new ByteArrayInputStream(bytes))) {
				in.readObject();
				in.readInt();
				if (in.readBoolean())
					return Optional.of((String) in.readObject());
				else
					return Optional.empty();
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static RoLiteralDict load() {
		// TODO Auto-generated method stub
		return null;
	}

}
