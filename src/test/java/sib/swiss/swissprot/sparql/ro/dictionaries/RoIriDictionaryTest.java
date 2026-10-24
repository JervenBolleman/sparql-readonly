/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import dev.hardwood.Hardwood;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempIriDictionary;

/**
 *
 * @author jbollema
 */
public class RoIriDictionaryTest {

    @TempDir
    Path folder;
    private final Hardwood hardwood = Hardwood.create();

    @Test
    public void testBasic() throws IOException {
        RoNamespaces namespaces = new RoNamespaces();
        namespaces.add(RDF.PREFIX, RDF.NAMESPACE);

        TempIriDictionary temp = new TempIriDictionary(folder.toFile(), hardwood);
        temp.add(RDF.TYPE);
        temp.add(RDF.VALUE);
        temp.add(RDF.ALT);
        RoIriDictionary roIriDictionary = temp.load();
        final Optional<RoIri> foundType = roIriDictionary.find(RDF.TYPE);
        assertTrue(foundType.isPresent());
        final Optional<RoIri> foundValue = roIriDictionary.find(RDF.VALUE);
        assertTrue(foundValue.isPresent());
        final Optional<RoIri> foundAlt = roIriDictionary.find(RDF.ALT);
        assertTrue(foundValue.isPresent());
        assertEquals(RDF.TYPE, foundType.get());
        assertEquals(RDF.VALUE, foundValue.get());
        assertEquals(RDF.ALT, foundAlt.get());
    }

    /**
     * An IRI ending in '/' has an empty local name: it is stored under the
     * namespace one level up, even when its own namespace is also in use.
     */
    @Test
    public void namespaceIriNextToItsMembers() throws IOException {
        final IRI ontology = SimpleValueFactory.getInstance().createIRI("http://example.org/taxonomy/");
        final IRI member = SimpleValueFactory.getInstance().createIRI("http://example.org/taxonomy/", "9606");
        TempIriDictionary temp = new TempIriDictionary(folder.toFile(), hardwood);
        temp.add(ontology);
        temp.add(member);
        temp.add(SimpleValueFactory.getInstance().createIRI("http://example.org/taxonomy/", "10239"));
        temp.close();
        RoIriDictionary roIriDictionary = temp.load();
        assertEquals(ontology, roIriDictionary.find(ontology).get());
        assertEquals(member, roIriDictionary.find(member).get());
    }

    @Test
    public void hashCodeIsThatOfTheIriString() throws IOException {
        final IRI basic = RDF.TYPE;
        final IRI numbered = SimpleValueFactory.getInstance().createIRI("http://example.org/test/", "GO_00005");
        TempIriDictionary temp = new TempIriDictionary(folder.toFile(), hardwood);
        temp.add(basic);
        temp.add(RDF.VALUE);
        temp.add(numbered);
        temp.add(SimpleValueFactory.getInstance().createIRI("http://example.org/test/", "GO_00010"));
        temp.close();
        RoIriDictionary roIriDictionary = temp.load();
        for (IRI iri : new IRI[]{basic, numbered}) {
            final RoIri found = roIriDictionary.find(iri).get();
            assertEquals(iri.hashCode(), found.hashCode());
            // as read back from a triple list, without the original string
            final RoIri decoded = new RoIri(found.getLongId(), roIriDictionary);
            assertEquals(iri.hashCode(), decoded.hashCode());
            assertTrue(new java.util.HashSet<>(java.util.List.of(iri)).contains(decoded));
            assertTrue(new java.util.HashSet<>(java.util.List.of(decoded)).contains(iri));
        }
    }

    @AfterEach
    public void after() throws Exception {
        hardwood.close();
    }

    @Test
    public void testMany() throws IOException {
        RoNamespaces namespaces = new RoNamespaces();
        namespaces.add(RDF.PREFIX, RDF.NAMESPACE);

        TempIriDictionary temp = new TempIriDictionary(folder.toFile(), hardwood);
        for (int i = 0; i < 10_000; i++) {
            temp.add(SimpleValueFactory.getInstance().createIRI(RDF.NAMESPACE, i + "aaaa"));
        }

        RoIriDictionary roIriDictionary = temp.load();
        for (int i = 0; i < 10_000; i++) {
            final IRI test = SimpleValueFactory.getInstance().createIRI(RDF.NAMESPACE, i + "aaaa");
            final Optional<RoIri> foundType = roIriDictionary.find(test);
            assertTrue(foundType.isPresent());
            assertEquals(RDF.NAMESPACE, foundType.get().getNamespace());
            assertEquals(test.getLocalName(), foundType.get().getLocalName());
        }
    }

    @Test
    public void testManyNamespaces() throws IOException {
        RoNamespaces namespaces = new RoNamespaces();
        namespaces.add(RDF.PREFIX, RDF.NAMESPACE);

        TempIriDictionary temp = new TempIriDictionary(folder.toFile(), hardwood);
        for (int j = 0; j < 4_000; j++) {
            for (int i = 0; i < 50; i++) {
                temp.add(SimpleValueFactory.getInstance().createIRI("http://example.org/" + j + '/', i + "aaaa"));
            }
        }

        RoIriDictionary roIriDictionary = temp.load();
        for (int j = 0; j < 4_000; j++) {
            for (int i = 0; i < 50; i++) {
                final IRI test = SimpleValueFactory.getInstance().createIRI("http://example.org/" + j + '/', i + "aaaa");
                final Optional<RoIri> foundType = roIriDictionary.find(test);
                assertTrue(foundType.isPresent());
                assertEquals(test.getNamespace(), foundType.get().getNamespace());
                assertEquals(test.getLocalName(), foundType.get().getLocalName());
            }
        }
    }
}
