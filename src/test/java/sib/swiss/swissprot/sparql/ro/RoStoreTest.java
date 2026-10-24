package sib.swiss.swissprot.sparql.ro;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RoStoreTest {

    @TempDir
    Path folder;
    private Path dir;
    private static final ValueFactory VF = SimpleValueFactory.getInstance();
    private static final IRI SUBJECT = VF
            .createIRI("http://example.org/subject");
    private static final IRI PREDICATE = VF
            .createIRI("http://example.org/predicate");
    private static final Literal OBJECT = VF.createLiteral(true);
    private static final Literal OBJECT2 = VF.createLiteral(1);
    private static final Literal OBJECT3 = VF.createLiteral("hello");

    @BeforeEach
    public void init() throws Exception {
        dir = Files.createDirectory(folder.resolve("readonlystore")).toFile().toPath();
    }

    @Test
    public void basicConfigLoad() {
        RoStoreFactory fact = new RoStoreFactory();
        final RoConfig config = fact.getConfig();
        config.setFile(dir.toString());
        RoStore store = fact.getSail(config);
        assertNotNull(store);
    }

    @Test
    public void basicTipleLoad() throws IOException {
        File file = Files.createFile(folder.resolve("test.ttl")).toFile();
        final RDFWriter writer = Rio.createWriter(RDFFormat.TURTLE,
                new FileOutputStream(file));
        writer.startRDF();
        final Statement s1 = VF.createStatement(SUBJECT, PREDICATE, OBJECT);

        writer.handleStatement(s1);
        final Statement s2 = VF.createStatement(SUBJECT, PREDICATE, OBJECT2);
        writer.handleStatement(s2);
        final Statement s3 = VF.createStatement(SUBJECT, PREDICATE, OBJECT3);
        writer.handleStatement(s3);
        final Statement s4 = VF.createStatement(SUBJECT, PREDICATE, PREDICATE);
        writer.handleStatement(s4);
        final Statement s5 = VF.createStatement(VF.createIRI("https://www.wildflower.org/image_archive/640x480/TRG/"), PREDICATE, PREDICATE);
        writer.handleStatement(s5);

        writer.endRDF();

        RoStoreFactory fact = new RoStoreFactory();
        final RoConfig config = fact.getConfig();
        config.setFile(dir.toString());
        RoStore store = fact.getSail(config);
        store.init();
        store.load(file);
        assertNotNull(store);

        CloseableIteration<? extends Statement> statements = store.getConnection().getStatements(null, PREDICATE, null, true);
        assertEquals(5, store.getConnection().size());
        assertTrue(statements.hasNext());
        Statement next = statements.next();

        assertEquals(PREDICATE, next.getPredicate());
        assertEquals(SUBJECT, next.getSubject());
        assertEquals(PREDICATE, next.getObject());
        assertTrue(statements.hasNext());

        next = statements.next();
        assertEquals(PREDICATE, next.getPredicate());
        assertEquals(s5.getSubject(), next.getSubject());
        assertEquals(s5.getObject(), next.getObject());
        
        next = statements.next();
        assertEquals(PREDICATE, next.getPredicate());
        assertEquals(SUBJECT, next.getSubject());
        final Value o2 = next.getObject();
        assertEquals(o2, o2);
        assertTrue(o2.equals(OBJECT2));

        assertTrue(statements.hasNext());
        next = statements.next();
        assertEquals(PREDICATE, next.getPredicate());
        assertEquals(SUBJECT, next.getSubject());
        final Value o3 = next.getObject();
        assertEquals(o3, o3);
        ((Literal)o3).getDatatype();
        assertTrue(o3.equals(OBJECT3));

        assertTrue(statements.hasNext());
        next = statements.next();
        assertEquals(PREDICATE, next.getPredicate());
        assertEquals(SUBJECT, next.getSubject());
        final Value o1 = next.getObject();
        assertEquals(o1, o1);
        assertTrue(o1.equals(OBJECT));
        
        assertFalse(statements.hasNext());
    }

    @Test
    public void languageLiteralLoad() throws IOException {
        final Literal french = VF.createLiteral("bonjour", "fr");
        File file = Files.createFile(folder.resolve("lang.ttl")).toFile();
        try (FileOutputStream out = new FileOutputStream(file)) {
            final RDFWriter writer = Rio.createWriter(RDFFormat.TURTLE, out);
            writer.startRDF();
            writer.handleStatement(VF.createStatement(SUBJECT, PREDICATE, french));
            writer.endRDF();
        }

        RoStoreFactory fact = new RoStoreFactory();
        final RoConfig config = fact.getConfig();
        config.setFile(dir.toString());
        RoStore store = fact.getSail(config);
        store.init();
        try {
            store.load(file);
            try (var connection = store.getConnection();
                    CloseableIteration<? extends Statement> statements = connection
                            .getStatements(null, PREDICATE, null, true)) {
                java.util.Set<Value> objects = new java.util.HashSet<>();
                statements.forEachRemaining(st -> objects.add(st.getObject()));
                assertEquals(1, objects.size());
                assertTrue(objects.stream().anyMatch(french::equals));
            }
        } finally {
            store.shutDown();
        }
    }

    @Test
    public void integerLoad() throws IOException {
        final java.util.List<Literal> integers = java.util.List.of(
                VF.createLiteral(java.math.BigInteger.valueOf(42)),
                VF.createLiteral(java.math.BigInteger.valueOf(-5)),
                VF.createLiteral(new java.math.BigInteger("99999999999999999999999")));
        final org.eclipse.rdf4j.model.BNode bnode = VF.createBNode();
        File file = Files.createFile(folder.resolve("integers.ttl")).toFile();
        try (FileOutputStream out = new FileOutputStream(file)) {
            final RDFWriter writer = Rio.createWriter(RDFFormat.TURTLE, out);
            writer.startRDF();
            for (Literal integer : integers) {
                writer.handleStatement(VF.createStatement(SUBJECT, PREDICATE, integer));
                writer.handleStatement(VF.createStatement(bnode, PREDICATE, integer));
            }
            writer.endRDF();
        }

        RoStoreFactory fact = new RoStoreFactory();
        final RoConfig config = fact.getConfig();
        config.setFile(dir.toString());
        RoStore store = fact.getSail(config);
        store.init();
        try {
            store.load(file);
            try (var connection = store.getConnection();
                    CloseableIteration<? extends Statement> statements = connection
                            .getStatements(null, PREDICATE, null, true)) {
                java.util.List<Statement> all = new java.util.ArrayList<>();
                statements.forEachRemaining(all::add);
                assertEquals(6, all.size());
                for (Literal integer : integers) {
                    assertEquals(2,
                            all.stream().filter(st -> integer.equals(st.getObject())).count(), integer.toString());
                    assertTrue(all.stream().anyMatch(st -> st.getObject().equals(integer)));
                }
            }
        } finally {
            store.shutDown();
        }
    }

    @Test
    public void bnodeLiteralLoad() throws IOException {
        final java.util.List<Literal> literals = java.util.List.of(
                VF.createLiteral("hello"),
                VF.createLiteral("bonjour", "fr"),
                VF.createLiteral(true),
                VF.createLiteral(7),
                // the label Turtle writes for 1.5d
                VF.createLiteral("1.5E0", org.eclipse.rdf4j.model.vocabulary.XSD.DOUBLE));
        final org.eclipse.rdf4j.model.BNode bnode = VF.createBNode();
        File file = Files.createFile(folder.resolve("bnodes.ttl")).toFile();
        try (FileOutputStream out = new FileOutputStream(file)) {
            final RDFWriter writer = Rio.createWriter(RDFFormat.TURTLE, out);
            writer.startRDF();
            for (Literal literal : literals) {
                writer.handleStatement(VF.createStatement(bnode, PREDICATE, literal));
            }
            writer.endRDF();
        }

        RoStoreFactory fact = new RoStoreFactory();
        final RoConfig config = fact.getConfig();
        config.setFile(dir.toString());
        RoStore store = fact.getSail(config);
        store.init();
        try {
            store.load(file);
            try (var connection = store.getConnection();
                    CloseableIteration<? extends Statement> statements = connection
                            .getStatements(null, PREDICATE, null, true)) {
                java.util.List<Statement> all = new java.util.ArrayList<>();
                statements.forEachRemaining(all::add);
                assertEquals(literals.size(), all.size());
                for (Literal literal : literals) {
                    assertTrue(
                            all.stream().anyMatch(st -> literal.equals(st.getObject())), literal + " not in " + all.stream().map(st -> st.getObject().toString()).toList());
                }
            }
        } finally {
            store.shutDown();
        }
    }

    /**
     * With a memory budget this small the dictionaries spill and merge many
     * runs, the store must still return exactly what was loaded.
     */
    @Test
    public void loadWithSpilling() throws IOException {
        java.util.Random random = new java.util.Random(7);
        java.util.Set<Statement> expected = new java.util.HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            IRI subject = random.nextBoolean()
                    ? VF.createIRI("http://example.org/numbered/", "P" + (10_000 + random.nextInt(5_000)))
                    : VF.createIRI("http://example.org/named/", "name-" + random.nextInt(5_000));
            IRI predicate = VF.createIRI("http://example.org/p" + random.nextInt(5));
            Value object = switch (random.nextInt(5)) {
                case 0 -> VF.createIRI("http://example.org/ns" + random.nextInt(20) + "/", "o" + random.nextInt(1_000));
                case 1 -> VF.createLiteral("label " + random.nextInt(3_000));
                case 2 -> VF.createLiteral("tekst " + random.nextInt(1_000), random.nextBoolean() ? "nl" : "de");
                case 3 -> VF.createLiteral(new java.math.BigInteger("1" + "0".repeat(20)).add(java.math.BigInteger.valueOf(random.nextInt(500))));
                default -> VF.createLiteral(-1 - random.nextInt(500));
            };
            expected.add(VF.createStatement(subject, predicate, object));
        }
        File file = Files.createFile(folder.resolve("spill.nt")).toFile();
        try (FileOutputStream out = new FileOutputStream(file)) {
            final RDFWriter writer = Rio.createWriter(RDFFormat.NTRIPLES, out);
            writer.startRDF();
            expected.forEach(writer::handleStatement);
            writer.endRDF();
        }

        RoStore store = new RoStore();
        store.setDataDir(dir.toFile());
        store.setLoadMemory(20_000);
        store.init();
        try {
            store.load(file);
            java.util.Set<Statement> back = new java.util.HashSet<>();
            try (var connection = store.getConnection();
                    CloseableIteration<? extends Statement> statements = connection
                            .getStatements(null, null, null, true)) {
                statements.forEachRemaining(st -> back.add(
                        VF.createStatement(st.getSubject(), st.getPredicate(), st.getObject())));
            }
            assertEquals(expected.size(), back.size());
            assertEquals(expected, back);
        } finally {
            store.shutDown();
        }
    }

    @Test
    public void taxonomyTripleLoad() throws IOException {
        RoStoreFactory fact = new RoStoreFactory();
        final RoConfig config = fact.getConfig();
        config.setFile(dir.toString());
        RoStore store = fact.getSail(config);
        store.init();
        store.load(new File("test_data", "taxonomy.rdf"), new File("test_data", "taxonomy-hierarchy.rdf"));
        // 16 555 403 in taxonomy.rdf, 39 317 130 in taxonomy-hierarchy.rdf,
        // which repeats 2 402 179 of the direct subClassOf of taxonomy.rdf in
        // its transitive closure: the store holds each triple once
        assertEquals(53_470_354, store.getConnection().size());
    }
}
