package sib.swiss.swissprot.sparql.temporary.dictionaries;

import com.sun.istack.logging.Logger;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.Collator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.CompressionKind;
import org.apache.orc.OrcFile;
import org.apache.orc.Reader;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;

import sib.swiss.swissprot.sparql.ro.FileNameEncoderFunctions;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;
import static sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict.DATATYPE_ID;
import static sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict.LABEL;

public class TempLiteralDictionary extends TempDictionary {

    private final Map<IRI, FileOutputStream> datatypes = new HashMap<>();
    private final Map<String, FileOutputStream> languages = new HashMap<>();

    public TempLiteralDictionary(File out, Configuration conf) {
        super(out, conf);
    }

    public void add(Literal subject) throws IOException {
        final IRI namespace = subject.getDatatype();
        if (namespace.equals(XMLSchema.BOOLEAN) || namespace.equals(XMLSchema.INT)) {
            return;
        }

        final byte[] label = stringAsUtf8ByteArray(subject.getLabel());
        if (subject.getLanguage().isPresent()) {
            final String language = subject.getLanguage().get();
            FileOutputStream fos = languages.get(language);
            if (fos == null) {
                fos = new FileOutputStream(new File(out, "lang-" + language));
                languages.put(language, fos);
            }
            fos.write(label);
            fos.write('\n');
        } else {
            FileOutputStream fos = datatypes.get(namespace);
            if (fos == null) {
                fos = new FileOutputStream(new File(out,
                        FileNameEncoderFunctions.encodeIRI(namespace)));
                datatypes.put(namespace, fos);
            }
            fos.write(label);
            fos.write('\n');
        }
    }

    @Override
    public void close() throws IOException {
        for (FileOutputStream fos : datatypes.values()) {
            fos.close();
        }
        for (FileOutputStream fos : languages.values()) {
            fos.close();
        }
    }

    public RoLiteralDict load() throws IOException {
        TypeDescription schema;
        schema = TypeDescription.createStruct()
                .addField(RoDictionary.ID, TypeDescription.createLong())
                .addField(LABEL, TypeDescription.createString())
                .addField(DATATYPE_ID, TypeDescription.createLong())
                .addField(RoLiteralDict.LANGUAGE, TypeDescription.createString());
        VectorizedRowBatch batch = schema.createRowBatch();
        File file = new File(out.getAbsolutePath(), RoLiteralDict.PATH_NAME);
        if (file.exists()) {
            file.delete();
        }
        final Path path = new Path(out.getAbsolutePath(), RoLiteralDict.PATH_NAME);
        try (Writer writer = OrcFile.createWriter(path,
                OrcFile.writerOptions(conf)
                        .setSchema(schema)
                        .compress(CompressionKind.LZ4))) {

            long index = writeLanguageStrings(batch, writer);

            writeDatatypesStrings(batch, writer, index);
        }
        Reader reader = OrcFile.createReader(path, OrcFile.readerOptions(conf));
        return new RoLiteralDict(reader);
    }

    private long writeLanguageStrings(VectorizedRowBatch batch, final Writer writer) throws IOException {
        int datatypeCode = RoLiteralDict.datatypeCode(XMLSchema.LANGUAGE);
        long index = 0;
        for (String lang : languages.keySet()) {
            String languageTag = lang.substring(5);
            try (BufferedReader br = Files.newBufferedReader(new File(out, lang).toPath())) {

                //br returns as stream and convert it into a List
                Iterator<String> iterator = br.lines()
                        .sorted(Collator.getInstance(Locale.forLanguageTag(languageTag)))
                        .distinct()
                        .iterator();
                while (iterator.hasNext()) {
                    LongColumnVector idCol = (LongColumnVector) batch.cols[RoDictionary.ID_COL];
                    BytesColumnVector labelsCol = (BytesColumnVector) batch.cols[RoDictionary.VALUE_COL];
                    LongColumnVector datatypesCol = (LongColumnVector) batch.cols[RoLiteralDict.DATATYPE_COL];
                    BytesColumnVector languagesCol = (BytesColumnVector) batch.cols[RoLiteralDict.LANGUAGE_COL];
                    if (batch.size == batch.getMaxSize()) {
                        datatypesCol.fill(datatypeCode);
                        writer.addRowBatch(batch);
                        batch.reset();
                    }
                    idCol.vector[batch.size] = index++;
                    labelsCol.setVal(batch.size, iterator.next().getBytes());
                   
                    languagesCol.setVal(batch.size, languageTag.getBytes());
                    batch.size++;
                }
                ((LongColumnVector) batch.cols[RoLiteralDict.DATATYPE_COL]).fill(datatypeCode);
                writer.addRowBatch(batch);
                batch.reset();
            }
            if (!new File(out, lang).delete()) {
                Logger.getLogger(this.getClass()).warning("Can't delete lang temp file " + lang);
            }
        }
        return index;
    }

    private void writeDatatypesStrings(VectorizedRowBatch batch, Writer writer, long index) throws IOException {
        for (IRI datatype : datatypes.keySet()) {

            int datatypeCode = RoLiteralDict.datatypeCode(datatype);
            File tempFile = new File(out, FileNameEncoderFunctions.encodeIRI(datatype));
            try (BufferedReader br = Files.newBufferedReader(tempFile.toPath())) {

                //br returns as stream and convert it into a List
                Iterator<String> iterator = br.lines()
                        .sorted()
                        .distinct()
                        .iterator();
                while (iterator.hasNext()) {
                    LongColumnVector idCol = (LongColumnVector) batch.cols[RoDictionary.ID_COL];
                    BytesColumnVector labelsCol = (BytesColumnVector) batch.cols[RoDictionary.VALUE_COL];
                    LongColumnVector datatypesCol = (LongColumnVector) batch.cols[RoLiteralDict.DATATYPE_COL];
                    BytesColumnVector languagesCol = (BytesColumnVector) batch.cols[RoLiteralDict.LANGUAGE_COL];
                    if (batch.size == batch.getMaxSize()) {
                        datatypesCol.fill(datatypeCode);
                        languagesCol.fillWithNulls();
                        writer.addRowBatch(batch);
                        batch.reset();
                    }
                    idCol.vector[batch.size] = index++;
                    labelsCol.setVal(batch.size, iterator.next().getBytes());
                    batch.size++;
                }
                ((LongColumnVector) batch.cols[RoLiteralDict.DATATYPE_COL]).fill(datatypeCode);
                ((BytesColumnVector) batch.cols[RoLiteralDict.LANGUAGE_COL]).fillWithNulls();
                writer.addRowBatch(batch);
                batch.reset();
            }

            if (!tempFile.delete()) {
                Logger.getLogger(this.getClass()).warning("Can't delete datatype temp file " + datatype);
            }
           
        }
    }
}
