package sib.swiss.swissprot.sparql.ro;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RoStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private Path dir;
    private static final ValueFactory VF = SimpleValueFactory.getInstance();
    private static final IRI SUBJECT = VF
            .createIRI("http://example.org/subject");
    private static final IRI PREDICATE = VF
            .createIRI("http://example.org/predicate");
    private static final Literal OBJECT = VF.createLiteral(true);
    private static final Literal OBJECT2 = VF.createLiteral(1);
    private static final Literal OBJECT3 = VF.createLiteral("hello");

    @Before
    public void init() throws Exception {
        dir = folder.newFolder("readonlystore").toPath();
    }

    @After
    public void after() throws Exception {
        folder.delete();
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
        File file = folder.newFile("test.ttl");
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
        File file = folder.newFile("lang.ttl");
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
        File file = folder.newFile("integers.ttl");
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
                    assertEquals(integer.toString(), 2,
                            all.stream().filter(st -> integer.equals(st.getObject())).count());
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
        File file = folder.newFile("bnodes.ttl");
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
                    assertTrue(literal + " not in " + all.stream().map(st -> st.getObject().toString()).toList(),
                            all.stream().anyMatch(st -> literal.equals(st.getObject())));
                }
            }
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
        // 16 555 403 in taxonomy.rdf, 39 317 130 in taxonomy-hierarchy.rdf
        assertEquals(55_872_533, store.getConnection().size());
    }
}
