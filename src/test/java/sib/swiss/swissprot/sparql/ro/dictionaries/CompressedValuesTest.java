package sib.swiss.swissprot.sparql.ro.dictionaries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Values written in groups are read back by their ids, which are those the
 * writer reported, and found by the hash index within their group.
 */
public class CompressedValuesTest {

    @TempDir
    Path folder;

    @Test
    public void groupsOfValues() throws IOException {
        List<List<String>> groups = new ArrayList<>();
        // more than two segments, the last batch partly filled
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 2 * CompressedValues.SEGMENT_BATCHES * CompressedValues.BATCH + 300; i++) {
            many.add(String.format("http://purl.uniprot.org/taxonomy/%07d", i));
        }
        groups.add(many);
        groups.add(List.of("only"));
        groups.add(List.of("", "", ""));
        List<String> mixed = new ArrayList<>();
        mixed.add("");
        mixed.add("é ✓ 𝄞");
        mixed.add("a".repeat(127));
        mixed.add("b".repeat(20_000));
        mixed.add("only");
        for (int i = 0; i < 1000; i++) {
            mixed.add("value " + i);
        }
        groups.add(mixed);

        File file = Files.createFile(folder.resolve("values")).toFile();
        File indexFile = new File(folder.toFile(), "index");
        long total = groups.stream().mapToLong(List::size).sum();
        Map<Long, String> reported = new HashMap<>();
        List<CompressedValues.Ids> ids = new ArrayList<>();
        List<Long> starts = new ArrayList<>();
        long[] group = new long[1];
        try (HashIndex.Writer index = new HashIndex.Writer(indexFile, total);
                CompressedValues.Writer writer = new CompressedValues.Writer(file, (value, id) -> {
                    index.add(HashIndex.hash(value, group[0]), id);
                    reported.put(id, new String(value, StandardCharsets.UTF_8));
                })) {
            for (int g = 0; g < groups.size(); g++) {
                group[0] = g;
                starts.add(writer.position());
                for (int i = 0; i < groups.get(g).size(); i++) {
                    assertEquals(i, writer.add(groups.get(g).get(i).getBytes(StandardCharsets.UTF_8)));
                }
                ids.add(writer.endGroup());
            }
            starts.add(writer.position());
        }
        assertEquals(total, reported.size());
        try (CompressedValues values = CompressedValues.open(file); HashIndex index = HashIndex.open(indexFile)) {
            assertEquals((long) starts.get(starts.size() - 1), values.size());
            for (int g = 0; g < groups.size(); g++) {
                long first = CompressedValues.idAt(starts.get(g));
                long last = CompressedValues.idAt(starts.get(g + 1));
                for (int i = 0; i < groups.get(g).size(); i++) {
                    String expected = groups.get(g).get(i);
                    long id = ids.get(g).id(i);
                    assertEquals(expected, reported.get(id));
                    assertTrue(id >= first && id < last);
                    assertEquals(expected, values.string(id));
                    byte[] bytes = expected.getBytes(StandardCharsets.UTF_8);
                    assertTrue(values.equalsAt(id, bytes));
                    long found = index.find(HashIndex.hash(bytes, g),
                            at -> at >= first && at < last && values.equalsAt(at, bytes));
                    if (!expected.isEmpty() || g != 2) {
                        assertEquals(id, found, expected);
                    }
                }
            }
            byte[] missing = "not there".getBytes(StandardCharsets.UTF_8);
            assertEquals(-1, index.find(HashIndex.hash(missing, 3), at -> values.equalsAt(at, missing)));
            assertFalse(values.equalsAt(ids.get(3).id(5), missing));
        }
    }

    @Test
    public void noFileIsEmpty() throws IOException {
        try (CompressedValues values = CompressedValues.open(new File(folder.toFile(), "none"));
                HashIndex index = HashIndex.open(new File(folder.toFile(), "none-index"))) {
            assertEquals(0, values.size());
            assertEquals(-1, index.find(1, at -> true));
        }
    }
}
