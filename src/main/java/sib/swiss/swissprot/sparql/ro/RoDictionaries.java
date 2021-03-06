/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sib.swiss.swissprot.sparql.ro;

import sib.swiss.swissprot.sparql.ro.dictionaries.RoBigIntegerDict;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;

/**
 *
 * @author jbollema
 */
public class RoDictionaries {

    private final RoIriDictionary iriDict;
    private final RoLiteralDict literalDict;
    private final RoBigIntegerDict intDict;

    public RoDictionaries(RoIriDictionary iriDict, RoLiteralDict literalDict, RoBigIntegerDict intDict) {
        this.iriDict = iriDict;
        this.literalDict = literalDict;
        this.intDict = intDict;
    }

    public RoIriDictionary getIriDict() {
        return iriDict;
    }

    public RoLiteralDict getLiteralDict() {
        return literalDict;
    }

    public RoBigIntegerDict getIntDict() {
        return intDict;
    }
}
