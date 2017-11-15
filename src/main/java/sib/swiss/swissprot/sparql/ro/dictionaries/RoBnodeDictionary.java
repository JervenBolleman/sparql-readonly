package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import org.apache.orc.Reader;

import org.eclipse.rdf4j.model.BNode;

import sib.swiss.swissprot.sparql.ro.values.RoBnode;

public class RoBnodeDictionary extends RoDictionary<RoBnode, BNode> {

    public static String PATH_NAME = "bnodes";

    public RoBnodeDictionary(Reader reader) {
        super(reader);
    }

    public String getFromId(long id) throws IOException {
        if ((SECOND_BYTE_TRUE & id) == SECOND_BYTE_TRUE) {
            int withoutMask = (int) id;;
            return readStringAt(withoutMask);
        } else {
            return String.valueOf(id);
        }

    }

    @Override
    public Optional<RoBnode> find(BNode subject) {
        return null;
    }
}
