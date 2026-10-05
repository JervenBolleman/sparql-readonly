package sib.swiss.swissprot.sparql.ro.quads;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.hardwood.Hardwood;
import dev.hardwood.metadata.PhysicalType;
import dev.hardwood.metadata.RepetitionType;
import dev.hardwood.reader.ColumnReaders;
import dev.hardwood.schema.FileSchema;

import sib.swiss.swissprot.sparql.ro.ParquetTable;

/**
 * Merges triple lists that have one object per subject, and largely the same
 * subjects, into one wide table: a subject column and one column of objects
 * per list, null where a subject has no object in that list. The subject
 * column, otherwise stored once per list, is stored once, and a star of
 * patterns over these lists reads rows that are already joined.
 *
 * <p>
 * A merged list keeps its file sorted by object. Its file sorted by subject
 * is replaced by a pointer, {@code <list>-wide}, holding the path of the
 * table relative to the list's directory and the name of its column.
 *
 * <p>
 * The lists may be in different named graphs: the graph columns of a list,
 * see {@link GraphColumns}, are copied to the table prefixed with its object
 * column, so that each list keeps its own.
 */
public final class WideTables {

    private static final Logger LOGGER = Logger.getLogger(WideTables.class.getName());
    private static final String WIDE_SUFFIX = "-wide";
    private static final int BATCH_SIZE = 64 * 1024;

    private WideTables() {
    }

    /**
     * Where a list's triples sorted by subject are in a wide table.
     *
     * @param table the wide table
     * @param column the object column of the list in it
     */
    public record Column(File table, String column) {
    }

    /**
     * @return the column of the list in a wide table, if it was merged
     */
    static Optional<Column> columnOf(File list) throws IOException {
        File pointer = pointerFile(list);
        if (!pointer.exists()) {
            return Optional.empty();
        }
        String[] line = Files.readString(pointer.toPath(), StandardCharsets.UTF_8).trim().split("\t");
        // normalized, so the columns of one table name the same file
        File table = list.getParentFile().toPath().resolve(line[0]).normalize().toAbsolutePath().toFile();
        return Optional.of(new Column(table, line[1]));
    }

    private static File pointerFile(File list) {
        return new File(list.getParentFile(), list.getName() + WIDE_SUFFIX);
    }

    /**
     * A list that may be merged: one object per subject.
     */
    private record Candidate(File list, boolean iriSubjects, long[] subjects, List<String> graphs) {
    }

    /**
     * Merges the lists of the store that qualify into wide tables.
     *
     * @param predicateLists the directory of the predicates' directories
     * @param wideTables where the tables are written
     * @param overlapPercent the least overlap, in percent, between the
     * subjects of a list and those of a table for the list to join it:
     * {@code |A ∩ B| / |A ∪ B|}. 0 merges all lists with one object per
     * subject and the same kind of subject, whatever their subjects. Above
     * 100 merges none.
     * @return the number of tables written
     */
    public static int build(File predicateLists, File wideTables, Hardwood hardwood, int overlapPercent)
            throws IOException {
        if (overlapPercent > 100) {
            return 0;
        }
        List<Candidate> candidates = candidates(predicateLists, hardwood);
        candidates.sort(Comparator.comparingInt((Candidate c) -> c.subjects().length).reversed());
        List<List<Candidate>> groups = new ArrayList<>();
        List<long[]> groupSubjects = new ArrayList<>();
        for (Candidate candidate : candidates) {
            int best = -1;
            double bestOverlap = -1;
            for (int g = 0; g < groups.size(); g++) {
                if (groups.get(g).get(0).iriSubjects() != candidate.iriSubjects()) {
                    continue;
                }
                double overlap = overlap(candidate.subjects(), groupSubjects.get(g));
                if (overlap * 100 >= overlapPercent && overlap > bestOverlap) {
                    best = g;
                    bestOverlap = overlap;
                }
            }
            if (best < 0) {
                groups.add(new ArrayList<>(List.of(candidate)));
                groupSubjects.add(candidate.subjects());
            } else {
                groups.get(best).add(candidate);
                groupSubjects.set(best, union(groupSubjects.get(best), candidate.subjects()));
            }
        }
        int written = 0;
        for (int g = 0; g < groups.size(); g++) {
            List<Candidate> group = groups.get(g);
            if (group.size() < 2) {
                continue;
            }
            Files.createDirectories(wideTables.toPath());
            File table = new File(wideTables, "table-" + written);
            write(table, group, groupSubjects.get(g), hardwood);
            for (int c = 0; c < group.size(); c++) {
                File list = group.get(c).list();
                String relative = list.getParentFile().toPath().relativize(table.toPath()).toString();
                Files.writeString(pointerFile(list).toPath(), relative + "\t" + column(c) + "\n",
                        StandardCharsets.UTF_8);
                Files.delete(list.toPath());
            }
            LOGGER.log(Level.INFO, "Merged {0} triple lists into {1}, {2} subjects",
                    new Object[]{group.size(), table.getName(), groupSubjects.get(g).length});
            written++;
        }
        return written;
    }

    private static String column(int index) {
        return "p" + index;
    }

    /**
     * The lists sorted by subject that have one object per subject, with
     * their subjects.
     */
    private static List<Candidate> candidates(File predicateLists, Hardwood hardwood) throws IOException {
        List<Candidate> candidates = new ArrayList<>();
        File[] predicates = predicateLists.listFiles(File::isDirectory);
        if (predicates == null) {
            return candidates;
        }
        Arrays.sort(predicates);
        for (File predicate : predicates) {
            File[] lists = predicate.listFiles((dir, name) -> (name.startsWith("iri_") || name.startsWith("bnode_"))
                    && !name.contains("-"));
            if (lists == null) {
                continue;
            }
            Arrays.sort(lists);
            for (File list : lists) {
                if (pointerFile(list).exists()) {
                    continue;
                }
                long[] subjects = distinctSubjects(list, hardwood);
                if (subjects != null) {
                    List<String> graphs;
                    try (ParquetTable table = ParquetTable.open(hardwood, list)) {
                        graphs = GraphColumns.in(table, "");
                    }
                    candidates.add(new Candidate(list, list.getName().startsWith("iri_"), subjects, graphs));
                }
            }
        }
        return candidates;
    }

    /**
     * @return the subjects of a list sorted by subject, null if a subject
     * has more than one object
     */
    private static long[] distinctSubjects(File list, Hardwood hardwood) throws IOException {
        try (ParquetTable table = ParquetTable.open(hardwood, list)) {
            long rows = table.numberOfRows();
            if (rows == 0 || rows > Integer.MAX_VALUE) {
                return null;
            }
            long[] subjects = new long[(int) rows];
            int size = 0;
            Optional<ColumnReaders> batches = table.columns(BATCH_SIZE, RoResourceRoValueList.SUBJECT);
            try (ColumnReaders readers = batches.get()) {
                while (readers.nextBatch()) {
                    for (long subject : readers.getColumnReader(0).getLongs()) {
                        if (size > 0 && subjects[size - 1] == subject) {
                            return null;
                        }
                        subjects[size++] = subject;
                    }
                }
            }
            return subjects;
        }
    }

    /**
     * |a ∩ b| / |a ∪ b| of two sorted sets.
     */
    static double overlap(long[] a, long[] b) {
        int i = 0;
        int j = 0;
        long both = 0;
        while (i < a.length && j < b.length) {
            int c = Long.compare(a[i], b[j]);
            if (c == 0) {
                both++;
                i++;
                j++;
            } else if (c < 0) {
                i++;
            } else {
                j++;
            }
        }
        long either = (long) a.length + b.length - both;
        return either == 0 ? 1 : (double) both / either;
    }

    private static long[] union(long[] a, long[] b) {
        long[] union = new long[a.length + b.length];
        int i = 0;
        int j = 0;
        int size = 0;
        while (i < a.length || j < b.length) {
            long next;
            if (j == b.length || (i < a.length && a[i] < b[j])) {
                next = a[i++];
            } else if (i == a.length || b[j] < a[i]) {
                next = b[j++];
            } else {
                next = a[i++];
                j++;
            }
            union[size++] = next;
        }
        return Arrays.copyOf(union, size);
    }

    /**
     * Writes the table: a row per subject of the group, with the object of
     * every list or null.
     */
    private static void write(File table, List<Candidate> group, long[] subjects, Hardwood hardwood)
            throws IOException {
        FileSchema.Builder schema = FileSchema.builder("wide")
                .addColumn(RoResourceRoValueList.SUBJECT, PhysicalType.INT64, RepetitionType.REQUIRED);
        for (int c = 0; c < group.size(); c++) {
            schema.addColumn(column(c), PhysicalType.INT64, RepetitionType.OPTIONAL);
            for (String graph : group.get(c).graphs()) {
                schema.addColumn(GraphColumns.prefix(column(c)) + graph, PhysicalType.BOOLEAN,
                        RepetitionType.REQUIRED);
            }
        }
        int columns = group.size();
        List<ListReader> readers = new ArrayList<>();
        try (ParquetTable.Writer writer = new ParquetTable.Writer(table, schema.build(), ParquetTable.LIST_CONFIG)) {
            for (Candidate candidate : group) {
                readers.add(new ListReader(candidate.list(), candidate.graphs(), hardwood));
            }
            for (int start = 0; start < subjects.length; start += BATCH_SIZE) {
                int size = Math.min(BATCH_SIZE, subjects.length - start);
                long[] batchSubjects = Arrays.copyOfRange(subjects, start, start + size);
                long[][] objects = new long[columns][size];
                boolean[][] nulls = new boolean[columns][size];
                boolean[][][] inGraph = new boolean[columns][][];
                for (int c = 0; c < columns; c++) {
                    ListReader reader = readers.get(c);
                    inGraph[c] = new boolean[group.get(c).graphs().size()][size];
                    for (int r = 0; r < size; r++) {
                        if (reader.at(batchSubjects[r])) {
                            objects[c][r] = reader.object();
                            for (int g = 0; g < inGraph[c].length; g++) {
                                inGraph[c][g][r] = reader.inGraph(g);
                            }
                        } else {
                            nulls[c][r] = true;
                        }
                    }
                }
                writer.writeBatch(b -> {
                    b.longs(RoResourceRoValueList.SUBJECT, batchSubjects);
                    for (int c = 0; c < columns; c++) {
                        b.longs(column(c), objects[c], nulls[c]);
                        List<String> graphs = group.get(c).graphs();
                        for (int g = 0; g < graphs.size(); g++) {
                            b.booleans(GraphColumns.prefix(column(c)) + graphs.get(g), inGraph[c][g]);
                        }
                    }
                });
            }
        } finally {
            for (ListReader reader : readers) {
                reader.close();
            }
        }
    }

    /**
     * Reads a list sorted by subject along with the subjects of its table.
     */
    private static final class ListReader implements AutoCloseable {

        private final ParquetTable table;
        private final ColumnReaders batches;
        private long[] subjects = new long[0];
        private long[] objects = new long[0];
        private final List<String> graphs;
        private boolean[][] inGraph;
        private int next;

        private ListReader(File list, List<String> graphs, Hardwood hardwood) throws IOException {
            this.table = ParquetTable.open(hardwood, list);
            this.graphs = graphs;
            List<String> columns = new ArrayList<>(List.of(RoResourceRoValueList.SUBJECT, RoResourceRoValueList.OBJECT));
            columns.addAll(graphs);
            this.batches = table.columns(BATCH_SIZE, columns.toArray(String[]::new)).orElseThrow();
        }

        /**
         * @return true if the list has the subject, which is at or after the
         * last one asked for
         */
        boolean at(long subject) {
            while (true) {
                while (next < subjects.length && subjects[next] < subject) {
                    next++;
                }
                if (next < subjects.length) {
                    return subjects[next] == subject;
                }
                if (!batches.nextBatch()) {
                    return false;
                }
                subjects = batches.getColumnReader(RoResourceRoValueList.SUBJECT).getLongs();
                objects = batches.getColumnReader(RoResourceRoValueList.OBJECT).getLongs();
                inGraph = new boolean[graphs.size()][];
                for (int g = 0; g < inGraph.length; g++) {
                    inGraph[g] = batches.getColumnReader(graphs.get(g)).getBooleans();
                }
                next = 0;
            }
        }

        long object() {
            return objects[next];
        }

        /**
         * @return whether the triple of the subject is in the g-th graph of
         * the list
         */
        boolean inGraph(int g) {
            return inGraph[g][next];
        }

        @Override
        public void close() throws IOException {
            batches.close();
            table.close();
        }
    }
}
