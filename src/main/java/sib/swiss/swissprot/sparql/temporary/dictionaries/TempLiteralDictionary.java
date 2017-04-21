package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;

import sib.swiss.swissprot.sparql.ro.FileNameEncoderFunctions;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;

public class TempLiteralDictionary extends TempDictionary {
	private final Map<IRI, FileOutputStream> datatypes = new HashMap<>();
	private final Map<String, FileOutputStream> languages = new HashMap<>();

	public TempLiteralDictionary(File out) {
		super(out);
	}

	public void add(Literal subject) throws IOException {
		final IRI namespace = subject.getDatatype();
		if (namespace == XMLSchema.BOOLEAN || namespace == XMLSchema.INT)
			return;

		final byte[] label = stringAsUtf8ByteArray(subject.getLabel());
		if (subject.getLanguage().isPresent()) {
			final String language = subject.getLanguage().get();
			FileOutputStream fos = languages.get(language);
			if (fos == null) {
				fos = new FileOutputStream(new File(out,
						FileNameEncoderFunctions.encodeIRI(namespace)));
				languages.put(language, fos);
			}
			fos.write(label);
			fos.write('\n');
		} else {
			FileOutputStream fos = datatypes.get(namespace);
			if (fos == null) {
				fos = new FileOutputStream(new File(out,
						FileNameEncoderFunctions.encodeIRI(namespace)));
				datatypes.put(namespace, fos);
			}
			fos.write(label);
			fos.write('\n');
		}
	}

	@Override
	public void close() throws IOException {
		for (FileOutputStream fos : datatypes.values())
			fos.close();
		for (FileOutputStream fos : languages.values())
			fos.close();
	}

	public RoLiteralDict load() {

		return null;
	}
}
