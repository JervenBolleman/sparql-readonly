/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempIriDictionary;

/**
 *
 * @author jbollema
 */
public class RoIriDictionaryTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testBasic() throws IOException {
        RoNamespaces namespaces = new RoNamespaces();
        namespaces.add(RDF.PREFIX, RDF.NAMESPACE);

        TempIriDictionary temp = new TempIriDictionary(folder.newFolder());
        temp.add(RDF.TYPE);
        temp.add(RDF.VALUE);
        RoIriDictionary roIriDictionary = temp.load();
        final Optional<RoIri> foundType = roIriDictionary.find(RDF.TYPE);
        assertTrue(foundType.isPresent());
        final Optional<RoIri> foundValue = roIriDictionary.find(RDF.VALUE);
        assertTrue(foundValue.isPresent());
        assertEquals(RDF.TYPE, foundType.get());
        assertEquals(0L, foundType.get().getLongId());
        assertEquals(1L, foundValue.get().getLongId());
        assertEquals(RDF.VALUE, foundValue.get());
    }
}
