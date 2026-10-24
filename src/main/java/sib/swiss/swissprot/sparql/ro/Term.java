package sib.swiss.swissprot.sparql.ro;

import java.math.BigInteger;
import java.util.Optional;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.XSD;

import sib.swiss.swissprot.sparql.ro.values.RoBigIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoBooleanLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoSimpleLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoStringLiteral;
import sib.swiss.swissprot.sparql.temporary.dictionaries.TempIntegerDictionary;

/**
 * A value as it is stored in a triple list: which kind of column it is in and
 * the id there. Used to turn a value in a query into a filter on the lists.
 *
 * @param kind the kind of value, each triple list holds one kind of subject
 * and one kind of object
 * @param id the id of the value in the column for its kind
 */
public record Term(Kind kind, long id) {

    /**
     * The kinds of value, with how their id is found.
     */
    public enum Kind {
        /** Id in the IRI dictionary. */
        IRI,
        /** Id given while loading, only known for blank nodes of this store. */
        BNODE,
        /** Id in the literal dictionary. */
        LITERAL,
        /** The value of an xsd:int. */
        INT,
        /** 1 for true, 0 for false. */
        BOOLEAN,
        /**
         * An xsd:integer: the value itself, or with the high bit set its id
         * in the integer dictionary.
         */
        INTEGER
    }

    /**
     * Finds how the value is stored, in the same way loading stored it.
     *
     * @return empty if no triple of this store can have the value
     */
    public static Optional<Term> of(Value value, RoDictionaries dictionaries) {
        // values read from this store know their id
        if (value instanceof RoIri iri) {
            return Optional.of(new Term(Kind.IRI, iri.getLongId()));
        } else if (value instanceof RoBnode bnode) {
            return Optional.of(new Term(Kind.BNODE, bnode.getLongId()));
        } else if (value instanceof RoSimpleLiteral || value instanceof RoStringLiteral) {
            return Optional.of(new Term(Kind.LITERAL, ((RoLiteral) value).getLongId()));
        } else if (value instanceof RoIntegerLiteral literal) {
            return Optional.of(new Term(Kind.INT, literal.getLongId()));
        } else if (value instanceof RoBooleanLiteral literal) {
            return Optional.of(new Term(Kind.BOOLEAN, literal.getLongId()));
        } else if (value instanceof RoBigIntegerLiteral literal) {
            return Optional.of(new Term(Kind.INTEGER, literal.getLongId()));
        }
        if (value instanceof IRI iri) {
            return dictionaries.getIriDict().find(iri).map(found -> new Term(Kind.IRI, found.getLongId()));
        } else if (value instanceof BNode) {
            // the labels of blank nodes are not kept
            return Optional.empty();
        } else if (value instanceof Literal literal) {
            return ofLiteral(literal, dictionaries);
        }
        return Optional.empty();
    }

    private static Optional<Term> ofLiteral(Literal literal, RoDictionaries dictionaries) {
        IRI datatype = literal.getDatatype();
        // as LoadingHandler: a value that is not valid for its datatype is a
        // plain dictionary literal
        try {
            if (XSD.INTEGER.equals(datatype)) {
                BigInteger value = literal.integerValue();
                if (TempIntegerDictionary.isInline(value)) {
                    return Optional.of(new Term(Kind.INTEGER, value.longValue()));
                }
                return dictionaries.getIntDict().find(value).map(found -> new Term(Kind.INTEGER, found.getLongId()));
            } else if (XSD.INT.equals(datatype)) {
                return Optional.of(new Term(Kind.INT, literal.intValue()));
            } else if (XSD.BOOLEAN.equals(datatype)) {
                return Optional.of(new Term(Kind.BOOLEAN, literal.booleanValue() ? 1 : 0));
            }
        } catch (IllegalArgumentException e) {
            // NumberFormatException is one
        }
        return dictionaries.getLiteralDict().find(literal).map(found -> new Term(Kind.LITERAL, found.getLongId()));
    }
}
