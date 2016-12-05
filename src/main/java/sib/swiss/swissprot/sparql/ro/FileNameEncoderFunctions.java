package sib.swiss.swissprot.sparql.ro;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.eclipse.rdf4j.model.IRI;

public class FileNameEncoderFunctions {

	public static String encodeNamespace(String namespace) {
		try {
			return URLEncoder.encode(namespace, StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			assert false;
			return namespace;
		}
	}

	public static String encodeIRI(IRI predicate) {
		try {
			return URLEncoder.encode(predicate.stringValue(),
					StandardCharsets.UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			assert false;
			return predicate.stringValue();
		}
	}
}
