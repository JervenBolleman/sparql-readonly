package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.hadoop.hive.ql.io.sarg.PredicateLeaf;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgument;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgumentFactory;
import org.apache.orc.Reader;
import org.apache.orc.RecordReader;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;

import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoSimpleLiteral;

public class RoLiteralDict extends RoDictionary<RoLiteral, Literal> {

    public static final String PATH_NAME = "literals";
    public static final String LABEL = "label";
    public static final String DATATYPE_ID = "datatype_id";
    public static final String LANGUAGE = "language";

    private static final IRI[] DATA_TYPES = new IRI[]{XMLSchema.ANYURI,
        XMLSchema.BASE64BINARY, XMLSchema.BOOLEAN, XMLSchema.BYTE,
        XMLSchema.DATE, XMLSchema.DATETIME, XMLSchema.DAYTIMEDURATION,
        XMLSchema.DECIMAL, XMLSchema.DOUBLE, XMLSchema.DURATION,
        XMLSchema.ENTITIES, XMLSchema.ENTITY, XMLSchema.FLOAT,
        XMLSchema.GDAY, XMLSchema.GMONTH, XMLSchema.GMONTHDAY,
        XMLSchema.GYEAR, XMLSchema.GYEARMONTH, XMLSchema.HEXBINARY,
        XMLSchema.ID, XMLSchema.IDREF, XMLSchema.IDREFS, XMLSchema.INT,
        XMLSchema.INTEGER, XMLSchema.LANGUAGE, XMLSchema.LONG,
        XMLSchema.NAME, XMLSchema.NCNAME, XMLSchema.NEGATIVE_INTEGER,
        XMLSchema.NMTOKEN, XMLSchema.NMTOKENS,
        XMLSchema.NON_NEGATIVE_INTEGER, XMLSchema.NON_POSITIVE_INTEGER,
        XMLSchema.NORMALIZEDSTRING, XMLSchema.NOTATION,
        XMLSchema.POSITIVE_INTEGER, XMLSchema.QNAME, XMLSchema.SHORT,
        XMLSchema.STRING, XMLSchema.TIME, XMLSchema.TOKEN,
        XMLSchema.UNSIGNED_BYTE, XMLSchema.UNSIGNED_INT,
        XMLSchema.UNSIGNED_LONG, XMLSchema.UNSIGNED_SHORT,
        XMLSchema.YEARMONTHDURATION};

    public static final int DATATYPE_COL = RoDictionary.VALUE_COL + 1;
    public static final int LANGUAGE_COL = DATATYPE_COL + 1;

    public RoLiteralDict(Reader reader) {
        super(reader);
    }

    @Override
    public Optional<RoLiteral> find(Literal value) {
        try {
            IRI dt = value.getDatatype();
            int dtIndex = -1;
            SearchArgument.Builder builder = SearchArgumentFactory.newBuilder()
                    .equals(LABEL, PredicateLeaf.Type.STRING, value.stringValue());
            if (dt != null) {
                dtIndex = datatypeCode(value.getDatatype());
                builder = builder.equals(DATATYPE_ID, PredicateLeaf.Type.LONG, (long) dtIndex)
                        .isNull(LANGUAGE, PredicateLeaf.Type.STRING);

            } else {
                builder = builder
                        .equals(LANGUAGE, PredicateLeaf.Type.STRING, value.getLanguage().orElse(""))
                        .isNull(DATATYPE_ID, PredicateLeaf.Type.LONG);
            }

            final Reader.Options options = new Reader.Options()
                    .include(new boolean[]{true, true, true, true, true})
                    .searchArgument(builder.build(), new String[]{LABEL, DATATYPE_ID, LANGUAGE});

            try (RecordReader rows = reader.rows(options)) {
                VectorizedRowBatch batch = schema.createRowBatch();
                boolean nextBatch = rows.nextBatch(batch);
                while (nextBatch) {
                    final LongColumnVector id = (LongColumnVector) batch.cols[RoDictionary.ID_COL];
                    final BytesColumnVector bcv = (BytesColumnVector) batch.cols[RoDictionary.VALUE_COL];

                    for (int i = 0; i < batch.size; i++) {
                        if (dt != null) {
                            final LongColumnVector dtId = (LongColumnVector) batch.cols[DATATYPE_COL];
                            if (dtId.vector[i] == dtIndex && new String(bcv.vector[i], bcv.start[i], bcv.length[i]).equals(value.stringValue())) {
                                return Optional.of(new RoSimpleLiteral(id.vector[i], this));
                            }
                        } else {
                            final BytesColumnVector lang = (BytesColumnVector) batch.cols[LANGUAGE_COL];
                            if (new String(bcv.vector[i], bcv.start[i], bcv.length[i]).equals(value.stringValue())
                                    && new String(lang.vector[i], lang.start[i], lang.length[i]).equals(value.getLanguage().get())) {
                                return Optional.of(new RoSimpleLiteral(id.vector[i], this));
                            }
                        }
                    }
                }
                return Optional.empty();
            }
        } catch (IOException ex) {
            Logger.getLogger(RoLiteralDict.class.getName()).log(Level.SEVERE, null, ex);
            return Optional.empty();
        }
    }

    public String stringValue(long id) {
        try (RecordReader rows = reader.rows()) {
            rows.seekToRow(id);
            VectorizedRowBatch batch = schema.createRowBatch(1);
            final boolean nextBatch = rows.nextBatch(batch);
            assert nextBatch;
            BytesColumnVector stringVector = (BytesColumnVector) batch.cols[RoDictionary.VALUE_COL];
            return stringVector.toString(0);
        } catch (IOException ex) {
            Logger.getLogger(RoLiteralDict.class.getName()).log(Level.SEVERE, null, ex);
            return "";
        }
    }

    public IRI dataType(long id) {
        try (RecordReader rows = reader.rows()) {
            rows.seekToRow(id);
            VectorizedRowBatch batch = schema.createRowBatch(1);
            final boolean nextBatch = rows.nextBatch(batch);
            assert nextBatch;
            LongColumnVector stringVector = (LongColumnVector) batch.cols[DATATYPE_COL];
            long did = stringVector.vector[0];
            return DATA_TYPES[(int) did];
        } catch (IOException ex) {
            Logger.getLogger(RoLiteralDict.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public RoLiteral get(long id) {
        return new RoSimpleLiteral(id, this);
    }

    public Optional<String> language(long id) {
        try (RecordReader rows = reader.rows()) {
            rows.seekToRow(id);
            VectorizedRowBatch batch = schema.createRowBatch(1);
            final boolean nextBatch = rows.nextBatch(batch);
            assert nextBatch;
            BytesColumnVector bcv = (BytesColumnVector) batch.cols[LANGUAGE_COL];
            if (bcv.vector[0] == null || bcv.length[0] == 0) {
                return Optional.empty();
            }
            String s = new String(bcv.vector[0], bcv.start[0], bcv.length[0]);
            if (s.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(s);
            }
        } catch (IOException ex) {
            Logger.getLogger(RoLiteralDict.class.getName()).log(Level.SEVERE, null, ex);
            return Optional.empty();
        }
    }

    public static int datatypeCode(IRI datatype) {
        for (int i = 0; i < DATA_TYPES.length; i++) {
            if (DATA_TYPES[i].equals(datatype)) {
                return i;
            }
        }
        return -1;
    }
}
