package sib.swiss.swissprot.sparql.ro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Statements come back in their graphs, the same as from RDF4J's
 * MemoryStore: from the lists and from wide tables, with triples in named
 * graphs, in the default graph, in a blank node graph and in several graphs
 * at once.
 */
@RunWith(Parameterized.class)
public class NamedGraphTest {

    @Parameters(name = "overlap {0}")
    public static List<Object[]> overlaps() {
        // 101: no wide tables; 0: every list with one object per subject
        return List.of(new Object[]{101}, new Object[]{0});
    }

    private static final ValueFactory VF = SimpleValueFactory.getInstance();
    private static final String EX = "http://example.org/";
    private static final String PREFIX = "PREFIX : <" + EX + "> ";
    private static final IRI G1 = VF.createIRI(EX, "g1");
    private static final IRI G2 = VF.createIRI(EX, "g2");

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();
    private static final Map<Integer, Repository> STORES = new HashMap<>();
    private static File data;
    private static Repository reference;
    private final int overlap;
    private Repository ro;

    public NamedGraphTest(int overlap) {
        this.overlap = overlap;
    }

    @BeforeClass
    public static void load() throws IOException {
        BNode bnodeGraph = VF.createBNode("bg");
        List<Statement> statements = new ArrayList<>();
        List<Resource> subjects = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            subjects.add(VF.createIRI(EX + "t/", "t" + i));
        }
        for (int i = 0; i < 20; i++) {
            subjects.add(VF.createBNode("b" + i));
        }
        for (int i = 0; i < subjects.size(); i++) {
            Resource s = subjects.get(i);
            statements.add(VF.createStatement(s, iri("name"), VF.createLiteral("name " + i), i % 2 == 0 ? G1 : null));
            IRI rank = i % 4 == 0 ? iri("Species") : iri("Genus");
            if (i % 3 == 0) {
                statements.add(VF.createStatement(s, iri("rank"), rank, G2));
                if (i % 5 == 0) {
                    // the same triple in a second graph
                    statements.add(VF.createStatement(s, iri("rank"), rank, G1));
                }
            } else {
                statements.add(VF.createStatement(s, iri("rank"), rank));
            }
            statements.add(VF.createStatement(s, iri("count"), VF.createLiteral(i % 10), i % 7 == 0 ? bnodeGraph : G1));
            // only in the default graph: a list without graph columns
            statements.add(VF.createStatement(s, iri("plain"), VF.createLiteral("p" + (i % 13))));
            if (i % 11 == 0) {
                statements.add(VF.createStatement(s, iri("alias"), VF.createLiteral("a" + i), G2));
                statements.add(VF.createStatement(s, iri("alias"), VF.createLiteral("b" + i)));
            }
        }
        data = folder.newFile("data.nq");
        try (FileOutputStream out = new FileOutputStream(data)) {
            RDFWriter writer = Rio.createWriter(RDFFormat.NQUADS, out);
            writer.startRDF();
            statements.forEach(writer::handleStatement);
            writer.endRDF();
        }
        reference = new SailRepository(new MemoryStore());
        try (RepositoryConnection connection = reference.getConnection()) {
            connection.add(data, RDFFormat.NQUADS);
        }
    }

    private static IRI iri(String local) {
        return VF.createIRI(EX, local);
    }

    @Before
    public void open() throws IOException {
        ro = STORES.get(overlap);
        if (ro == null) {
            RoStore store = new RoStore();
            File dir = folder.newFolder("store-" + overlap);
            store.setDataDir(dir);
            store.setWideTableOverlap(overlap);
            store.init();
            store.load(data);
            ro = new SailRepository(store);
            STORES.put(overlap, ro);
            String[] wide = new File(dir, "wideTables").list();
            assertEquals("wide tables with overlap " + overlap, overlap == 0, wide != null && wide.length > 0);
            // :rank has a triple in two graphs, which is one row
            File rank = new File(dir, "predicateLists/"
                    + java.net.URLEncoder.encode(EX + "rank", java.nio.charset.StandardCharsets.UTF_8) + "/iri_iri-wide");
            assertEquals(":rank merged with overlap " + overlap, overlap == 0, rank.exists());
        }
    }

    @AfterClass
    public static void shutDown() {
        STORES.values().forEach(Repository::shutDown);
        STORES.clear();
        reference.shutDown();
    }

    @Test
    public void statementsInTheirGraphs() {
        Resource[][] contexts = {
            {},
            {G1},
            {G2},
            {G1, G2},
            {(Resource) null},
            {null, G2},
            {iri("notAGraph")},
            {G1, iri("notAGraph")},
        };
        // every predicate in every graph, then a single predicate per set of
        // graphs: reading all statements as text is slow
        List<String> expected = statements(reference, null, new Resource[0]);
        assertTrue(expected.size() > 6000);
        assertEquals(expected, statements(ro, null, new Resource[0]));
        for (Resource[] in : contexts) {
            for (IRI predicate : new IRI[]{iri("rank"), iri("plain")}) {
                assertEquals(predicate + " in " + java.util.Arrays.toString(in), statements(reference, predicate, in),
                        statements(ro, predicate, in));
            }
        }
    }

    @Test
    public void sameAnswersAsMemoryStore() {
        String[] queries = {
            "SELECT * WHERE { ?s :name ?n ; :rank ?r }",
            "SELECT * WHERE { ?s :rank ?r ; :count ?c ; :name ?n }",
            // few enough subjects to drive the star
            "SELECT (COUNT(*) AS ?c) WHERE { ?s :rank :Species ; :name ?n }",
            // too many: merged
            "SELECT (COUNT(*) AS ?c) WHERE { ?s :rank :Genus ; :name ?n }",
            "SELECT * WHERE { GRAPH ?g { ?s :rank ?r } }",
            "SELECT * WHERE { GRAPH :g1 { ?s :name ?n ; :rank ?r } }",
            "SELECT * WHERE { GRAPH ?g { ?s :alias ?a } }",
            "SELECT * FROM :g2 WHERE { ?s :rank ?r ; :alias ?a }",
            "SELECT * FROM NAMED :g1 WHERE { GRAPH ?g { ?s :name ?n } }",
            "SELECT ?g (COUNT(*) AS ?c) WHERE { GRAPH ?g { ?s ?p ?o } } GROUP BY ?g",
            "SELECT (COUNT(*) AS ?c) WHERE { GRAPH :g1 { ?s ?p ?o } }",
            "SELECT * WHERE { GRAPH :g2 { ?s :rank :Species } }",
            "SELECT * FROM :g1 FROM :g2 WHERE { ?s :rank ?r ; :plain ?p }",
        };
        for (String query : queries) {
            assertEquals(query, answers(reference, query), answers(ro, query));
        }
    }

    /**
     * The statements as text, blank nodes made anonymous, sorted: duplicates
     * count.
     */
    private static List<String> statements(Repository repository, IRI predicate, Resource[] contexts) {
        List<String> all = new ArrayList<>();
        try (RepositoryConnection connection = repository.getConnection();
                RepositoryResult<Statement> it = connection.getStatements(null, predicate, null, false, contexts)) {
            for (Statement st : it) {
                all.add(text(st.getSubject()) + " " + st.getPredicate() + " " + text(st.getObject()) + " "
                        + text(st.getContext()));
            }
        }
        Collections.sort(all);
        return all;
    }

    private static String text(Value value) {
        if (value == null) {
            return "default";
        } else if (value instanceof Literal l) {
            return l.getLabel() + "^^" + l.getDatatype();
        } else if (value.isBNode()) {
            return "_:b";
        }
        return value.stringValue();
    }

    private static List<String> answers(Repository repository, String query) {
        List<String> rows = new ArrayList<>();
        try (RepositoryConnection connection = repository.getConnection();
                TupleQueryResult result = connection.prepareTupleQuery(PREFIX + query).evaluate()) {
            for (BindingSet bindings : result) {
                List<String> row = new ArrayList<>();
                for (String name : bindings.getBindingNames()) {
                    row.add(name + "=" + text(bindings.getValue(name)));
                }
                Collections.sort(row);
                rows.add(String.join(" ", row));
            }
        }
        Collections.sort(rows);
        return rows;
    }
}
