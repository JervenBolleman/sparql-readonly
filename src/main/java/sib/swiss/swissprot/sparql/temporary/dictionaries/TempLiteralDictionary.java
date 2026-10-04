package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.Collator;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

import dev.hardwood.Hardwood;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;

import sib.swiss.swissprot.sparql.ro.FileNameEncoderFunctions;
import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;
import static sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict.DATATYPE;
import static sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict.LABEL;
import static sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict.LANGUAGE;

public class TempLiteralDictionary extends TempDictionary {

    private static final int BATCH_SIZE = 64 * 1024;

    private final Map<IRI, FileOutputStream> datatypes = new HashMap<>();
    private final Map<String, FileOutputStream> languages = new HashMap<>();

    public TempLiteralDictionary(File out, Hardwood hardwood) {
        super(out, hardwood);
    }

    public void add(Literal subject) throws IOException {
        final IRI namespace = subject.getDatatype();
        if (namespace.equals(XSD.BOOLEAN) || namespace.equals(XSD.INT)) {
            return;
        }

        // one label per line
        final byte[] label = stringAsUtf8ByteArray(escapeLine(subject.getLabel()));
        if (subject.getLanguage().isPresent()) {
            final String language = subject.getLanguage().get();
            FileOutputStream fos = languages.get(language);
            if (fos == null) {
                fos = new FileOutputStream(languageFile(language));
                languages.put(language, fos);
            }
            fos.write(label);
            fos.write('\n');
        } else {
            FileOutputStream fos = datatypes.get(namespace);
            if (fos == null) {
                fos = new FileOutputStream(datatypeFile(namespace));
                datatypes.put(namespace, fos);
            }
            fos.write(label);
            fos.write('\n');
        }
    }

    @Override
    public void close() throws IOException {
        for (FileOutputStream fos : datatypes.values()) {
            fos.close();
        }
        for (FileOutputStream fos : languages.values()) {
            fos.close();
        }
    }

    private File languageFile(String language) {
        return new File(out, "lang-" + language);
    }

    private File datatypeFile(IRI datatype) {
        return new File(out, FileNameEncoderFunctions.encodeIRI(datatype));
    }

    public RoLiteralDict load() throws IOException {
        File file = new File(out, RoLiteralDict.PATH_NAME);
        if (file.exists()) {
            file.delete();
        }
        try (ParquetTable.Writer writer = new ParquetTable.Writer(file, RoLiteralDict.SCHEMA, ParquetTable.LOOKUP_CONFIG)) {
            LiteralBatch batch = new LiteralBatch(writer);
            writeLanguageStrings(batch);
            writeDatatypesStrings(batch);
            batch.flush();
        }
        return new RoLiteralDict(ParquetTable.open(hardwood, file));
    }

    private void writeLanguageStrings(LiteralBatch batch) throws IOException {
        for (String language : languages.keySet()) {
            File tempFile = languageFile(language);
            try (BufferedReader br = Files.newBufferedReader(tempFile.toPath())) {
                Stream<String> labels = br.lines()
                        .map(TempDictionary::unescapeLine)
                        .sorted(Collator.getInstance(Locale.forLanguageTag(language)))
                        .distinct();
                batch.addAll(labels.iterator(), RDF.LANGSTRING, language);
            }
            if (!tempFile.delete()) {
                Logger.getLogger(TempLiteralDictionary.class.getName()).warning("Can't delete lang temp file " + language);
            }
        }
    }

    private void writeDatatypesStrings(LiteralBatch batch) throws IOException {
        for (IRI datatype : datatypes.keySet()) {
            File tempFile = datatypeFile(datatype);
            try (BufferedReader br = Files.newBufferedReader(tempFile.toPath())) {
                Stream<String> labels = br.lines()
                        .map(TempDictionary::unescapeLine)
                        .sorted()
                        .distinct();
                batch.addAll(labels.iterator(), datatype, null);
            }
            if (!tempFile.delete()) {
                Logger.getLogger(TempLiteralDictionary.class.getName()).warning("Can't delete datatype temp file " + datatype);
            }
        }
    }

    /**
     * Collects literal rows, the id of a literal is its row number.
     */
    private static final class LiteralBatch {

        private final ParquetTable.Writer writer;
        private final long[] ids = new long[BATCH_SIZE];
        private final byte[][] labels = new byte[BATCH_SIZE][];
        private final byte[][] datatypes = new byte[BATCH_SIZE][];
        private final byte[][] languages = new byte[BATCH_SIZE][];
        private final boolean[] noLanguage = new boolean[BATCH_SIZE];
        private int size;
        private long index;

        private LiteralBatch(ParquetTable.Writer writer) {
            this.writer = writer;
        }

        private void addAll(Iterator<String> labels, IRI datatype, String language) throws IOException {
            final byte[] datatypeBytes = stringAsUtf8ByteArray(datatype.stringValue());
            final byte[] languageBytes = language == null ? null : stringAsUtf8ByteArray(language);
            while (labels.hasNext()) {
                if (size == BATCH_SIZE) {
                    flush();
                }
                ids[size] = index++;
                this.labels[size] = stringAsUtf8ByteArray(labels.next());
                datatypes[size] = datatypeBytes;
                languages[size] = languageBytes;
                noLanguage[size] = language == null;
                size++;
            }
        }

        private void flush() throws IOException {
            if (size == 0) {
                return;
            }
            final long[] i = Arrays.copyOf(ids, size);
            final byte[][] l = Arrays.copyOf(labels, size);
            final byte[][] d = Arrays.copyOf(datatypes, size);
            final byte[][] lang = Arrays.copyOf(languages, size);
            final boolean[] nulls = Arrays.copyOf(noLanguage, size);
            writer.writeBatch(b -> b.longs(RoDictionary.ID, i)
                    .bytes(LABEL, l)
                    .bytes(DATATYPE, d)
                    .bytes(LANGUAGE, lang, nulls));
            size = 0;
        }
    }
}
