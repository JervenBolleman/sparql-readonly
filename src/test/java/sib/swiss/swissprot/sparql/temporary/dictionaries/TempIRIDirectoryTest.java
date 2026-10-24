package sib.swiss.swissprot.sparql.temporary.dictionaries;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

public class TempIRIDirectoryTest {

    @Test
    public void datadistribution() throws IOException {
        TempIriDictionary.DataDistribution dd = new TempIriDictionary.DataDistribution();

        dd.measure("GO_2");
        dd.measure("GO_5");
        assertTrue(dd.allNumericWithPrefix());

    }

    @Test
    public void differentPrefixesAreNotNumericWithPrefix() {
        TempIriDictionary.DataDistribution dd = new TempIriDictionary.DataDistribution();
        dd.measure("A_1");
        dd.measure("B_2");
        assertFalse(dd.allNumericWithPrefix());
    }

    @Test
    public void digitsBeforePrefixAreNotNumericWithPrefix() {
        TempIriDictionary.DataDistribution dd = new TempIriDictionary.DataDistribution();
        dd.measure("1");
        dd.measure("A2");
        assertFalse(dd.allNumericWithPrefix());
    }

    @Test
    public void numbersBeyondIntAreNotNumericWithPrefix() {
        TempIriDictionary.DataDistribution dd = new TempIriDictionary.DataDistribution();
        dd.measure("3110390023");
        dd.measure("3110390024");
        assertFalse(dd.allNumericWithPrefix());
    }
}
