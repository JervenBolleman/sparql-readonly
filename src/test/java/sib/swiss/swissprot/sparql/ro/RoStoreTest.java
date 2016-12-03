package sib.swiss.swissprot.sparql.ro;

import static org.junit.Assert.assertNotNull;

import java.nio.file.Path;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RoStoreTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	private Path dir;

	@Before
	public void init() throws Exception {
		dir = folder.newFolder().toPath();
	}

	@Test
	public void basicConfigLoad() {

		RoStoreFactory fact = new RoStoreFactory();
		final RoConfig config = fact.getConfig();
		config.setFile(dir.toString());
		RoStore store = fact.getSail(config);
		assertNotNull(store);
	}
}
