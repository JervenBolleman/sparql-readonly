package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.List;
import java.util.Locale;

import org.eclipse.rdf4j.model.BNode;

import sib.swiss.swissprot.sparql.ro.ByteBuffersBackedByFilesTools;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoBnodeDictionary;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;

import com.google.common.io.Files;

public class TempBNodeDictionary extends TempDictionary {

	protected static final long SECOND_BYTE_TRUE = 1L << 63;
	private final FileOutputStream fos;

	public TempBNodeDictionary(File out) throws FileNotFoundException {
		super(out);
		fos = new FileOutputStream(new File(out, "tempbnodes"));
	}

	public void add(BNode subject) throws IOException {
		try {
			long pid = Long.parseLong(subject.getID());
			if ((SECOND_BYTE_TRUE & pid) == SECOND_BYTE_TRUE) {
				add(new RoBnode(pid));
			}
		} catch (NumberFormatException e) {
			add(subject.getID());
		}
	}

	private void add(String id) throws IOException {
		fos.write(stringAsUtf8ByteArray(id));
		fos.write('\n');
	}

	@Override
	public void close() throws IOException {
		fos.close();

	}

	public RoBnodeDictionary load() throws IOException {
		final List<String> tempNodes = Files.readLines(new File(out,
				"tempbnodes"), StandardCharsets.UTF_8);
		tempNodes.sort(Collator.getInstance(Locale.US));
		File lengthString = new File(out, "bnodes");
		long[] offsetMap = new long[tempNodes.size()];
		long pos = 0;
		try (FileOutputStream os = new FileOutputStream(lengthString)) {
			for (int i = 0; i < tempNodes.size(); i++) {
				String node = tempNodes.get(i);
				final byte[] data = stringAsUtf8ByteArray(node);
				final byte[] length = new byte[Integer.BYTES];
				ByteBuffer.wrap(length).asIntBuffer().put(data.length);
				os.write(length);
				os.write(data);
				offsetMap[i] = pos + data.length;
				pos = offsetMap[i];
			}
		}
		return new RoBnodeDictionary(offsetMap,
				ByteBuffersBackedByFilesTools.openByteBuffer(lengthString
						.toPath()));
	}
}
