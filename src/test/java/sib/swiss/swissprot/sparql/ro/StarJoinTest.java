package sib.swiss.swissprot.sparql.ro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.algebra.helpers.AbstractSimpleQueryModelVisitor;
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
 * Stars of patterns on one subject give the same answers as RDF4J's
 * MemoryStore, whichever way {@link StarJoinStep} evaluates them.
 */
public class StarJoinTest {

    private static final ValueFactory VF = SimpleValueFactory.getInstance();
    private static final String EX = "http://example.org/";
    private static final String PREFIX = "PREFIX : <" + EX + "> ";

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();
    private static Repository ro;
    private static Repository reference;

    @BeforeClass
    public static void load() throws IOException {
        Random random = new Random(11);
        List<Statement> statements = new ArrayList<>();
        List<Resource> subjects = new ArrayList<>();
        // enough that :Species and :Genus have more subjects than a pattern
        // may have to drive the star: those are merged
        for (int i = 0; i < 3500; i++) {
            subjects.add(VF.createIRI(EX + "taxon/", "t" + i));
        }
        for (int i = 0; i < 40; i++) {
            subjects.add(VF.createIRI(EX + "other#", "o" + i));
            subjects.add(VF.createBNode("b" + i));
        }
        for (Resource s : subjects) {
            if (random.nextInt(10) < 9) {
                statements.add(VF.createStatement(s, iri("name"), VF.createLiteral("name of " + s.stringValue())));
            }
            if (random.nextInt(10) < 8) {
                IRI rank = random.nextInt(100) == 0 ? iri("Rare") : random.nextBoolean() ? iri("Species") : iri("Genus");
                statements.add(VF.createStatement(s, iri("rank"), rank));
            }
            if (random.nextInt(10) < 7) {
                statements.add(VF.createStatement(s, iri("parent"), subjects.get(random.nextInt(subjects.size()))));
            }
            for (int a = random.nextInt(3); a > 0; a--) {
                statements.add(VF.createStatement(s, iri("alias"), VF.createLiteral("alias " + random.nextInt(50))));
            }
            if (random.nextBoolean()) {
                statements.add(VF.createStatement(s, iri("count"), VF.createLiteral(random.nextInt(10))));
            }
            if (random.nextBoolean()) {
                statements.add(VF.createStatement(s, iri("flag"), VF.createLiteral(random.nextBoolean())));
            }
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

    private static IRI iri(String local) {
        return VF.createIRI(EX, local);
    }

    @AfterClass
    public static void shutDown() {
        ro.shutDown();
        reference.shutDown();
    }

    @Test
    public void sameAnswersAsMemoryStore() {
        String[] queries = {
            "SELECT * WHERE { ?s :name ?n ; :rank ?r }",
            "SELECT * WHERE { ?s :name ?n ; :rank :Species ; :parent ?p }",
            "SELECT * WHERE { ?s :rank :Rare ; :name ?n }",
            "SELECT * WHERE { ?s :alias ?a ; :name ?n }",
            "SELECT * WHERE { ?s :alias ?a ; :count ?c ; :flag ?f }",
            "SELECT * WHERE { ?s :parent ?x ; :rank ?x }",
            "SELECT * WHERE { ?s :alias ?x . ?s :alias ?x }",
            "SELECT * WHERE { ?s :name ?n ; :parent ?p . ?p :name ?pn ; :rank ?pr }",
            "SELECT * WHERE { ?s :name ?n ; :rank ?r OPTIONAL { ?s :alias ?a } }",
            "SELECT * WHERE { ?s :name ?n OPTIONAL { ?s :alias ?a ; :count ?c } }",
            "SELECT * WHERE { ?s :count ?c ; :name ?n FILTER(?c > 3) }",
            "SELECT * WHERE { ?s :count ?c ; :flag true }",
            "SELECT * WHERE { VALUES ?s { :t1 :t2 :t3 <http://example.org/other#o1> } ?s :name ?n ; :rank ?r }",
            // blank nodes are ordered by their labels, which differ between stores
            "SELECT * WHERE { ?s :name ?n ; :rank ?r FILTER(isIRI(?s)) } ORDER BY ?s LIMIT 5",
            "SELECT (COUNT(*) AS ?c) WHERE { ?s :name ?n ; :rank ?r ; :parent ?p }",
            "SELECT * WHERE { ?s :name ?n ; :unknown ?x }",
            "SELECT * WHERE { ?s :rank :NotThere ; :name ?n }",
            "SELECT * WHERE { ?s :name ?n ; :rank ?r . FILTER(isBlank(?s)) }",
            "SELECT * WHERE { { ?s :name ?n ; :rank :Genus } UNION { ?s :alias ?n ; :flag false } }",
        };
        for (String query : queries) {
            List<String> expected = answers(reference, query);
            List<String> actual = answers(ro, query);
            assertEquals(query, expected, actual);
        }
    }

    @Test
    public void starsAreFound() {
        ParsedTupleQuery query = QueryParserUtil.parseTupleQuery(QueryLanguage.SPARQL,
                PREFIX + "SELECT * WHERE { ?s :name ?n ; :rank ?r ; :parent ?p . ?p :name ?pn }", null);
        new StarJoinOptimizer().optimize(query.getTupleExpr(), null, null);
        List<StarJoin> stars = new ArrayList<>();
        query.getTupleExpr().visit(new AbstractSimpleQueryModelVisitor<RuntimeException>(false) {
            @Override
            public void meetOther(org.eclipse.rdf4j.query.algebra.QueryModelNode node) {
                if (node instanceof StarJoin star) {
                    stars.add(star);
                }
            }
        });
        assertEquals(1, stars.size());
        assertEquals("s", stars.get(0).getSubject());
        assertEquals(3, stars.get(0).getPatterns().size());
        assertTrue(stars.get(0).getBindingNames().containsAll(List.of("s", "n", "r", "p")));
    }

    /**
     * The rows, each binding as text with blank nodes made anonymous,
     * sorted: duplicates count.
     */
    private static List<String> answers(Repository repository, String query) {
        List<String> rows = new ArrayList<>();
        try (RepositoryConnection connection = repository.getConnection();
                TupleQueryResult result = connection.prepareTupleQuery(PREFIX + query).evaluate()) {
            for (BindingSet bindings : result) {
                List<String> row = new ArrayList<>();
                for (String name : bindings.getBindingNames()) {
                    Value value = bindings.getValue(name);
                    String text = value instanceof Literal l
                            ? l.getLabel() + "^^" + l.getDatatype()
                            : value.isBNode() ? "_:b" : value.stringValue();
                    row.add(name + "=" + text);
                }
                Collections.sort(row);
                rows.add(String.join(" ", row));
            }
        }
        Collections.sort(rows);
        return rows;
    }
}
