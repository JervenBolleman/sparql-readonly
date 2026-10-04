package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import dev.hardwood.Hardwood;

public abstract class TempDictionary {

    protected final File out;
    protected final Hardwood hardwood;

    protected TempDictionary(File out, Hardwood hardwood) {
        super();
        this.out = out;
        this.hardwood = hardwood;
    }

    public abstract void close() throws IOException;

    protected static byte[] stringAsUtf8ByteArray(String string) {
        return string.getBytes(StandardCharsets.UTF_8);
    }
}
