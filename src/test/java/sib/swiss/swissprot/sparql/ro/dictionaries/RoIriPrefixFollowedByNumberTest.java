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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.roaringbitmap.RoaringBitmap;

import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempIriDictionary;

public class RoIriPrefixFollowedByNumberTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void basicConfigLoad() throws IOException {

		File info = folder.newFile("info");
		File bitmap = folder.newFile("bitmap");
		File temp = folder.newFile("temp");
		new TempIriDictionary(temp);

		String namespace = "http://example.org/test/";
		String prefix = "GO_";
		RoNamespace roNamespace = new RoNamespace(prefix, namespace, 0);

		List<String> infoS = Arrays.asList(new String[] {
				String.valueOf(prefix.length()), prefix, "8", "8" });
		Files.write(info.toPath(), infoS, StandardCharsets.UTF_8);
		final RoaringBitmap rb = new RoaringBitmap();
		rb.add(5);
		rb.add(10);

		try (final DataOutputStream dos = new DataOutputStream(
				new FileOutputStream(bitmap))) {
			rb.serialize(dos);
		}
		final RoNamespaces namespaces = new RoNamespaces();
		namespaces.putOrGet(0, namespace);
		final RoIriDictionary iriDictionary = new RoIriDictionary(namespaces);
		final RoIriNamespaceDictionary dict = iriDictionary
				.addPredixFollowedByBNumberDictionary(info, bitmap,
						roNamespace);

		assertEquals(namespace, dict.getNamespace());

		assertEquals(prefix + "00010", dict.getLocalNameFromId(10).get());
		assertEquals(prefix + "00005", dict.getLocalNameFromId(5).get());
		assertFalse(dict.getLocalNameFromId(11).isPresent());

		Iterator<IRI> values = dict.values().iterator();
		assertTrue(values.hasNext());
		IRI next = values.next();
		String five = namespace + prefix + "00005";
		String ten = namespace + prefix + "00010";
		assertEquals(five, next.stringValue());
		assertTrue(values.hasNext());
		next = values.next();
		assertEquals(ten, next.stringValue());
		assertFalse(values.hasNext());
	}
}
