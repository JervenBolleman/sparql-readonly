package sib.swiss.swissprot.sparql.ro.quads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

import dev.hardwood.Hardwood;
import dev.hardwood.reader.RowReader;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import sib.swiss.swissprot.sparql.ro.ParquetTable;

public class SortedTripleWriterTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private final Hardwood hardwood = Hardwood.create();

    @After
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
        File file = new File(folder.getRoot(), "list");
        assertTrue(file.createNewFile());
        SortedTripleWriter writer = new SortedTripleWriter(file, hardwood, false);
        assertEquals(0, writer.finish((context, row) -> {
        }));
        assertFalse(file.exists());
    }

    /**
     * Adds random triples with duplicates and contexts, then checks the list
     * holds each distinct triple once, sorted, and that the context rows
     * point at the triples of the context.
     */
    private void check(int runTriples, int triples, boolean byObject) throws IOException {
        File file = new File(folder.getRoot(), "list-" + runTriples + (byObject ? "-o" : "-s"));
        SortedTripleWriter writer = new SortedTripleWriter(file, hardwood, runTriples, byObject);
        Random random = new Random(runTriples);
        int[] order = byObject ? new int[]{1, 0, 2} : new int[]{0, 1, 2};
        TreeSet<long[]> expected = new TreeSet<>((a, b) -> {
            for (int i : order) {
                int c = Long.compare(a[i], b[i]);
                if (c != 0) {
                    return c;
                }
            }
            return 0;
        });
        for (int i = 0; i < triples; i++) {
            long subject = random.nextInt(500);
            long object = random.nextInt(50) - 25;
            int context = random.nextInt(4) - 1;
            writer.add(subject, object, context);
            expected.add(new long[]{subject, object, context});
        }
        Map<Long, Integer> contextOfRow = new HashMap<>();
        long written = writer.finish((context, row) -> contextOfRow.put(row, context));
        assertEquals(expected.size(), written);

        List<long[]> rows = new ArrayList<>();
        try (ParquetTable table = ParquetTable.open(hardwood, file);
                RowReader reader = table.rows().get()) {
            long row = 0;
            while (reader.hasNext()) {
                reader.next();
                rows.add(new long[]{reader.getLong(RoResourceRoValueList.SUBJECT),
                    reader.getLong(RoResourceRoValueList.OBJECT),
                    contextOfRow.getOrDefault(row++, SortedTripleWriter.NO_CONTEXT)});
            }
        }
        List<long[]> expectedRows = new ArrayList<>(expected);
        assertEquals(expectedRows.size(), rows.size());
        for (int i = 0; i < rows.size(); i++) {
            assertEquals("row " + i, java.util.Arrays.toString(expectedRows.get(i)), java.util.Arrays.toString(rows.get(i)));
        }
        // no runs left behind
        assertEquals(0, folder.getRoot().listFiles(f -> f.getName().startsWith(file.getName() + ".run")).length);
    }
}
