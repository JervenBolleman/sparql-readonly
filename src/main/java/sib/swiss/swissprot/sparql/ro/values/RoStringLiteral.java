package sib.swiss.swissprot.sparql.ro.values;

import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;

/**
 * A literal of the literal dictionary read from a list of string objects.
 */
public class RoStringLiteral extends RoSimpleLiteral {

    private static final long serialVersionUID = 1L;

    public RoStringLiteral(long id, RoLiteralDict dict) {
        super(id, dict);
    }
}
