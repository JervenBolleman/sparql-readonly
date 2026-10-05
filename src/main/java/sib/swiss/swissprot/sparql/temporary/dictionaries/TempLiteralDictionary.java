package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.hardwood.Hardwood;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;
import static sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict.DATATYPE;
import static sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict.LABEL;
import static sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict.LANGUAGE;

public class TempLiteralDictionary extends TempDictionary {

    private static final int BATCH_SIZE = 64 * 1024;

    /**
     * Literals with a language are grouped per language, the others per
     * datatype.
     */
    private final Map<String, ProvisionalGroup<String>> byLanguage = new HashMap<>();
    private final Map<IRI, ProvisionalGroup<String>> byDatatype = new HashMap<>();
    private final List<ProvisionalGroup<String>> groups = new ArrayList<>();
    private final List<Literal> exampleOfGroup = new ArrayList<>();
    private final SpillContext spill;

    public TempLiteralDictionary(File out, Hardwood hardwood) {
        this(out, hardwood, SpillContext.unbounded(hardwood));
    }

    public TempLiteralDictionary(File out, Hardwood hardwood, SpillContext spill) {
        super(out, hardwood);
        this.spill = spill;
    }

    /**
     * @return the provisional id of the literal, see {@link #finalId(long)}
     */
    public long add(Literal literal) throws IOException {
        final Optional<String> language = literal.getLanguage();
        ProvisionalGroup<String> group = language.isPresent()
                ? byLanguage.computeIfAbsent(language.get(), l -> newGroup(literal))
                : byDatatype.computeIfAbsent(literal.getDatatype(), d -> newGroup(literal));
        return group.add(literal.getLabel());
    }

    private ProvisionalGroup<String> newGroup(Literal literal) {
        ProvisionalGroup<String> group = new ProvisionalGroup<>(groups.size(), ProvisionalGroup.STRINGS, spill);
        groups.add(group);
        exampleOfGroup.add(literal);
        return group;
    }

    /**
     * @return the id in the dictionary {@link #load()} wrote, of the literal
     * that got this provisional id
     */
    public long finalId(long provisionalId) {
        return groups.get(ProvisionalGroup.group(provisionalId)).finalId(provisionalId);
    }

    @Override
    public void close() {
    }

    /**
     * Writes all literals into one dictionary, sorted per group. The id of a
     * literal is its row.
     */
    public RoLiteralDict load() throws IOException {
        File file = new File(out, RoLiteralDict.PATH_NAME);
        if (file.exists()) {
            file.delete();
        }
        if (groups.isEmpty()) {
            // without literals there is no file
            return new RoLiteralDict(ParquetTable.open(hardwood, file));
        }
        try (ParquetTable.Writer writer = new ParquetTable.Writer(file, RoLiteralDict.SCHEMA, ParquetTable.LOOKUP_CONFIG)) {
            LiteralBatch batch = new LiteralBatch(writer);
            for (ProvisionalGroup<String> group : groups) {
                Literal example = exampleOfGroup.get(group.index());
                final byte[] datatype = stringAsUtf8ByteArray(example.getDatatype().stringValue());
                final String language = example.getLanguage().orElse(null);
                final byte[] languageBytes = language == null ? null : stringAsUtf8ByteArray(language);
                group.finish(label -> batch.add(label, datatype, languageBytes));
            }
            batch.flush();
        }
        return new RoLiteralDict(ParquetTable.open(hardwood, file));
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

        /**
         * @param language null for a literal without
         * @return the id of the literal
         */
        private long add(String label, byte[] datatype, byte[] language) throws IOException {
            if (size == BATCH_SIZE) {
                flush();
            }
            ids[size] = index;
            labels[size] = stringAsUtf8ByteArray(label);
            datatypes[size] = datatype;
            languages[size] = language;
            noLanguage[size] = language == null;
            size++;
            return index++;
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
