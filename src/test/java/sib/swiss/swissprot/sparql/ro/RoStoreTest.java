package sib.swiss.swissprot.sparql.ro;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RoStoreTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	private Path dir;
	private static final ValueFactory VF = SimpleValueFactory.getInstance();
	private static final IRI SUBJECT = VF
			.createIRI("http://example.org/subject");
	private static final IRI PREDICATE = VF
			.createIRI("http://example.org/predicate");
	private static final Literal OBJECT = VF.createLiteral(true);

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

	@Test
	public void basicTipleLoad() throws IOException {
		File file = folder.newFile();
		final RDFWriter writer = Rio.createWriter(RDFFormat.TURTLE,
				new FileOutputStream(file));
		writer.startRDF();

		writer.handleStatement(VF.createStatement(SUBJECT, PREDICATE, OBJECT));
		writer.endRDF();

		RoStoreFactory fact = new RoStoreFactory();
		final RoConfig config = fact.getConfig();
		config.setFile(dir.toString());
		RoStore store = fact.getSail(config);
                store.initialize();
		store.load(file);
		assertNotNull(store);

	}
}
