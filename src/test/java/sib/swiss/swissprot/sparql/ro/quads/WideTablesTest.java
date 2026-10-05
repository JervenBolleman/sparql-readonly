package sib.swiss.swissprot.sparql.ro.quads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.SailConnection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import sib.swiss.swissprot.sparql.ro.RoStore;

/**
 * Which triple lists are merged into wide tables, at which overlap, and that
 * the store returns the same statements, also after reopening.
 */
public class WideTablesTest {

    private static final ValueFactory VF = SimpleValueFactory.getInstance();
    private static final String EX = "http://example.org/";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void overlapOfSortedSets() {
        assertEquals(1.0, WideTables.overlap(new long[]{1, 2, 3}, new long[]{1, 2, 3}), 0);
        assertEquals(0.5, WideTables.overlap(new long[]{1, 2}, new long[]{1, 2, 3, 4}), 0);
        assertEquals(0.0, WideTables.overlap(new long[]{1, 2}, new long[]{3, 4}), 0);
        assertEquals(1.0 / 3, WideTables.overlap(new long[]{1, 2}, new long[]{2, 3}), 1e-9);
    }

    /**
     * a: subjects 0..999; b: 0..949, 95% of a; c: 0..799, 80% of a and b;
     * d: 2000..2099, none in common; e: two objects per subject.
     */
    @Test
    public void groupsByOverlap() throws IOException {
        File data = data();
        check(data, 90, List.of(Set.of("a", "b")));
        check(data, 75, List.of(Set.of("a", "b", "c")));
        check(data, 0, List.of(Set.of("a", "b", "c", "d")));
        check(data, 101, List.of());
    }

    private File data() throws IOException {
        List<Statement> statements = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            statements.add(statement(i, "a", VF.createLiteral("a" + i)));
            if (i < 950) {
                statements.add(statement(i, "b", VF.createIRI(EX, "o" + (i % 17))));
            }
            if (i < 800) {
                statements.add(statement(i, "c", VF.createLiteral(i)));
            }
            if (i < 100) {
                statements.add(statement(2000 + i, "d", VF.createLiteral(i % 2 == 0)));
                statements.add(statement(i, "e", VF.createLiteral("one")));
                statements.add(statement(i, "e", VF.createLiteral("two")));
            }
        }
        File file = folder.newFile("data.nt");
        try (FileOutputStream out = new FileOutputStream(file)) {
            RDFWriter writer = Rio.createWriter(RDFFormat.NTRIPLES, out);
            writer.startRDF();
            statements.forEach(writer::handleStatement);
            writer.endRDF();
        }
        return file;
    }

    private static Statement statement(int subject, String predicate, org.eclipse.rdf4j.model.Value object) {
        return VF.createStatement(VF.createIRI(EX + "s/", "s" + subject), VF.createIRI(EX, predicate), object);
    }

    /**
     * Loads with the overlap, checks which lists became columns of which
     * table, and that all statements are there, before and after reopening.
     */
    private void check(File data, int overlap, List<Set<String>> expectedTables) throws IOException {
        File dir = folder.newFolder("store-" + overlap);
        RoStore store = new RoStore();
        store.setDataDir(dir);
        store.setWideTableOverlap(overlap);
        store.init();
        store.load(data);
        Set<String> loaded = all(store);
        store.shutDown();
        assertEquals(1000 + 950 + 800 + 100 + 200, loaded.size());

        List<Set<String>> tables = new ArrayList<>();
        File[] files = new File(dir, "wideTables").listFiles();
        for (File table : files == null ? new File[0] : files) {
            Set<String> members = new TreeSet<>();
            for (String predicate : List.of("a", "b", "c", "d", "e")) {
                File predicateDir = new File(dir, "predicateLists/" + URLEncoder.encode(EX + predicate, StandardCharsets.UTF_8));
                for (File list : predicateDir.listFiles((d, name) -> name.endsWith("-wide"))) {
                    File listFile = new File(predicateDir, list.getName().replace("-wide", ""));
                    if (WideTables.columnOf(listFile).get().table().getCanonicalFile().equals(table.getCanonicalFile())) {
                        members.add(predicate);
                        assertFalse("merged list keeps no file sorted by subject", listFile.exists());
                        assertTrue("merged list keeps its file sorted by object",
                                new File(predicateDir, listFile.getName() + "-by-object").exists());
                    }
                }
            }
            tables.add(members);
        }
        assertEquals("tables at overlap " + overlap, expectedTables.stream().map(TreeSet::new).toList(), tables);

        RoStore reopened = new RoStore();
        reopened.setDataDir(dir);
        reopened.init();
        try {
            assertEquals(loaded, all(reopened));
        } finally {
            reopened.shutDown();
        }
    }

    private static Set<String> all(RoStore store) {
        Set<String> all = new TreeSet<>();
        try (SailConnection connection = store.getConnection();
                CloseableIteration<? extends Statement> it = connection.getStatements(null, null, null, true)) {
            while (it.hasNext()) {
                Statement st = it.next();
                all.add(st.getSubject().stringValue() + " " + st.getPredicate() + " " + st.getObject());
            }
        }
        return all;
    }
}
