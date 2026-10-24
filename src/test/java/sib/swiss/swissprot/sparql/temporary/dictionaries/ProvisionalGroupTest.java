package sib.swiss.swissprot.sparql.temporary.dictionaries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

import dev.hardwood.Hardwood;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProvisionalGroupTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private final Hardwood hardwood = Hardwood.create();

    @After
    public void after() {
        hardwood.close();
    }

    @Test
    public void inMemory() throws IOException {
        try (SpillContext context = SpillContext.unbounded(hardwood)) {
            check(context, 10_000, 3_000);
            assertEquals(0, context.runFilesWritten());
        }
    }

    @Test
    public void spillsAndMergesInTheBackground() throws IOException {
        try (SpillContext context = new SpillContext(4_000, folder.newFolder(), hardwood, true)) {
            check(context, 20_000, 5_000);
            // more runs than one merge takes, so merging happened
            assertTrue(context.runFilesWritten() > ProvisionalGroup.MERGE_FAN_IN);
        }
    }

    @Test
    public void spillsAndMergesWhenFinishing() throws IOException {
        try (SpillContext context = new SpillContext(4_000, folder.newFolder(), hardwood, false)) {
            check(context, 20_000, 5_000);
            assertTrue(context.runFilesWritten() > ProvisionalGroup.MERGE_FAN_IN);
        }
    }

    @Test
    public void groupsShareTheBudget() throws IOException {
        try (SpillContext context = new SpillContext(4_000, folder.newFolder(), hardwood, true)) {
            ProvisionalGroup<String> first = new ProvisionalGroup<>(0, ProvisionalGroup.STRINGS, context);
            ProvisionalGroup<String> second = new ProvisionalGroup<>(1, ProvisionalGroup.STRINGS, context);
            Map<Long, String> added = new HashMap<>();
            Random random = new Random(3);
            for (int i = 0; i < 10_000; i++) {
                String value = "v" + random.nextInt(2_000);
                ProvisionalGroup<String> group = random.nextBoolean() ? first : second;
                added.put(group.add(value), value);
            }
            for (ProvisionalGroup<String> group : List.of(first, second)) {
                List<String> distinct = new ArrayList<>();
                group.finish(value -> {
                    distinct.add(value);
                    return distinct.size() - 1;
                });
                added.forEach((provisional, value) -> {
                    if (ProvisionalGroup.group(provisional) == group.index()) {
                        assertEquals(value, distinct.get((int) group.finalId(provisional)));
                    }
                });
            }
        }
    }

    @Test
    public void bigIntegersInNumericOrder() throws IOException {
        try (SpillContext context = new SpillContext(500, folder.newFolder(), hardwood, true)) {
            ProvisionalGroup<BigInteger> group = new ProvisionalGroup<>(0, ProvisionalGroup.BIG_INTEGERS, context);
            List<BigInteger> values = new ArrayList<>();
            for (int i = -100; i < 100; i++) {
                values.add(BigInteger.valueOf(i).multiply(new BigInteger("100000000000000000000")));
            }
            java.util.Collections.shuffle(values, new Random(5));
            Map<Long, BigInteger> added = new HashMap<>();
            for (BigInteger value : values) {
                added.put(group.add(value), value);
                added.put(group.add(value), value);
            }
            List<BigInteger> distinct = new ArrayList<>();
            group.finish(value -> {
                distinct.add(value);
                return distinct.size() - 1;
            });
            assertEquals(new ArrayList<>(new TreeSet<>(values)), distinct);
            added.forEach((provisional, value) -> assertEquals(value, distinct.get((int) group.finalId(provisional))));
        }
    }

    /**
     * Adds random values with many repeats, then checks that finishing gives
     * each distinct value once and in order, and that every provisional id
     * maps to the final id of its value.
     */
    private void check(SpillContext context, int adds, int distinctValues) throws IOException {
        ProvisionalGroup<String> group = new ProvisionalGroup<>(0, ProvisionalGroup.STRINGS, context);
        Random random = new Random(1);
        Map<Long, String> added = new HashMap<>();
        TreeSet<String> expected = new TreeSet<>();
        for (int i = 0; i < adds; i++) {
            String value = "value-" + random.nextInt(distinctValues);
            added.put(group.add(value), value);
            expected.add(value);
        }
        List<String> distinct = new ArrayList<>();
        group.finish(value -> {
            distinct.add(value);
            return 1000 + distinct.size() - 1;
        });
        assertEquals(new ArrayList<>(expected), distinct);
        for (Map.Entry<Long, String> entry : added.entrySet()) {
            assertEquals(entry.getValue(), distinct.get((int) (group.finalId(entry.getKey()) - 1000)));
        }
    }
}
