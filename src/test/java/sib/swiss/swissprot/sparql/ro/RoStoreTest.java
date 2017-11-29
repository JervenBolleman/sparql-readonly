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
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.SailException;
import static org.junit.Assert.assertEquals;
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
        writer.endRDF();

        RoStoreFactory fact = new RoStoreFactory();
        final RoConfig config = fact.getConfig();
        config.setFile(dir.toString());
        RoStore store = fact.getSail(config);
        store.initialize();
        store.load(file);
        assertNotNull(store);

        CloseableIteration<? extends Statement, SailException> statements = store.getConnection().getStatements(null, PREDICATE, null, true);
        assertTrue(statements.hasNext());
        Statement next = statements.next();

        assertEquals(PREDICATE, next.getPredicate());
        assertEquals(SUBJECT, next.getSubject());
        assertEquals(PREDICATE, next.getObject());
        assertTrue(statements.hasNext());

        next = statements.next();
        assertEquals(PREDICATE, next.getPredicate());
        assertEquals(SUBJECT, next.getSubject());
        assertEquals(OBJECT, next.getObject());

        assertTrue(statements.hasNext());
        next = statements.next();

        assertEquals(4, store.getConnection().size());
    }
}
