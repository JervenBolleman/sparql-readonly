package sib.swiss.swissprot.sparql.ro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.SailConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Loads literals into a store and checks that they come back equal, with an
 * IRI and with a blank node as subject.
 */
@ParameterizedClass(name = "{0}")
@MethodSource("literals")
public class LiteralRoundTripTest {

    private static final ValueFactory VF = SimpleValueFactory.getInstance();
    private static final IRI SUBJECT = VF.createIRI("http://example.org/subject");
    private static final BNode BNODE = VF.createBNode("b1");
    private static final IRI PREDICATE = VF.createIRI("http://example.org/predicate");

    @TempDir
    Path folder;

    public static Collection<Object[]> literals() {
        List<Object[]> literals = new ArrayList<>();
        for (Literal literal : allLiterals()) {
            literals.add(new Object[]{literal});
        }
        return literals;
    }

    static List<Literal> allLiterals() {
        return List.of(
                VF.createLiteral("hello"),
                VF.createLiteral(""),
                VF.createLiteral("ünïcödé ☃ 𝄞"),
                VF.createLiteral("tab\there"),
                VF.createLiteral("line\nbreak"),
                VF.createLiteral("carriage\rreturn"),
                VF.createLiteral("back\\slash"),
                VF.createLiteral("not a \\n newline"),
                VF.createLiteral("trailing\\"),
                VF.createLiteral("bonjour", "fr"),
                VF.createLiteral("hello", "en"),
                VF.createLiteral("hello", "en-GB"),
                VF.createLiteral(true),
                VF.createLiteral(false),
                VF.createLiteral(7),
                VF.createLiteral(-7),
                VF.createLiteral(Integer.MAX_VALUE),
                VF.createLiteral(Integer.MIN_VALUE),
                VF.createLiteral(123456789012L),
                VF.createLiteral((short) 12),
                VF.createLiteral((byte) 3),
                VF.createLiteral(BigInteger.ZERO),
                VF.createLiteral(BigInteger.valueOf(42)),
                VF.createLiteral(BigInteger.valueOf(-42)),
                VF.createLiteral(BigInteger.valueOf(Long.MAX_VALUE)),
                VF.createLiteral(BigInteger.valueOf(Long.MIN_VALUE)),
                VF.createLiteral(new BigInteger("99999999999999999999999")),
                VF.createLiteral(new BigInteger("-99999999999999999999999")),
                VF.createLiteral(new BigDecimal("1.50")),
                VF.createLiteral(1.5d),
                VF.createLiteral(1.5f),
                VF.createLiteral("2026-10-04", XSD.DATE),
                VF.createLiteral("2026-10-04T12:00:00Z", XSD.DATETIME),
                VF.createLiteral("P1D", XSD.DURATION),
                VF.createLiteral("http://example.org/", XSD.ANYURI),
                VF.createLiteral("custom", VF.createIRI("http://example.org/datatype")));
    }

    private final Literal literal;

    public LiteralRoundTripTest(Literal literal) {
        this.literal = literal;
    }

    @Test
    public void withIriSubject() throws IOException {
        load(List.of(VF.createStatement(SUBJECT, PREDICATE, literal)), back -> {
            assertEquals(1, back.size());
            Statement found = back.get(0);
            assertEquals(SUBJECT, found.getSubject());
            assertEquals(PREDICATE, found.getPredicate());
            assertLiteral((Literal) found.getObject());
        });
    }

    @Test
    public void withBnodeSubject() throws IOException {
        load(List.of(VF.createStatement(BNODE, PREDICATE, literal)), back -> {
            assertEquals(1, back.size());
            assertTrue(back.get(0).getSubject().isBNode());
            assertLiteral((Literal) back.get(0).getObject());
        });
    }

    @Test
    public void togetherWithAllOthers() throws IOException {
        List<Statement> statements = new ArrayList<>();
        for (Literal other : allLiterals()) {
            statements.add(VF.createStatement(SUBJECT, PREDICATE, other));
        }
        load(statements, back -> {
            assertEquals(statements.size(), back.size());
            assertEquals(1, back.stream().filter(st -> literal.equals(st.getObject())).count());
        });
    }

    private void assertLiteral(Literal object) {
        assertEquals(literal, object);
        assertEquals(object, literal);
        assertEquals(literal.hashCode(), object.hashCode());
        assertEquals(literal.getLabel(), object.getLabel());
        assertEquals(literal.getDatatype(), object.getDatatype());
        assertEquals(literal.getLanguage(), object.getLanguage());
    }

    /**
     * Loads the statements and hands what the store returns to the check,
     * while the store is still open: its values read the dictionaries
     * lazily.
     */
    private void load(List<Statement> statements, Consumer<List<Statement>> check) throws IOException {
        File file = Files.createFile(folder.resolve("data.nt")).toFile();
        try (FileOutputStream out = new FileOutputStream(file)) {
            RDFWriter writer = Rio.createWriter(RDFFormat.NTRIPLES, out);
            writer.startRDF();
            statements.forEach(writer::handleStatement);
            writer.endRDF();
        }
        RoStore store = new RoStore();
        store.setDataDir(Files.createDirectory(folder.resolve("store")).toFile());
        store.init();
        try {
            store.load(file);
            List<Statement> back = new ArrayList<>();
            try (SailConnection connection = store.getConnection();
                    CloseableIteration<? extends Statement> it = connection
                            .getStatements(null, PREDICATE, null, true)) {
                it.forEachRemaining(back::add);
                check.accept(back);
            }
        } finally {
            store.shutDown();
        }
    }
}
