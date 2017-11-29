package sib.swiss.swissprot.sparql.ro;

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
import java.util.stream.Stream;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;
import sib.swiss.swissprot.sparql.ro.quads.BnodeBnodeList;
import sib.swiss.swissprot.sparql.ro.quads.BnodeBooleanList;
import sib.swiss.swissprot.sparql.ro.quads.BnodeIntegerList;
import sib.swiss.swissprot.sparql.ro.quads.BnodeIriList;
import sib.swiss.swissprot.sparql.ro.quads.BnodeStringLiteralList;
import sib.swiss.swissprot.sparql.ro.quads.IriBnodeList;
import sib.swiss.swissprot.sparql.ro.quads.IriBooleanList;
import sib.swiss.swissprot.sparql.ro.quads.IriIntegerLiteralList;
import sib.swiss.swissprot.sparql.ro.quads.IriIriList;
import sib.swiss.swissprot.sparql.ro.quads.IriLiteralList;
import sib.swiss.swissprot.sparql.ro.quads.RoResourceRoValueList;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoBooleanLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoResource;
import sib.swiss.swissprot.sparql.ro.values.RoValue;

public class RoPredicateStore implements Iterable<Statement> {

    private final RoIri predicate;

    private final IriIriList iriIriList;

    private final IriBnodeList iriBnodeList;
    private final IriIntegerLiteralList iriIntegerList;
    private final IriBooleanList iriBooleanList;

    private final BnodeIriList bNodeIriList;
    private final BnodeBnodeList bNodeBnodeList;
    private final BnodeIntegerList bNodeIntList;
    private final BnodeBooleanList bNodeBooleanList;

    private static final String FILE_TO_STORE_PREDICATE_ID = "predicate";
    private static final String FILE_TO_STORE_IRI_BNODE = "iri_bnode";
    private static final String FILE_TO_STORE_BNODE_BNODE = "bnode_bnode";
    private static final String FILE_TO_STORE_BNODE_IRI = "bnode_iri";
    private static final String FILE_TO_STORE_IRI_IRI = "iri_iri";
    private static final String FILE_TO_STORE_IRI_INTEGER = "iri_integer";
    private static final String FILE_TO_STORE_IRI_BOOLEAN = "iri_boolean";
    private static final String FILE_TO_STORE_IRI_LITERAL = "iri_literal";

    private static final String FILE_TO_STORE_BNODE_INTEGER = "bnode_integer";
    private static final String FILE_TO_STORE_BNODE_BOOLEAN = "bnode_boolean";
    private static final String FILE_TO_STORE_BNODE_STRING = "bnode_string";

    public RoPredicateStore(File directory, RoDictionaries dictionaries)
            throws IOException {

        try (DataInputStream fr = new DataInputStream(new FileInputStream(
                new File(directory, FILE_TO_STORE_PREDICATE_ID)))) {
            predicate = new RoIri(fr.readLong(), dictionaries.getIriDict());
        }

        this.iriIriList = new IriIriList(new File(directory, FILE_TO_STORE_IRI_IRI),
                predicate, dictionaries);
        this.iriBnodeList = new IriBnodeList(new File(directory, FILE_TO_STORE_IRI_BNODE),
                predicate, dictionaries);
        this.iriIntegerList = new IriIntegerLiteralList(
                new File(directory, FILE_TO_STORE_IRI_INTEGER), predicate, dictionaries);
        this.iriBooleanList = new IriBooleanList(
                new File(directory, FILE_TO_STORE_IRI_BOOLEAN), predicate, dictionaries);

        this.bNodeIriList = new BnodeIriList(new File(directory, FILE_TO_STORE_BNODE_IRI),
                predicate, dictionaries);
        this.bNodeBnodeList = new BnodeBnodeList(
                new File(directory, FILE_TO_STORE_IRI_BNODE), predicate, dictionaries);
        this.bNodeIntList = new BnodeIntegerList(
                new File(directory, FILE_TO_STORE_BNODE_INTEGER), predicate, dictionaries);
        this.bNodeBooleanList = new BnodeBooleanList(
                new File(directory, FILE_TO_STORE_BNODE_BOOLEAN), predicate, dictionaries);
    }

    private RoPredicateStore(RoIri predicate,
            BnodeIriList bNodeIriList,
            BnodeBnodeList bnodeBnodeList,
            BnodeIntegerList bNodeIntList,
            BnodeBooleanList bNodeBooleanList,
            IriBnodeList iriBnodeList,
            IriIriList iriIriList,
            IriIntegerLiteralList iriIntegerList,
            IriBooleanList iriBooleanList
    )
            throws IOException {
        this.bNodeIriList = bNodeIriList;
        this.iriIriList = iriIriList;
        this.bNodeBooleanList = bNodeBooleanList;
        this.iriBnodeList = iriBnodeList;
        this.bNodeBnodeList = bnodeBnodeList;
        this.iriIntegerList = iriIntegerList;
        this.iriBooleanList = iriBooleanList;
        this.bNodeIntList = bNodeIntList;
        this.predicate = predicate;
    }

    public static File initDirectory(File directory, IRI predicate) {
        File file = null;
        try {
            file = new File(directory, URLEncoder.encode(
                    predicate.stringValue(), StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException();
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
        return Stream.of(bNodeBnodeList, bNodeIriList, bNodeIntList, bNodeBooleanList, iriBnodeList, iriIriList, iriIntegerList, iriBooleanList)
                .flatMap(RoResourceRoValueList::stream);
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
        private final IriBnodeList.Builder iriBnodeBuilder;
        private final IriIntegerLiteralList.Builder iriIntegerBuilder;
        private final IriLiteralList.Builder iriLiteralBuilder;
        private final IriBooleanList.Builder iriBooleanBuilder;

        protected Builder(File directory, RoIri predicate,
                RoDictionaries dictionaries) throws IOException {
            this.directory = directory;
            initDirectory(directory, predicate);
            this.predicate = predicate;
            this.iriIriBuilder = new IriIriList.Builder(
                    new File(directory, FILE_TO_STORE_IRI_IRI), predicate,
                    dictionaries);
            this.bNodeIriBuilder = new BnodeIriList.Builder(
                    new File(directory, FILE_TO_STORE_BNODE_IRI), predicate,
                    dictionaries);
            this.bNodeBnodeBuilder = new BnodeBnodeList.Builder(
                    new File(directory, FILE_TO_STORE_BNODE_BNODE), predicate,
                    dictionaries);
            this.iriBnodeBuilder = new IriBnodeList.Builder(
                    new File(directory, FILE_TO_STORE_IRI_BNODE), predicate,
                    dictionaries);
            this.iriIntegerBuilder = new IriIntegerLiteralList.Builder(
                    new File(directory, FILE_TO_STORE_IRI_INTEGER), predicate,
                    dictionaries);
            this.iriLiteralBuilder = new IriLiteralList.Builder(
                    new File(directory, FILE_TO_STORE_IRI_LITERAL), predicate,
                    dictionaries);
            this.bNodeIntegerBuilder = new BnodeIntegerList.Builder(
                    new File(directory, FILE_TO_STORE_BNODE_INTEGER), predicate,
                    dictionaries);
            this.bnodeStringBuilder = new BnodeStringLiteralList.Builder(
                    new File(directory, FILE_TO_STORE_BNODE_STRING), predicate,
                    dictionaries);
            this.bNodeBooleanBuilder = new BnodeBooleanList.Builder(new File(directory, FILE_TO_STORE_BNODE_BOOLEAN), predicate,
                    dictionaries);
            this.iriBooleanBuilder = new IriBooleanList.Builder(
                    new File(directory, FILE_TO_STORE_IRI_BOOLEAN), predicate,
                    dictionaries);
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
            } else if (object.getDatatype() == XMLSchema.STRING)
				;
            addBnodeString(subject, object, context);
        }

        private void addBnodeString(RoBnode subject, RoLiteral object,
                RoResource context) throws IOException {
            bnodeStringBuilder.add(subject, object, context);
        }

        private void addIriLiteral(RoIri subject, RoLiteral object,
                RoResource context) throws IOException {

            if (object instanceof RoIntegerLiteral) {
                addIriInteger(subject, (RoIntegerLiteral) object, context);
            } else if (object instanceof RoBooleanLiteral) {
                addIriBoolean(subject, (RoBooleanLiteral) object, context);
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
                    iriBooleanBuilder.build());
        }

        private void addIriBoolean(RoIri subject, RoBooleanLiteral roBooleanLiteral, RoResource context) throws IOException {
            iriBooleanBuilder.add(subject, roBooleanLiteral, context);
        }
    }

    public RoIri getPredicate() {
        return predicate;
    }
}
