package sib.swiss.swissprot.sparql.ro;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RoStoreTest extends TestCase {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	protected File newFile = null;
	protected File dataDir = null;

	@Override
	@Before
	public void setUp() {
		try {
			dataDir = folder.newFolder("data.dir");

		} catch (IOException e) {
			fail();
		}
	}

	@Test
	public void basicStartTest() {
		RoStore store = new RoStore();
	}

	@Override
	@After
	public void tearDown() {

		dataDir.delete();
	}

}
