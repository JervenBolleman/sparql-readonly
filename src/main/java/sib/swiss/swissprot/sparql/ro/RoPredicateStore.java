package sib.swiss.swissprot.sparql.ro;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;

public class RoPredicateStore {

	private final IRI predicate;
	private final File file;

	public RoPredicateStore(File file, IRI predicate) {
		try {
			this.file = new File(file, URLEncoder.encode(
					predicate.stringValue(), StandardCharsets.UTF_8.name()));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException();
		}

		this.predicate = predicate;

	}

	public void add(Resource subject, Value object, Resource context) {
		if (subject instanceof IRI && object instanceof IRI)
			add((IRI) subject, (IRI) object, context);
		if (subject instanceof IRI && object instanceof BNode)
			add((BNode) subject, (IRI) object, context);
	}

	private void add(IRI subject, IRI object, Resource context) {

	}

	private void add(BNode subject, IRI object, Resource context) {

	}
}
