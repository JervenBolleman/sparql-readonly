package sib.swiss.swissprot.sparql.ro.dictionaries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Values written one after the other are read back at their offsets, and
 * found by the hash index, per group.
 */
public class LinearValuesTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void valuesAndIndex() throws IOException {
        List<String> values = new ArrayList<>();
        values.add("");
        values.add("é ✓ 𝄞");
        // lengths that take one, two and three bytes
        values.add("a".repeat(127));
        values.add("b".repeat(128));
        values.add("c".repeat(20_000));
        for (int i = 0; i < 100_000; i++) {
            values.add("value " + i);
        }
        File file = folder.newFile("values");
        File indexFile = new File(folder.getRoot(), "index");
        // two groups with the same values
        long[][] offsets = new long[2][values.size()];
        try (LinearValues.Writer writer = new LinearValues.Writer(file);
                HashIndex.Writer index = new HashIndex.Writer(indexFile, 2L * values.size())) {
            for (int group = 0; group < 2; group++) {
                for (int i = 0; i < values.size(); i++) {
                    byte[] bytes = values.get(i).getBytes(StandardCharsets.UTF_8);
                    offsets[group][i] = writer.add(bytes);
                    index.add(HashIndex.hash(bytes, group), offsets[group][i]);
                }
            }
        }
        try (LinearValues read = LinearValues.open(file); HashIndex index = HashIndex.open(indexFile)) {
            long groupEnd = offsets[1][0];
            for (int group = 0; group < 2; group++) {
                long start = group == 0 ? 0 : groupEnd;
                long end = group == 0 ? groupEnd : read.size();
                for (int i = 0; i < values.size(); i++) {
                    assertEquals(values.get(i), read.string(offsets[group][i]));
                    byte[] bytes = values.get(i).getBytes(StandardCharsets.UTF_8);
                    assertTrue(read.equalsAt(offsets[group][i], bytes));
                    long found = index.find(HashIndex.hash(bytes, group),
                            at -> at >= start && at < end && read.equalsAt(at, bytes));
                    assertEquals(values.get(i), offsets[group][i], found);
                }
            }
            byte[] missing = "not there".getBytes(StandardCharsets.UTF_8);
            assertEquals(-1, index.find(HashIndex.hash(missing, 0), at -> read.equalsAt(at, missing)));
            assertFalse(read.equalsAt(offsets[0][5], missing));
        }
    }

    @Test
    public void noFileIsEmpty() throws IOException {
        try (LinearValues read = LinearValues.open(new File(folder.getRoot(), "none"));
                HashIndex index = HashIndex.open(new File(folder.getRoot(), "none-index"))) {
            assertEquals(0, read.size());
            assertEquals(-1, index.find(1, at -> true));
        }
    }
}
