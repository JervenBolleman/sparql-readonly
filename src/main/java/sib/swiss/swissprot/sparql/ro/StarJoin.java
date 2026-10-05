package sib.swiss.swissprot.sparql.ro;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.rdf4j.query.algebra.AbstractQueryModelNode;
import org.eclipse.rdf4j.query.algebra.QueryModelNode;
import org.eclipse.rdf4j.query.algebra.QueryModelVisitor;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.algebra.Var;

/**
 * Statement patterns that share their subject variable, joined on it: a
 * star. Evaluated by {@link StarJoinStep}, which merges the triple lists of
 * the patterns, all sorted by subject, instead of looking up every pattern
 * for every subject.
 */
public final class StarJoin extends AbstractQueryModelNode implements TupleExpr {

    private static final long serialVersionUID = 1L;

    private String subject;
    private List<StatementPattern> patterns;

    /**
     * @param subject the name of the subject variable of all patterns
     * @param patterns at least two patterns with a constant predicate
     */
    public StarJoin(String subject, List<StatementPattern> patterns) {
        this.subject = subject;
        this.patterns = new ArrayList<>(patterns);
        for (StatementPattern pattern : this.patterns) {
            pattern.setParentNode(this);
        }
    }

    public String getSubject() {
        return subject;
    }

    public List<StatementPattern> getPatterns() {
        return patterns;
    }

    @Override
    public Set<String> getBindingNames() {
        Set<String> names = new LinkedHashSet<>();
        names.add(subject);
        for (StatementPattern pattern : patterns) {
            Var object = pattern.getObjectVar();
            if (!object.hasValue()) {
                names.add(object.getName());
            }
        }
        return names;
    }

    @Override
    public Set<String> getAssuredBindingNames() {
        return getBindingNames();
    }

    @Override
    public <X extends Exception> void visit(QueryModelVisitor<X> visitor) throws X {
        visitor.meetOther(this);
    }

    @Override
    public <X extends Exception> void visitChildren(QueryModelVisitor<X> visitor) throws X {
        for (StatementPattern pattern : patterns) {
            pattern.visit(visitor);
        }
    }

    @Override
    public void replaceChildNode(QueryModelNode current, QueryModelNode replacement) {
        int index = patterns.indexOf(current);
        if (index >= 0 && replacement instanceof StatementPattern pattern) {
            patterns.set(index, pattern);
            pattern.setParentNode(this);
        } else {
            throw new IllegalArgumentException("Not a pattern of this star: " + current);
        }
    }

    @Override
    public String getSignature() {
        return "StarJoin (?" + subject + ")";
    }

    @Override
    public StarJoin clone() {
        StarJoin clone = (StarJoin) super.clone();
        clone.patterns = new ArrayList<>();
        for (StatementPattern pattern : patterns) {
            StatementPattern copy = pattern.clone();
            copy.setParentNode(clone);
            clone.patterns.add(copy);
        }
        return clone;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StarJoin star && subject.equals(star.subject) && patterns.equals(star.patterns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, patterns);
    }
}
