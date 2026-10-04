package sib.swiss.swissprot.sparql.ro;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;

import sib.swiss.swissprot.sparql.ro.StagedTriples.Kind;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempIntegerDictionary;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempIriDictionary;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempLiteralDictionary;

/**
 * Adds the values of each parsed statement to the temp dictionaries and stages
 * the statement with the ids it got. One handler parses one file: blank node
 * labels are local to their file.
 */
final class LoadingHandler extends AbstractRDFHandler {

    private static final Logger LOGGER = Logger.getLogger(LoadingHandler.class.getName());

    private final RoNamespaces prefixes;
    private final TempIriDictionary iris;
    private final TempLiteralDictionary literals;
    private final TempIntegerDictionary integers;
    private final StagedTriples staged;
    private final AtomicLong bnodeCounter;
    private final Map<String, Long> bnodes = new HashMap<>();
    private final long started = System.nanoTime();
    private long statements;

    // the kind and id of the last value given to identify(...)
    private Kind kind;
    private long id;

    LoadingHandler(RoNamespaces prefixes, TempIriDictionary iris, TempLiteralDictionary literals,
            TempIntegerDictionary integers, StagedTriples staged, AtomicLong bnodeCounter) {
        this.prefixes = prefixes;
        this.iris = iris;
        this.literals = literals;
        this.integers = integers;
        this.staged = staged;
        this.bnodeCounter = bnodeCounter;
    }

    @Override
    public void handleNamespace(String prefix, String uri) throws RDFHandlerException {
        prefixes.add(prefix, uri);
    }

    @Override
    public void handleStatement(Statement st) throws RDFHandlerException {
        identify(st.getSubject());
        Kind subjectKind = kind;
        long subject = id;
        long predicate = iris.add(st.getPredicate());
        identify(st.getObject());
        Kind objectKind = kind;
        long object = id;
        Kind contextKind = Kind.NONE;
        long context = 0;
        if (st.getContext() != null) {
            identify(st.getContext());
            contextKind = kind;
            context = id;
        }
        try {
            staged.add(subjectKind, subject, predicate, objectKind, object, contextKind, context);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (++statements % 1_000_000 == 0) {
            double seconds = (System.nanoTime() - started) / 1e9;
            LOGGER.log(Level.INFO, "{0} statements parsed, {1} per second",
                    new Object[]{statements, Math.round(statements / seconds)});
        }
    }

    /**
     * Sets {@link #kind} and {@link #id} for the value.
     */
    private void identify(Value value) {
        if (value instanceof IRI iri) {
            set(Kind.IRI, iris.add(iri));
        } else if (value instanceof BNode bnode) {
            set(Kind.BNODE, bnodes.computeIfAbsent(bnode.getID(), label -> bnodeCounter.incrementAndGet()));
        } else if (value instanceof Literal literal) {
            identify(literal);
        } else {
            throw new RDFHandlerException("Can not store " + value);
        }
    }

    private void identify(Literal literal) {
        IRI datatype = literal.getDatatype();
        // a value that is not valid for its datatype is kept as it is
        try {
            if (XSD.INTEGER.equals(datatype)) {
                BigInteger value = literal.integerValue();
                if (TempIntegerDictionary.isInline(value)) {
                    set(Kind.INTEGER, value.longValue());
                } else {
                    set(Kind.BIG_INTEGER, integers.add(value));
                }
                return;
            } else if (XSD.INT.equals(datatype)) {
                set(Kind.INT, literal.intValue());
                return;
            } else if (XSD.BOOLEAN.equals(datatype)) {
                set(Kind.BOOLEAN, literal.booleanValue() ? 1 : 0);
                return;
            }
        } catch (IllegalArgumentException e) {
            // NumberFormatException is one
        }
        set(Kind.LITERAL, literals.add(literal));
    }

    private void set(Kind kind, long id) {
        this.kind = kind;
        this.id = id;
    }
}
