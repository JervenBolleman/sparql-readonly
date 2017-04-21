package sib.swiss.swissprot.sparql.ro;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;

import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;
import sib.swiss.swissprot.sparql.ro.quads.BnodeBnodeList;
import sib.swiss.swissprot.sparql.ro.quads.BnodeIntegerLiteralList;
import sib.swiss.swissprot.sparql.ro.quads.BnodeIriList;
import sib.swiss.swissprot.sparql.ro.quads.BnodeStringLiteralList;
import sib.swiss.swissprot.sparql.ro.quads.IriBnodeList;
import sib.swiss.swissprot.sparql.ro.quads.IriIntegerLiteralList;
import sib.swiss.swissprot.sparql.ro.quads.IriIriList;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIntegerLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoResource;
import sib.swiss.swissprot.sparql.ro.values.RoValue;

public class RoPredicateStore {

	private final RoIri predicate;
	private final BnodeIriList bNodeIriList;
	private final IriIriList iriIriList;
	private final IriBnodeList iriBnodeList;
	private final BnodeBnodeList bnodeBnodeList;

	private static final String FILE_TO_STORE_PREDICATE_ID = "predicate";
	private static final String FILE_TO_STORE_IRI_BNODE = "iri_bnode";
	private static final String FILE_TO_STORE_BNODE_BNODE = "bnode_bnode";
	private static final String FILE_TO_STORE_BNODE_IRI = "bnode_iri";
	private static final String FILE_TO_STORE_IRI_IRI = "iri_iri";
	private static final String FILE_TO_STORE_IRI_INTEGER = "iri_integer";
	private static final String FILE_TO_STORE_IRI_BOOLEAN = "iri_boolean";
	private static final String FILE_TO_STORE_IRI_STRING = "iri_string";

	private static final String FILE_TO_STORE_BNODE_INTEGER = "bnode_integer";
	private static final String FILE_TO_STORE_BNODE_BOOLEAN = "bnode_boolean";
	private static final String FILE_TO_STORE_BNODE_STRING = "bnode_string";

	public RoPredicateStore(File directory, RoIriDictionary dictionary)
			throws FileNotFoundException, IOException {
		try (DataInputStream fr = new DataInputStream(new FileInputStream(
				new File(directory, FILE_TO_STORE_PREDICATE_ID)))) {
			predicate = new RoIri(fr.readLong(), dictionary);
		}
		this.bNodeIriList = new BnodeIriList(new File(FILE_TO_STORE_BNODE_IRI),
				predicate);
		this.iriIriList = new IriIriList(new File(FILE_TO_STORE_IRI_IRI),
				predicate);
		this.iriBnodeList = new IriBnodeList(new File(FILE_TO_STORE_IRI_BNODE),
				predicate);
		this.bnodeBnodeList = new BnodeBnodeList(
				new File(FILE_TO_STORE_IRI_BNODE), predicate);
	}

	private RoPredicateStore(File directory, RoIri predicate,
			BnodeIriList bNodeIriList, IriIriList iriIriList,
			IriBnodeList iriBnodeList, BnodeBnodeList bnodeBnodeList)
			throws IOException {
		this.bNodeIriList = bNodeIriList;
		this.iriIriList = iriIriList;
		this.iriBnodeList = iriBnodeList;
		this.bnodeBnodeList = bnodeBnodeList;
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

	public static class Builder {

		private final IriIriList.Builder iriIriBuilder;
		private final File directory;
		private final RoIri predicate;
		private final BnodeIriList.Builder bnodeIriBuilder;
		private final BnodeBnodeList.Builder bnodeBnodeBuilder;
		private final IriBnodeList.Builder iriBnodeBuilder;
		private final IriIntegerLiteralList.Builder iriIntegerBuilder;
		private final BnodeIntegerLiteralList.Builder bnodeIntegerBuilder;
		private final BnodeStringLiteralList.Builder bnodeStringBuilder;

		protected Builder(File directory, RoIri predicate,
				RoLiteralDict literalDict, RoNamespaces namespaces,
				RoIriDictionary iriDictionary) throws IOException {
			this.directory = directory;
			initDirectory(directory, predicate);
			this.predicate = predicate;
			this.iriIriBuilder = new IriIriList.Builder(
					new File(directory, FILE_TO_STORE_IRI_IRI), predicate,
					iriDictionary, namespaces);
			this.bnodeIriBuilder = new BnodeIriList.Builder(
					new File(directory, FILE_TO_STORE_BNODE_IRI), predicate,
					namespaces, iriDictionary);
			this.bnodeBnodeBuilder = new BnodeBnodeList.Builder(
					new File(directory, FILE_TO_STORE_BNODE_BNODE), predicate,
					namespaces, iriDictionary);
			this.iriBnodeBuilder = new IriBnodeList.Builder(
					new File(directory, FILE_TO_STORE_IRI_BNODE), predicate,
					iriDictionary, namespaces);
			this.iriIntegerBuilder = new IriIntegerLiteralList.Builder(
					new File(directory, FILE_TO_STORE_IRI_INTEGER), predicate,
					iriDictionary, namespaces);
			this.bnodeIntegerBuilder = new BnodeIntegerLiteralList.Builder(
					new File(directory, FILE_TO_STORE_BNODE_INTEGER), predicate,
					namespaces, iriDictionary);
			this.bnodeStringBuilder = new BnodeStringLiteralList.Builder(
					new File(directory, FILE_TO_STORE_BNODE_STRING), predicate,
					literalDict, namespaces, iriDictionary);
		}

		public void add(RoResource subject, RoValue object, RoResource context)
				throws IOException {
			if (subject instanceof RoIri) {
				RoIri s = (RoIri) subject;
				if (object instanceof RoIri)
					addIriIri(s, (RoIri) object, context);
				else if (object instanceof RoBnode)
					addIriBnode(s, (RoBnode) object, context);
				else if (object instanceof RoLiteral)
					addIriLiteral(s, (RoLiteral) object, context);

			} else {
				RoBnode s = (RoBnode) subject;
				if (object instanceof RoIri)
					addBnodeIri(s, (RoIri) object, context);
				else if (object instanceof RoBnode)
					addBnodeBnode(s, (RoBnode) object, context);
				else if (object instanceof RoLiteral)
					addBnodeLiteral(s, (RoLiteral) object, context);
			}

		}

		private void addIriIri(RoIri subject, RoIri object, RoResource context)
				throws IOException {
			iriIriBuilder.add(subject, object, context);
		}

		private void addBnodeIri(RoBnode subject, RoIri object,
				RoResource context) throws IOException {
			bnodeIriBuilder.add(subject, object, context);
		}

		private void addIriBnode(RoIri subject, RoBnode object,
				RoResource context) throws IOException {
			iriBnodeBuilder.add(subject, object, context);
		}

		private void addBnodeBnode(RoBnode subject, RoBnode object,
				RoResource context) throws IOException {
			bnodeBnodeBuilder.add(subject, object, context);
		}

		private void addBnodeLiteral(RoBnode subject, RoLiteral object,
				RoResource context) throws IOException {

			if (object instanceof RoIntegerLiteral)
				addBnodeInteger(subject, (RoIntegerLiteral) object, context);
			else if (object.getDatatype() == XMLSchema.STRING)
				;
			addBnodeString(subject, object, context);
		}

		private void addBnodeString(RoBnode subject, RoLiteral object,
				RoResource context) throws IOException {
			bnodeStringBuilder.add(subject, object, context);
		}

		private void addIriLiteral(RoIri subject, RoLiteral object,
				RoResource context) throws IOException {

			if (object instanceof RoIntegerLiteral)
				addIriInteger(subject, (RoIntegerLiteral) object, context);
		}

		private void addBnodeInteger(RoBnode subject, RoIntegerLiteral object,
				RoResource context) throws IOException {
			bnodeIntegerBuilder.add(subject, object, context);
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
			return new RoPredicateStore(directory, predicate,
					bnodeIriBuilder.build(), iriIriBuilder.build(),
					iriBnodeBuilder.build(), bnodeBnodeBuilder.build());
		}
	}

	public RoIri getPredicate() {
		return predicate;
	}
}
