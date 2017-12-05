package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.hadoop.conf.Configuration;

public abstract class TempDictionary {

    protected final File out;
    protected final Configuration conf;

    protected TempDictionary(File out, Configuration conf) {
        super();
        this.out = out;
        this.conf = conf;
    }

    public abstract void close() throws IOException;

    protected static byte[] stringAsUtf8ByteArray(String string) {
        return string.getBytes(StandardCharsets.UTF_8);
    }
}
