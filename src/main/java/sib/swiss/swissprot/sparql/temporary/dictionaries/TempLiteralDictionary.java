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

    public TempLiteralDictionary(File out, Hardwood hardwood) {
        super(out, hardwood);
    }

    /**
     * @return the provisional id of the literal, see {@link #finalId(long)}
     */
    public long add(Literal literal) {
        final Optional<String> language = literal.getLanguage();
        ProvisionalGroup<String> group = language.isPresent()
                ? byLanguage.computeIfAbsent(language.get(), l -> newGroup(literal))
                : byDatatype.computeIfAbsent(literal.getDatatype(), d -> newGroup(literal));
        return group.add(literal.getLabel());
    }

    private ProvisionalGroup<String> newGroup(Literal literal) {
        ProvisionalGroup<String> group = new ProvisionalGroup<>(groups.size());
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
        try (ParquetTable.Writer writer = new ParquetTable.Writer(file, RoLiteralDict.SCHEMA, ParquetTable.LOOKUP_CONFIG)) {
            LiteralBatch batch = new LiteralBatch(writer);
            for (ProvisionalGroup<String> group : groups) {
                Literal example = exampleOfGroup.get(group.index());
                List<String> sorted = group.sorted();
                final long first = batch.addAll(sorted, example.getDatatype(),
                        example.getLanguage().orElse(null));
                group.assignFinalIds(sorted, position -> first + position);
                group.releaseValues();
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
         * @return the id of the first of the labels
         */
        private long addAll(List<String> labels, IRI datatype, String language) throws IOException {
            final long first = index;
            final byte[] datatypeBytes = stringAsUtf8ByteArray(datatype.stringValue());
            final byte[] languageBytes = language == null ? null : stringAsUtf8ByteArray(language);
            for (String label : labels) {
                if (size == BATCH_SIZE) {
                    flush();
                }
                ids[size] = index++;
                this.labels[size] = stringAsUtf8ByteArray(label);
                datatypes[size] = datatypeBytes;
                languages[size] = languageBytes;
                noLanguage[size] = language == null;
                size++;
            }
            return first;
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
