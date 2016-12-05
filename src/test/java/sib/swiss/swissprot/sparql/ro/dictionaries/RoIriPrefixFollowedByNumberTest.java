package sib.swiss.swissprot.sparql.ro.dictionaries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.roaringbitmap.RoaringBitmap;

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
		final RoIriPrefixFollowedByNumber dict = new RoIriPrefixFollowedByNumber(
				info, bitmap, namespace, 1);

		assertEquals(namespace, dict.getNamespace());

		assertEquals(prefix + "00010", dict.getLocalNameFromId(10).get());
		assertEquals(prefix + "00005", dict.getLocalNameFromId(5).get());
		assertFalse(dict.getLocalNameFromId(11).isPresent());
	}
}
