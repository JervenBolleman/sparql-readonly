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

    /**
     * Escapes a value to be stored as one line of a temp file.
     */
    protected static String escapeLine(String value) {
        if (value.indexOf('\\') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Reverses {@link #escapeLine(String)}.
     */
    protected static String unescapeLine(String line) {
        if (line.indexOf('\\') < 0) {
            return line;
        }
        StringBuilder sb = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                char next = line.charAt(++i);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    protected static byte[] stringAsUtf8ByteArray(String string) {
        return string.getBytes(StandardCharsets.UTF_8);
    }
}
