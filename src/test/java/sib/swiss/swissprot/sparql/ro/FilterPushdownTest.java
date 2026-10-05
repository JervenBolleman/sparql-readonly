package sib.swiss.swissprot.sparql.ro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.parser.ParsedTupleQuery;
import org.eclipse.rdf4j.query.parser.QueryParserUtil;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * FILTERs pushed into the triple lists must not change any answer: every
 * query is compared with RDF4J's own MemoryStore holding the same data.
 */
public class FilterPushdownTest {

    private static final ValueFactory VF = SimpleValueFactory.getInstance();
    private static final String EX = "http://example.org/";
    private static final IRI P = VF.createIRI(EX, "p");

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();
    private static Repository ro;
    private static Repository reference;

    @BeforeClass
    public static void load() throws IOException {
        List<Value> objects = new ArrayList<>();
        for (int i = -5; i <= 20; i++) {
            objects.add(VF.createLiteral(i));
            objects.add(VF.createLiteral(BigInteger.valueOf(i * 3L)));
        }
        objects.add(VF.createLiteral(new BigInteger("99999999999999999999")));
        objects.add(VF.createLiteral(new BigInteger("-99999999999999999999")));
        objects.add(VF.createLiteral(BigInteger.valueOf(Long.MAX_VALUE)));
        objects.add(VF.createLiteral(new BigDecimal("5.5")));
        objects.add(VF.createLiteral(new BigDecimal("2.0")));
        objects.add(VF.createLiteral("7.25", XSD.DOUBLE));
        objects.add(VF.createLiteral("-3.5", XSD.FLOAT));
        objects.add(VF.createLiteral(12L));
        objects.add(VF.createLiteral(true));
        objects.add(VF.createLiteral(false));
        objects.add(VF.createLiteral("5"));
        objects.add(VF.createLiteral("abc"));
        objects.add(VF.createLiteral("abc", "en"));
        objects.add(VF.createIRI(EX, "x"));
        objects.add(VF.createBNode("b"));
        List<Statement> statements = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            statements.add(VF.createStatement(VF.createIRI(EX, "s" + i), P, objects.get(i)));
        }
        File file = folder.newFile("data.nt");
        try (FileOutputStream out = new FileOutputStream(file)) {
            RDFWriter writer = Rio.createWriter(RDFFormat.NTRIPLES, out);
            writer.startRDF();
            statements.forEach(writer::handleStatement);
            writer.endRDF();
        }
        RoStore store = new RoStore();
        store.setDataDir(folder.newFolder("store"));
        store.init();
        store.load(file);
        ro = new SailRepository(store);
        reference = new SailRepository(new MemoryStore());
        try (RepositoryConnection connection = reference.getConnection()) {
            connection.add(file, RDFFormat.NTRIPLES);
        }
    }

    @AfterClass
    public static void shutDown() {
        ro.shutDown();
        reference.shutDown();
    }

    @Test
    public void sameAnswersAsMemoryStore() {
        String[] filters = {
            "?o > 5", "?o >= 5", "?o < 5", "?o <= 5", "?o = 5", "?o != 5",
            "5 < ?o", "5 >= ?o",
            "?o > 5.5", "?o >= 5.5", "?o < -3.2e0", "?o <= 7.25e0", "?o = 2.0", "?o = 2.5",
            "?o > 5 && ?o < 10", "?o > 10 && ?o < 5", "?o > 5 || ?o = true",
            "?o > 1000000000000000000000", "?o < -1000000000000000000000",
            "?o >= 9223372036854775807",
            "?o IN (1, 3, 15)", "?o IN (1, 2.5, 5.5)", "?o IN (1, <http://example.org/x>)",
            "?o IN (99999999999999999999)", "?o NOT IN (1, 2)",
            "?o = true", "?o = false", "?o = 3 && ?o = true", "?o = \"abc\"",
            "sameTerm(?o, 7)", "isLiteral(?o) && ?o > 0",
        };
        for (String filter : filters) {
            String query = "SELECT ?s ?o WHERE { ?s <" + P + "> ?o FILTER(" + filter + ") }";
            Set<String> expected = answers(reference, query);
            Set<String> actual = answers(ro, query);
            Set<String> missing = new TreeSet<>(expected);
            missing.removeAll(actual);
            Set<String> extra = new TreeSet<>(actual);
            extra.removeAll(expected);
            assertEquals(filter + ": missing " + missing + ", extra " + extra, expected, actual);
        }
    }

    @Test
    public void filtersAreFoundForPushdown() {
        assertTrue(pushed("?o > 5").contains(new PushedFilter(BigInteger.valueOf(6), null, null, null)));
        assertTrue(pushed("?o <= 5.5").contains(new PushedFilter(null, BigInteger.valueOf(5), null, null)));
        assertTrue(pushed("?o = true").contains(new PushedFilter(null, null, null, true)));
        assertTrue(pushed("?o > 1 && ?o < 9").contains(
                new PushedFilter(BigInteger.valueOf(2), BigInteger.valueOf(8), null, null)));
        // not pushed: could match objects that are not numbers
        assertTrue(pushed("?o != 5").isEmpty());
        assertTrue(pushed("?o > 5 || ?o = true").isEmpty());
        assertTrue(pushed("?o IN (1, <http://example.org/x>)").isEmpty());
    }

    /**
     * @return the filters found for pushdown, without their integer lists,
     * which records compare by identity
     */
    private static List<PushedFilter> pushed(String filter) {
        ParsedTupleQuery query = QueryParserUtil.parseTupleQuery(QueryLanguage.SPARQL,
                "SELECT ?s ?o WHERE { ?s <" + P + "> ?o FILTER(" + filter + ") }", null);
        Map<StatementPattern, PushedFilter> pushed = new IdentityHashMap<>();
        new FilterPushdownOptimizer(pushed).optimize(query.getTupleExpr(), null, null);
        List<PushedFilter> found = new ArrayList<>();
        pushed.values().forEach(f -> found.add(new PushedFilter(f.lower(), f.upper(), null, f.bool())));
        return found;
    }

    /**
     * The answers as subject and object terms, sorted.
     */
    private static Set<String> answers(Repository repository, String query) {
        Set<String> answers = new TreeSet<>();
        try (RepositoryConnection connection = repository.getConnection();
                TupleQueryResult result = connection.prepareTupleQuery(query).evaluate()) {
            for (BindingSet bindings : result) {
                Value o = bindings.getValue("o");
                String object = o instanceof Literal l
                        ? l.getLabel() + "^^" + l.getDatatype() + l.getLanguage().map(lang -> "@" + lang).orElse("")
                        : o.isBNode() ? "_:b" : o.stringValue();
                answers.add(bindings.getValue("s").stringValue() + " " + object);
            }
        }
        return answers;
    }
}
