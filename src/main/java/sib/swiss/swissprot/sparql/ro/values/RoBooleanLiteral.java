package sib.swiss.swissprot.sparql.ro.values;

import org.eclipse.rdf4j.model.impl.BooleanLiteral;

public class RoBooleanLiteral extends BooleanLiteral implements RoLiteral {

    private static final long serialVersionUID = 1L;

    public RoBooleanLiteral(boolean value) {
        super(value);
    }

    @Override
    public long getLongId() {
        if (booleanValue()) {
            return 1;
        } else {
            return 0;
        }
    }
}
