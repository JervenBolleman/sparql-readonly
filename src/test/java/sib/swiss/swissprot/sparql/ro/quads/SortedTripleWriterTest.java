package sib.swiss.swissprot.sparql.ro.quads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.TreeSet;

import dev.hardwood.Hardwood;
import dev.hardwood.reader.RowReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sib.swiss.swissprot.sparql.ro.ParquetTable;

public class SortedTripleWriterTest {

    @TempDir
    Path folder;
    private final Hardwood hardwood = Hardwood.create();

    @AfterEach
    public void after() {
        hardwood.close();
    }

    @Test
    public void inMemory() throws IOException {
        check(1_000_000, 5_000, false);
    }

    @Test
    public void runsMergedOnce() throws IOException {
        check(500, 5_000, false);
    }

    @Test
    public void byObject() throws IOException {
        check(1_000_000, 5_000, true);
        check(20, 5_000, true);
    }

    /**
     * More runs than are merged at a time: merged in two levels.
     */
    @Test
    public void runsMergedTwice() throws IOException {
        check(20, 5_000, false);
    }

    @Test
    public void emptyListHasNoFile() throws IOException {
        File file = new File(folder.toFile(), "list");
        assertTrue(file.createNewFile());
        SortedTripleWriter writer = new SortedTripleWriter(file, hardwood, false);
        assertEquals(0, writer.finish(List.of()));
        assertFalse(file.exists());
    }

    /**
     * Adds random triples with duplicates and contexts, then checks the list
     * holds each distinct triple once, sorted, with its graph columns true
     * for the contexts it was added in.
     */
    private void check(int runTriples, int triples, boolean byObject) throws IOException {
        File file = new File(folder.toFile(), "list-" + runTriples + (byObject ? "-o" : "-s"));
        SortedTripleWriter writer = new SortedTripleWriter(file, hardwood, runTriples, byObject);
        Random random = new Random(runTriples);
        Comparator<long[]> order = byObject
                ? Comparator.<long[]>comparingLong(t -> t[1]).thenComparingLong(t -> t[0])
                : Comparator.<long[]>comparingLong(t -> t[0]).thenComparingLong(t -> t[1]);
        TreeMap<long[], TreeSet<Integer>> expected = new TreeMap<>(order);
        for (int i = 0; i < triples; i++) {
            long subject = random.nextInt(500);
            long object = random.nextInt(50) - 25;
            int context = random.nextInt(4) - 1;
            writer.add(subject, object, context);
            expected.computeIfAbsent(new long[]{subject, object}, t -> new TreeSet<>()).add(context);
        }
        List<String> graphs = List.of("g_i0", "g_i1", "g_i2");
        long written = writer.finish(graphs);
        assertEquals(expected.size(), written);

        List<String> rows = new ArrayList<>();
        try (ParquetTable table = ParquetTable.open(hardwood, file);
                RowReader reader = table.rows().get()) {
            while (reader.hasNext()) {
                reader.next();
                TreeSet<Integer> in = new TreeSet<>();
                for (int g = 0; g < graphs.size(); g++) {
                    if (reader.getBoolean(graphs.get(g))) {
                        in.add(g);
                    }
                }
                if (reader.getBoolean(GraphColumns.DEFAULT)) {
                    in.add(SortedTripleWriter.NO_CONTEXT);
                }
                rows.add(reader.getLong(RoResourceRoValueList.SUBJECT) + " "
                        + reader.getLong(RoResourceRoValueList.OBJECT) + " " + in);
            }
        }
        List<String> expectedRows = new ArrayList<>();
        expected.forEach((t, in) -> expectedRows.add(t[0] + " " + t[1] + " " + in));
        assertEquals(expectedRows, rows);
        // no runs left behind
        assertEquals(0, folder.toFile().listFiles(f -> f.getName().startsWith(file.getName() + ".run")).length);
    }
}
