package sib.swiss.swissprot.sparql.temporary.dictionaries;

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
}
