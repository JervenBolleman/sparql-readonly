package sib.swiss.swissprot.sparql.ro.dictionaries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.roaringbitmap.RoaringBitmap;

import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempIriDictionary;

public class RoIriPrefixFollowedByNumberTest {
	private static final String NAMESPACE = "http://example.org/test/";
	private static final String PREFIX = "GO_";
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void directlyBuild() throws IOException {

		File info = folder.newFile("info");
		File bitmap = folder.newFile("bitmap");

		RoNamespace roNamespace = new RoNamespace(PREFIX, NAMESPACE, 0);

		List<String> infoS = Arrays.asList(new String[] {
				String.valueOf(PREFIX.length()), PREFIX, "8", "8" });
		Files.write(info.toPath(), infoS, StandardCharsets.UTF_8);
		final RoaringBitmap rb = new RoaringBitmap();
		rb.add(5);
		rb.add(10);

		try (final DataOutputStream dos = new DataOutputStream(
				new FileOutputStream(bitmap))) {
			rb.serialize(dos);
		}
		final RoNamespaces namespaces = new RoNamespaces();
		namespaces.putOrGet(0, NAMESPACE);

		final RoIriDictionary iriDictionary = new RoIriDictionary(namespaces);
		final RoIriNamespaceDictionary dict = iriDictionary
				.addPredixFollowedByBNumberDictionary(info, bitmap,
						roNamespace);

		assertEquals(NAMESPACE, dict.getNamespace());

		assertEquals(PREFIX + "00010", dict.getLocalNameFromId(10).get());
		assertEquals(PREFIX + "00005", dict.getLocalNameFromId(5).get());
		assertFalse(dict.getLocalNameFromId(11).isPresent());

		Iterator<IRI> values = dict.values().iterator();
		assertTrue(values.hasNext());
		IRI next = values.next();
		String five = NAMESPACE + PREFIX + "00005";
		String ten = NAMESPACE + PREFIX + "00010";
		assertEquals(five, next.stringValue());
		assertTrue(values.hasNext());
		next = values.next();
		assertEquals(ten, next.stringValue());
	}

	@Test
	public void buildViaTempDir() throws IOException {

		File temp = folder.newFolder("tempiridictionary");
		TempIriDictionary tempIriDictionary = new TempIriDictionary(temp);

		IRI sTen = SimpleValueFactory.getInstance().createIRI(NAMESPACE,
				PREFIX + "00010");
		IRI sFive = SimpleValueFactory.getInstance().createIRI(NAMESPACE,
				PREFIX + "00005");
		tempIriDictionary.add(sTen);
		tempIriDictionary.add(sFive);
		tempIriDictionary.close();
		RoIriDictionary roIriDictionary = tempIriDictionary.load();

		Iterator<IRI> values = roIriDictionary.values().iterator();
		assertTrue(values.hasNext());
		IRI next = values.next();
		String five = NAMESPACE + PREFIX + "00005";
		String ten = NAMESPACE + PREFIX + "00010";
		assertEquals(five, next.stringValue());
		assertTrue(values.hasNext());
		next = values.next();
		assertEquals(ten, next.stringValue());
		assertFalse(values.hasNext());
	}
}
