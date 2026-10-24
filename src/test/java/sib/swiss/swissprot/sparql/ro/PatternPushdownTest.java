package sib.swiss.swissprot.sparql.ro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.SailConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Statement patterns with a bound subject and/or object are answered by
 * Parquet filters on the triple lists. Checks that every combination gives
 * the same statements as filtering everything in Java.
 */
public class PatternPushdownTest {

    private static final ValueFactory VF = SimpleValueFactory.getInstance();
    private static final IRI P1 = VF.createIRI("http://example.org/p1");
    private static final IRI P2 = VF.createIRI("http://example.org/p2");
    private static final IRI S1 = VF.createIRI("http://example.org/s/1");
    private static final IRI S2 = VF.createIRI("http://example.org/s/2");
    private static final IRI S3 = VF.createIRI("http://example.org/other/three");
    private static final BNode B1 = VF.createBNode("b1");
    private static final List<Value> OBJECTS = List.of(
            VF.createIRI("http://example.org/o/1"),
            S2,
            VF.createLiteral("one"),
            VF.createLiteral("een", "nl"),
            VF.createLiteral("1.5", XSD.DECIMAL),
            VF.createLiteral(7),
            VF.createLiteral(true),
            VF.createLiteral(false),
            VF.createLiteral(BigInteger.valueOf(42)),
            VF.createLiteral(new BigInteger("-99999999999999999999")));

    @TempDir
    static Path folder;
    private static RoStore store;
    private static final List<Statement> LOADED = new ArrayList<>();

    @BeforeAll
    public static void load() throws IOException {
        // every subject with every object for p1, and a few for p2
        for (Resource subject : List.of(S1, S2, S3, B1)) {
            for (Value object : OBJECTS) {
                LOADED.add(VF.createStatement(subject, P1, object));
            }
        }
        LOADED.add(VF.createStatement(S1, P2, S2));
        LOADED.add(VF.createStatement(S2, P2, VF.createLiteral(7)));
        LOADED.add(VF.createStatement(B1, P2, B1));
        File file = Files.createFile(folder.resolve("data.nt")).toFile();
        try (FileOutputStream out = new FileOutputStream(file)) {
            RDFWriter writer = Rio.createWriter(RDFFormat.NTRIPLES, out);
            writer.startRDF();
            LOADED.forEach(writer::handleStatement);
            writer.endRDF();
        }
        store = new RoStore();
        store.setDataDir(Files.createDirectory(folder.resolve("store")).toFile());
        store.init();
        store.load(file);
    }

    @AfterAll
    public static void shutDown() {
        store.shutDown();
    }

    @Test
    public void everyPatternOfPlainValues() {
        List<Resource> subjects = new ArrayList<>(List.of(S1, S2, S3, VF.createIRI("http://example.org/s/9")));
        subjects.add(null);
        List<IRI> predicates = new ArrayList<>(List.of(P1, P2, VF.createIRI("http://example.org/p9")));
        predicates.add(null);
        List<Value> objects = new ArrayList<>(OBJECTS);
        objects.add(VF.createLiteral("not loaded"));
        objects.add(VF.createLiteral(8));
        objects.add(null);
        for (Resource s : subjects) {
            for (IRI p : predicates) {
                for (Value o : objects) {
                    assertEquals(expected(s, p, o), query(s, p, o), s + " " + p + " " + o);
                }
            }
        }
    }

    /**
     * Values read from the store, as a join would bind them, carry their
     * ids: blank nodes included.
     */
    @Test
    public void everyPatternOfStoreValues() {
        List<Statement> all = statements(null, null, null);
        assertEquals(LOADED.size(), all.size());
        Set<Resource> subjects = new HashSet<>();
        Set<Value> objects = new HashSet<>();
        for (Statement st : all) {
            subjects.add(st.getSubject());
            objects.add(st.getObject());
        }
        for (Resource s : subjects) {
            for (Value o : objects) {
                Set<String> found = asStrings(statements(s, null, o));
                Set<String> expected = new HashSet<>();
                for (Statement st : all) {
                    if (st.getSubject().equals(s) && st.getObject().equals(o)) {
                        expected.add(asString(st));
                    }
                }
                assertEquals(expected, found, s + " " + o);
            }
            // a blank node subject read from the store finds its triples
            if (s.isBNode()) {
                assertEquals(OBJECTS.size() + 1, statements(s, null, null).size());
            }
        }
    }

    @Test
    public void blankNodesOfTheQueryAreNotThoseOfTheStore() {
        assertTrue(statements(B1, null, null).isEmpty());
    }

    private static Set<String> expected(Resource s, IRI p, Value o) {
        Set<String> expected = new HashSet<>();
        for (Statement st : LOADED) {
            if ((s == null || st.getSubject().equals(s)) && (p == null || st.getPredicate().equals(p))
                    && (o == null || st.getObject().equals(o))) {
                expected.add(asString(st));
            }
        }
        return expected;
    }

    private static Set<String> query(Resource s, IRI p, Value o) {
        return asStrings(statements(s, p, o));
    }

    private static List<Statement> statements(Resource s, IRI p, Value o) {
        List<Statement> found = new ArrayList<>();
        try (SailConnection connection = store.getConnection();
                CloseableIteration<? extends Statement> it = connection.getStatements(s, p, o, true)) {
            it.forEachRemaining(found::add);
        }
        return found;
    }

    private static Set<String> asStrings(List<Statement> statements) {
        Set<String> strings = new HashSet<>();
        for (Statement st : statements) {
            strings.add(asString(st));
        }
        return strings;
    }

    /**
     * Blank nodes get new labels in the store: compare them as just "a blank
     * node".
     */
    private static String asString(Statement st) {
        String subject = st.getSubject().isBNode() ? "_:b" : st.getSubject().stringValue();
        String object = st.getObject().isBNode() ? "_:b" : Objects.toString(st.getObject());
        return subject + " " + st.getPredicate() + " " + object;
    }
}
