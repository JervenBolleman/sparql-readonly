package sib.swiss.swissprot.sparql.ro.values;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.Test;

public class RoBnodeTest {

    @Test
    public void equalsAndHashCodeFollowTheBNodeContract() {
        BNode simple = SimpleValueFactory.getInstance().createBNode("42");

        assertEquals(new RoBnode(42), new RoBnode(42));
        assertNotEquals(new RoBnode(42), new RoBnode(43));
        assertEquals(new RoBnode(42), simple);
        assertEquals(simple, new RoBnode(42));
        assertEquals(simple.hashCode(), new RoBnode(42).hashCode());
        assertTrue(new HashSet<>(List.of(new RoBnode(42))).contains(simple));
        assertTrue(new HashSet<>(List.of(simple)).contains(new RoBnode(42)));
    }
}
