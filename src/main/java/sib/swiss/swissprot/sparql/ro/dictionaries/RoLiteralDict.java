package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.hardwood.metadata.LogicalType;
import dev.hardwood.metadata.PhysicalType;
import dev.hardwood.metadata.RepetitionType;
import dev.hardwood.Validity;
import dev.hardwood.reader.ColumnReader;
import dev.hardwood.reader.ColumnReaders;
import dev.hardwood.reader.FilterPredicate;
import dev.hardwood.schema.FileSchema;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.util.Values;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoSimpleLiteral;

public class RoLiteralDict extends RoDictionary<RoLiteral, Literal> {

    public static final String PATH_NAME = "literals";
    public static final String LABEL = "label";
    public static final String DATATYPE = "datatype";
    public static final String LANGUAGE = "language";

    public static final FileSchema SCHEMA = FileSchema.builder(PATH_NAME)
            .addColumn(RoDictionary.ID, PhysicalType.INT64, RepetitionType.REQUIRED)
            .addColumn(LABEL, PhysicalType.BYTE_ARRAY, RepetitionType.REQUIRED, new LogicalType.StringType())
            .addColumn(DATATYPE, PhysicalType.BYTE_ARRAY, RepetitionType.REQUIRED, new LogicalType.StringType())
            .addColumn(LANGUAGE, PhysicalType.BYTE_ARRAY, RepetitionType.OPTIONAL, new LogicalType.StringType())
            .build();

    /**
     * Literals read at a time, as many as a row group holds: a literal is
     * mostly read along with others of nearby ids, the objects of one
     * subject or of subjects next to each other.
     */
    private static final int BLOCK_SIZE = 4096;
    private static final int CACHED_BLOCKS = 32;

    /**
     * The literals of a block of ids, starting at a multiple of
     * {@link #BLOCK_SIZE}.
     */
    private record Block(String[] labels, IRI[] datatypes, String[] languages) {
    }

    /**
     * The blocks read last, least recently used first.
     */
    private final Map<Long, Block> blocks = new LinkedHashMap<>(CACHED_BLOCKS * 2, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Block> eldest) {
            return size() > CACHED_BLOCKS;
        }
    };
    /**
     * Datatypes are few: one IRI each.
     */
    private final Map<String, IRI> datatypes = new ConcurrentHashMap<>();

    public RoLiteralDict(ParquetTable table) {
        super(table, LABEL);
    }

    /**
     * @return the block holding the literal, null if there is none
     */
    private Block block(long id) {
        long start = id - Math.floorMod(id, BLOCK_SIZE);
        synchronized (blocks) {
            Block block = blocks.get(start);
            if (block != null) {
                return block;
            }
        }
        Block block = read(start);
        synchronized (blocks) {
            blocks.put(start, block);
        }
        return block;
    }

    private Block read(long start) {
        String[] labels = new String[BLOCK_SIZE];
        IRI[] types = new IRI[BLOCK_SIZE];
        String[] languages = new String[BLOCK_SIZE];
        FilterPredicate inBlock = FilterPredicate.and(FilterPredicate.gtEq(ID, start),
                FilterPredicate.lt(ID, start + BLOCK_SIZE));
        Optional<ColumnReaders> batches = table.columns(inBlock, BLOCK_SIZE, ID, LABEL, DATATYPE, LANGUAGE);
        if (batches.isPresent()) {
            try (ColumnReaders rows = batches.get()) {
                while (rows.nextBatch()) {
                    long[] ids = rows.getColumnReader(ID).getLongs();
                    String[] batchLabels = rows.getColumnReader(LABEL).getStrings();
                    String[] batchTypes = rows.getColumnReader(DATATYPE).getStrings();
                    ColumnReader language = rows.getColumnReader(LANGUAGE);
                    String[] batchLanguages = language.getStrings();
                    Validity present = language.getLeafValidity();
                    for (int i = 0; i < ids.length; i++) {
                        int at = (int) (ids[i] - start);
                        labels[at] = batchLabels[i];
                        types[at] = datatypes.computeIfAbsent(batchTypes[i], Values::iri);
                        if (present.isNotNull(i) && batchLanguages[i] != null && !batchLanguages[i].isEmpty()) {
                            languages[at] = batchLanguages[i];
                        }
                    }
                }
            }
        }
        return new Block(labels, types, languages);
    }

    @Override
    public Optional<RoLiteral> find(Literal value) {
        final String label = value.stringValue();
        final Optional<String> language = value.getLanguage();
        final FilterPredicate filter;
        if (language.isPresent()) {
            filter = FilterPredicate.and(FilterPredicate.eq(LABEL, label),
                    FilterPredicate.eq(LANGUAGE, language.get()));
        } else {
            filter = FilterPredicate.and(FilterPredicate.eq(LABEL, label),
                    FilterPredicate.eq(DATATYPE, value.getDatatype().stringValue()),
                    FilterPredicate.isNull(LANGUAGE));
        }
        return findId(filter).map(id -> new RoSimpleLiteral(id, this));
    }

    public String stringValue(long id) {
        String label = block(id).labels()[Math.floorMod(id, BLOCK_SIZE)];
        if (label == null) {
            Logger.getLogger(RoLiteralDict.class.getName()).log(Level.SEVERE, "No literal at {0}", id);
            return "";
        }
        return label;
    }

    public IRI dataType(long id) {
        IRI datatype = block(id).datatypes()[Math.floorMod(id, BLOCK_SIZE)];
        if (datatype == null) {
            Logger.getLogger(RoLiteralDict.class.getName()).log(Level.SEVERE, "No literal at {0}", id);
        }
        return datatype;
    }

    public RoLiteral get(long id) {
        return new RoSimpleLiteral(id, this);
    }

    public Optional<String> language(long id) {
        return Optional.ofNullable(block(id).languages()[Math.floorMod(id, BLOCK_SIZE)]);
    }
}
