package sib.swiss.swissprot.sparql.ro;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.IntegerLiteral;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.RDFHandlerException;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import sib.swiss.swissprot.sparql.ro.RoPredicateStore.Builder;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoBooleanLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;
import sib.swiss.swissprot.sparql.ro.values.RoValue;

public class PredicateListBuildingHandler implements RDFHandler {

	private final RoStore roStore;
	private final Map<RoIri, RoPredicateStore.Builder> builders;
	private final File predicateListsDir;
	private final RoDictionaries dictionaries;
	private final AtomicLong bnodeCounter;
	private final Map<String, Long> bnodeTempDict = new HashMap<>();
	private final Cache<IRI, RoIri> iriCache;

	public PredicateListBuildingHandler(RoStore roStore,
			RoDictionaries dictionaries, File predicateListsDir,
			AtomicLong bnodeCounter,
			Map<RoIri, RoPredicateStore.Builder> builders) {
		this.roStore = roStore;
		this.dictionaries = dictionaries;
		this.predicateListsDir = predicateListsDir;
		this.bnodeCounter = bnodeCounter;
		this.iriCache = Caffeine.newBuilder().maximumSize(10_000).build(key -> {
			Optional<RoIri> found = dictionaries.getIriDict().find(key);
			if (found.isPresent()) {
				return found.get();
			} else {
				return null;
			}
		});
		this.builders = builders;
	}

	@Override
	public void startRDF() throws RDFHandlerException {

	}

	@Override
	public void endRDF() throws RDFHandlerException {

	}

	@Override
	public void handleNamespace(String prefix, String uri)
			throws RDFHandlerException {
		dictionaries.getIriDict().getNamespaces().add(prefix, uri);
	}

	@Override
	public void handleStatement(Statement st) throws RDFHandlerException {
		try {
			RoIri predicate = findPredicate(st);

			Builder builder = getOrInitBuilder(predicate);
			RoResource subject = find(st.getSubject()).get();
			RoValue object = find(st.getObject()).get();
			if (st.getContext() != null) {
				RoResource context = find(st.getContext()).get();
				builder.add(subject, object, context);
			} else {
				builder.add(subject, object, null);
			}
		} catch (IOException e) {
			throw new RDFHandlerException(st.toString(), e);
		}

	}

	public RoIri findPredicate(Statement st) {
		final IRI rawPredicate = st.getPredicate();
		RoIri predicate = iriCache.get(rawPredicate, (r) -> {
			Optional<RoIri> found = dictionaries.getIriDict().find(r);
			if (!found.isPresent()) {
				throw new RDFHandlerException(
						"all IRIs should be in the iriDict"
								+ st.getPredicate().stringValue());
			}
			return found.get();
		});
		return predicate;
	}

	public Builder getOrInitBuilder(RoIri predicate) throws IOException {
		Builder builder = builders.get(predicate);
		if (builder == null) {
			synchronized (builders) {
				builder = builders.get(predicate);
				if (builder == null) {
					builder = new RoPredicateStore.Builder(
							RoPredicateStore.initDirectory(predicateListsDir,
									predicate),
							predicate, dictionaries, roStore.conf);
					builders.put(predicate, builder);
				}
			}
		}
		return builder;
	}

	private Optional<? extends RoResource> find(Resource subject) {
		if (subject instanceof IRI) {
			return dictionaries.getIriDict().find((IRI) subject);
		} else if (subject instanceof BNode) {
			String id = ((BNode) subject).getID();
			if (bnodeTempDict.containsKey(id)) {
				return Optional.of(new RoBnode(bnodeTempDict.get(id)));
			} else {
				long roid = bnodeCounter.incrementAndGet();
				bnodeTempDict.put(id, roid);
				return Optional.of(new RoBnode(roid));
			}

		} else {
			return Optional.empty();
		}
	}

	private Optional<? extends RoValue> find(Value subject) {
		if (subject instanceof Resource) {
			return find((Resource) subject);
		} else {
			final Literal literal = (Literal) subject;
			if (subject instanceof IntegerLiteral) {
				return dictionaries.getIntDict().find((IntegerLiteral) subject);
			} else if (XMLSchema.INTEGER.equals(literal.getDatatype())) {
				return dictionaries.getIntDict().find(literal.integerValue());
			} else if (XMLSchema.INT.equals(literal.getDatatype())) {
				return Optional.of(new RoIntegerLiteral(literal.intValue()));
			} else if (XMLSchema.BOOLEAN.equals(literal.getDatatype())) {
				return Optional
						.of(new RoBooleanLiteral(literal.booleanValue()));
			} else {
				return dictionaries.getLiteralDict().find(literal);
			}
		}
	}

	@Override
	public void handleComment(String comment) throws RDFHandlerException {
		// TODO Auto-generated method stub

	}
}
