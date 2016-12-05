package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class TempDictionary {
	protected final File out;

	protected TempDictionary(File out) {
		super();
		this.out = out;
	}

	public abstract void close() throws IOException;

	protected static byte[] stringAsUtf8ByteArray(String string) {
		return string.getBytes(StandardCharsets.UTF_8);
	}
}
