package sib.swiss.swissprot.sparql.ro;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import dev.hardwood.Hardwood;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import sib.swiss.swissprot.sparql.ro.quads.BnodeBigIntegerList;
import sib.swiss.swissprot.sparql.ro.quads.BnodeBnodeList;
import sib.swiss.swissprot.sparql.ro.quads.BnodeBooleanList;
import sib.swiss.swissprot.sparql.ro.quads.BnodeIntegerList;
import sib.swiss.swissprot.sparql.ro.quads.BnodeIriList;
import sib.swiss.swissprot.sparql.ro.quads.BnodeLiteralList;
import sib.swiss.swissprot.sparql.ro.quads.BnodeStringLiteralList;
import sib.swiss.swissprot.sparql.ro.quads.IriBigIntegerList;
import sib.swiss.swissprot.sparql.ro.quads.IriBnodeList;
import sib.swiss.swissprot.sparql.ro.quads.IriBooleanList;
import sib.swiss.swissprot.sparql.ro.quads.IriIntegerLiteralList;
import sib.swiss.swissprot.sparql.ro.quads.IriIriList;
import sib.swiss.swissprot.sparql.ro.quads.IriLiteralList;
import sib.swiss.swissprot.sparql.ro.quads.RoResourceRoValueList;
import sib.swiss.swissprot.sparql.ro.values.RoBigIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoBooleanLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoResource;
import sib.swiss.swissprot.sparql.ro.values.RoStringLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoValue;

public class RoPredicateStore implements Iterable<Statement>, Closeable {

    private final RoIri predicate;

    private final IriIriList iriIriList;

    private final IriBnodeList iriBnodeList;
    private final IriIntegerLiteralList iriIntegerList;
    private final IriLiteralList iriLiteralList;
    private final IriBooleanList iriBooleanList;
    private final IriBigIntegerList iriBigIntegerList;

    private final BnodeIriList bNodeIriList;
    private final BnodeBnodeList bNodeBnodeList;
    private final BnodeIntegerList bNodeIntList;
    private final BnodeBooleanList bNodeBooleanList;
    private final BnodeBigIntegerList bNodeBigIntegerList;
    private final BnodeStringLiteralList bNodeStringList;
    private final BnodeLiteralList bNodeLiteralList;

    private static final String FILE_TO_STORE_PREDICATE_ID = "predicate";

    private static final String FILE_TO_STORE_IRI_BNODE = "iri_bnode";
    private static final String FILE_TO_STORE_IRI_IRI = "iri_iri";
    private static final String FILE_TO_STORE_IRI_INTEGER = "iri_integer";
    private static final String FILE_TO_STORE_IRI_BOOLEAN = "iri_boolean";
    private static final String FILE_TO_STORE_IRI_LITERAL = "iri_literal";
    private static final String FILE_TO_STORE_IRI_BIG_INTEGER = "iri_big_integer";

    private static final String FILE_TO_STORE_BNODE_BNODE = "bnode_bnode";
    private static final String FILE_TO_STORE_BNODE_IRI = "bnode_iri";
    private static final String FILE_TO_STORE_BNODE_INTEGER = "bnode_integer";
    private static final String FILE_TO_STORE_BNODE_BOOLEAN = "bnode_boolean";
    private static final String FILE_TO_STORE_BNODE_STRING = "bnode_string";
    private static final String FILE_TO_STORE_BNODE_LITERAL = "bnode_literal";
    private static final String FILE_TO_STORE_BNODE_BIG_INTEGER = "bnode_big_integer";
    

    public RoPredicateStore(File directory, RoDictionaries dictionaries, Hardwood hardwood)
            throws IOException {

        try (DataInputStream fr = new DataInputStream(new FileInputStream(
                new File(directory, FILE_TO_STORE_PREDICATE_ID)))) {
            predicate = new RoIri(fr.readLong(), dictionaries.getIriDict());
        }

        this.iriIriList = new IriIriList(new File(directory, FILE_TO_STORE_IRI_IRI),
                predicate, dictionaries, hardwood);
        this.iriBnodeList = new IriBnodeList(new File(directory, FILE_TO_STORE_IRI_BNODE),
                predicate, dictionaries, hardwood);
        this.iriIntegerList = new IriIntegerLiteralList(
                new File(directory, FILE_TO_STORE_IRI_INTEGER), predicate, dictionaries, hardwood);
        this.iriLiteralList = new IriLiteralList(
                new File(directory, FILE_TO_STORE_IRI_LITERAL), predicate, dictionaries, hardwood);
        this.iriBooleanList = new IriBooleanList(
                new File(directory, FILE_TO_STORE_IRI_BOOLEAN), predicate, dictionaries, hardwood);

        this.bNodeIriList = new BnodeIriList(new File(directory, FILE_TO_STORE_BNODE_IRI),
                predicate, dictionaries, hardwood);
        this.bNodeBnodeList = new BnodeBnodeList(
                new File(directory, FILE_TO_STORE_BNODE_BNODE), predicate, dictionaries, hardwood);
        this.bNodeIntList = new BnodeIntegerList(
                new File(directory, FILE_TO_STORE_BNODE_INTEGER), predicate, dictionaries, hardwood);
        this.bNodeBooleanList = new BnodeBooleanList(
                new File(directory, FILE_TO_STORE_BNODE_BOOLEAN), predicate, dictionaries, hardwood);
        this.iriBigIntegerList = new IriBigIntegerList(
                new File(directory, FILE_TO_STORE_IRI_BIG_INTEGER), predicate, dictionaries, hardwood);
        this.bNodeBigIntegerList = new BnodeBigIntegerList(
                new File(directory, FILE_TO_STORE_BNODE_BIG_INTEGER), predicate, dictionaries, hardwood);
        this.bNodeStringList = new BnodeStringLiteralList(
                new File(directory, FILE_TO_STORE_BNODE_STRING), predicate, dictionaries, hardwood);
        this.bNodeLiteralList = new BnodeLiteralList(
                new File(directory, FILE_TO_STORE_BNODE_LITERAL), predicate, dictionaries, hardwood);
    }

    private RoPredicateStore(RoIri predicate,
            BnodeIriList bNodeIriList,
            BnodeBnodeList bnodeBnodeList,
            BnodeIntegerList bNodeIntList,
            BnodeBooleanList bNodeBooleanList,
            IriBnodeList iriBnodeList,
            IriIriList iriIriList,
            IriIntegerLiteralList iriIntegerList,
            IriLiteralList iriLiteralList,
            IriBooleanList iriBooleanList,
            IriBigIntegerList iriBigIntegerList,
            BnodeBigIntegerList bNodeBigIntegerList,
            BnodeStringLiteralList bNodeStringList,
            BnodeLiteralList bNodeLiteralList
    )
            throws IOException {
        this.bNodeIriList = bNodeIriList;
        
        this.bNodeBnodeList = bnodeBnodeList;
        this.bNodeBooleanList = bNodeBooleanList;
        this.bNodeIntList = bNodeIntList;
        this.iriIriList = iriIriList;
        this.iriBnodeList = iriBnodeList;
        this.iriIntegerList = iriIntegerList;
        this.iriLiteralList = iriLiteralList;
        this.iriBooleanList = iriBooleanList;
        this.iriBigIntegerList = iriBigIntegerList;
        this.bNodeBigIntegerList = bNodeBigIntegerList;
        this.bNodeStringList = bNodeStringList;
        this.bNodeLiteralList = bNodeLiteralList;
        
        this.predicate = predicate;
    }

    public static File initDirectory(File directory, IRI predicate) {
        File file = null;
        try {
            file = new File(directory, URLEncoder.encode(
                    predicate.stringValue(), StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        if (!file.exists()) {
            file.mkdir();
        }
        return file;
    }

    @Override
    public Iterator<Statement> iterator() {
        return stream().iterator();
    }

    public Stream<Statement> stream() {
        return Stream.of(
                bNodeBnodeList, 
                bNodeIriList, 
                bNodeIntList, 
                bNodeBooleanList, 
                iriBnodeList, 
                iriIriList, 
                iriIntegerList, 
                iriLiteralList,
                iriBooleanList,
                iriBigIntegerList,
                bNodeBigIntegerList,
                bNodeStringList,
                bNodeLiteralList)
                .flatMap(RoResourceRoValueList::stream);
    }

    @Override
    public void close() throws IOException {
        for (RoResourceRoValueList<?, ?> list : List.of(bNodeBnodeList, bNodeIriList,
                bNodeIntList, bNodeBooleanList, iriBnodeList, iriIriList,
                iriIntegerList, iriLiteralList, iriBooleanList,
                iriBigIntegerList, bNodeBigIntegerList, bNodeStringList,
                bNodeLiteralList)) {
            list.close();
        }
    }

    public static class Builder {

        private final IriIriList.Builder iriIriBuilder;
        private final File directory;
        private final RoIri predicate;
        private final BnodeBnodeList.Builder bNodeBnodeBuilder;
        private final BnodeIriList.Builder bNodeIriBuilder;
        private final BnodeBooleanList.Builder bNodeBooleanBuilder;
        private final BnodeIntegerList.Builder bNodeIntegerBuilder;
        private final BnodeStringLiteralList.Builder bnodeStringBuilder;
        private final BnodeLiteralList.Builder bnodeLiteralBuilder;

        private final IriBnodeList.Builder iriBnodeBuilder;
        private final IriIntegerLiteralList.Builder iriIntegerBuilder;
        private final IriLiteralList.Builder iriLiteralBuilder;
        private final IriBooleanList.Builder iriBooleanBuilder;
        private final IriBigIntegerList.Builder iriBigIntegerBuilder;
        private final BnodeBigIntegerList.Builder bNodeBigIntegerBuilder;

        protected Builder(File directory, RoIri predicate,
                RoDictionaries dictionaries, Hardwood hardwood) throws IOException {
            this.directory = directory;
            this.predicate = predicate;
            this.iriIriBuilder = new IriIriList.Builder(
                    new File(directory, FILE_TO_STORE_IRI_IRI), predicate,
                    dictionaries, hardwood);
            this.iriBooleanBuilder = new IriBooleanList.Builder(
                    new File(directory, FILE_TO_STORE_IRI_BOOLEAN), predicate,
                    dictionaries, hardwood);
            this.iriBnodeBuilder = new IriBnodeList.Builder(
                    new File(directory, FILE_TO_STORE_IRI_BNODE), predicate,
                    dictionaries, hardwood);
            this.iriIntegerBuilder = new IriIntegerLiteralList.Builder(
                    new File(directory, FILE_TO_STORE_IRI_INTEGER), predicate,
                    dictionaries, hardwood);
            this.iriLiteralBuilder = new IriLiteralList.Builder(
                    new File(directory, FILE_TO_STORE_IRI_LITERAL), predicate,
                    dictionaries, hardwood);

            this.bNodeIriBuilder = new BnodeIriList.Builder(
                    new File(directory, FILE_TO_STORE_BNODE_IRI), predicate,
                    dictionaries, hardwood);
            this.bNodeBnodeBuilder = new BnodeBnodeList.Builder(
                    new File(directory, FILE_TO_STORE_BNODE_BNODE), predicate,
                    dictionaries, hardwood);
            this.bNodeIntegerBuilder = new BnodeIntegerList.Builder(
                    new File(directory, FILE_TO_STORE_BNODE_INTEGER), predicate,
                    dictionaries, hardwood);
            this.bnodeStringBuilder = new BnodeStringLiteralList.Builder(
                    new File(directory, FILE_TO_STORE_BNODE_STRING), predicate,
                    dictionaries, hardwood);
            this.bnodeLiteralBuilder = new BnodeLiteralList.Builder(
                    new File(directory, FILE_TO_STORE_BNODE_LITERAL), predicate,
                    dictionaries, hardwood);
            this.iriBigIntegerBuilder = new IriBigIntegerList.Builder(
                    new File(directory, FILE_TO_STORE_IRI_BIG_INTEGER), predicate,
                    dictionaries, hardwood);
            this.bNodeBigIntegerBuilder = new BnodeBigIntegerList.Builder(
                    new File(directory, FILE_TO_STORE_BNODE_BIG_INTEGER), predicate,
                    dictionaries, hardwood);
            this.bNodeBooleanBuilder = new BnodeBooleanList.Builder(
                    new File(directory, FILE_TO_STORE_BNODE_BOOLEAN), predicate,
                    dictionaries, hardwood);

        }

        public void add(RoResource subject, RoValue object, RoResource context)
                throws IOException {
            if (subject instanceof RoIri) {
                RoIri s = (RoIri) subject;
                if (object instanceof RoIri) {
                    addIriIri(s, (RoIri) object, context);
                } else if (object instanceof RoBnode) {
                    addIriBnode(s, (RoBnode) object, context);
                } else if (object instanceof RoLiteral) {
                    addIriLiteral(s, (RoLiteral) object, context);
                }

            } else {
                RoBnode s = (RoBnode) subject;
                if (object instanceof RoIri) {
                    addBnodeIri(s, (RoIri) object, context);
                } else if (object instanceof RoBnode) {
                    addBnodeBnode(s, (RoBnode) object, context);
                } else if (object instanceof RoLiteral) {
                    addBnodeLiteral(s, (RoLiteral) object, context);
                }
            }

        }

        private void addIriIri(RoIri subject, RoIri object, RoResource context)
                throws IOException {
            iriIriBuilder.add(subject, object, context);
        }

        private void addBnodeIri(RoBnode subject, RoIri object,
                RoResource context) throws IOException {
            bNodeIriBuilder.add(subject, object, context);
        }

        private void addIriBnode(RoIri subject, RoBnode object,
                RoResource context) throws IOException {
            iriBnodeBuilder.add(subject, object, context);
        }

        private void addBnodeBnode(RoBnode subject, RoBnode object,
                RoResource context) throws IOException {
            bNodeBnodeBuilder.add(subject, object, context);
        }

        private void addBnodeLiteral(RoBnode subject, RoLiteral object,
                RoResource context) throws IOException {

            if (object instanceof RoIntegerLiteral) {
                addBnodeInteger(subject, (RoIntegerLiteral) object, context);
            } else if (object instanceof RoBigIntegerLiteral) {
                bNodeBigIntegerBuilder.add(subject, (RoBigIntegerLiteral) object, context);
            } else if (object instanceof RoBooleanLiteral) {
                bNodeBooleanBuilder.add(subject, (RoBooleanLiteral) object, context);
            } else if (object instanceof RoStringLiteral) {
                addBnodeString(subject, (RoStringLiteral) object, context);
            } else {
                bnodeLiteralBuilder.add(subject, object, context);
            }
        }

        private void addBnodeString(RoBnode subject, RoStringLiteral object,
                RoResource context) throws IOException {
            bnodeStringBuilder.add(subject, object, context);
        }

        private void addIriLiteral(RoIri subject, RoLiteral object,
                RoResource context) throws IOException {

            if (object instanceof RoIntegerLiteral) {
                addIriInteger(subject, (RoIntegerLiteral) object, context);
            } else if (object instanceof RoBooleanLiteral) {
                addIriBoolean(subject, (RoBooleanLiteral) object, context);
            } else if (object instanceof RoBigIntegerLiteral) {
                iriBigIntegerBuilder.add(subject, (RoBigIntegerLiteral) object, context);
            } else {
                iriLiteralBuilder.add(subject, object, context);
            }
        }

        private void addBnodeInteger(RoBnode subject, RoIntegerLiteral object,
                RoResource context) throws IOException {
            bNodeIntegerBuilder.add(subject, object, context);
        }

        private void addIriInteger(RoIri subject, RoIntegerLiteral object,
                RoResource context) throws IOException {
            iriIntegerBuilder.add(subject, object, context);
        }

        RoPredicateStore build() throws IOException {
            try (DataOutputStream fw = new DataOutputStream(
                    new FileOutputStream(
                            new File(directory, FILE_TO_STORE_PREDICATE_ID)))) {
                fw.writeLong(predicate.getLongId());
            }
            return new RoPredicateStore(predicate,
                    bNodeIriBuilder.build(),
                    bNodeBnodeBuilder.build(),
                    bNodeIntegerBuilder.build(),
                    bNodeBooleanBuilder.build(),
                    iriBnodeBuilder.build(),
                    iriIriBuilder.build(),
                    iriIntegerBuilder.build(),
                    iriLiteralBuilder.build(),
                    iriBooleanBuilder.build(),
                    iriBigIntegerBuilder.build(),
                    bNodeBigIntegerBuilder.build(),
                    bnodeStringBuilder.build(),
                    bnodeLiteralBuilder.build());
        }

        private void addIriBoolean(RoIri subject, RoBooleanLiteral roBooleanLiteral, RoResource context) throws IOException {
            iriBooleanBuilder.add(subject, roBooleanLiteral, context);
        }
    }

    public RoIri getPredicate() {
        return predicate;
    }
}
