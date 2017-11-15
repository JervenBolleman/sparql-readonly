package sib.swiss.swissprot.sparql.temporary.dictionaries;

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
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;
import static sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict.DATATYPE_ID;
import static sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict.LABEL;

public class TempLiteralDictionary extends TempDictionary {

    private final Map<IRI, FileOutputStream> datatypes = new HashMap<>();
    private final Map<String, FileOutputStream> languages = new HashMap<>();

    public TempLiteralDictionary(File out) {
        super(out);
    }

    public void add(Literal subject) throws IOException {
        final IRI namespace = subject.getDatatype();
        if (namespace == XMLSchema.BOOLEAN || namespace == XMLSchema.INT) {
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
        Configuration conf = new Configuration();
        TypeDescription schema;
        schema = TypeDescription.createStruct()
                .addField(LABEL, TypeDescription.createString())
                .addField(DATATYPE_ID, TypeDescription.createByte())
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

            BytesColumnVector labelsCol = (BytesColumnVector) batch.cols[0];
            LongColumnVector datatypesCol = (LongColumnVector) batch.cols[1];
            BytesColumnVector languagesCol = (BytesColumnVector) batch.cols[2];
            writeLanguageStrings(batch, writer, labelsCol, datatypesCol, languagesCol);

            writeDatatypesStrings(batch, writer, labelsCol, datatypesCol, languagesCol);
        }
        Reader reader = OrcFile.createReader(path, OrcFile.readerOptions(conf));
        return new RoLiteralDict(reader);
    }

    private void writeLanguageStrings(VectorizedRowBatch batch, final Writer writer, BytesColumnVector labelsCol, LongColumnVector datatypesCol, BytesColumnVector languagesCol) throws IOException {
        int datatypeCode = RoLiteralDict.datatypeCode(XMLSchema.LANGUAGE);
        for (String lang : languages.keySet()) {
            String languageTag = lang.substring(5);
            try (BufferedReader br = Files.newBufferedReader(new File(out, lang).toPath())) {

                //br returns as stream and convert it into a List
                Iterator<String> iterator = br.lines()
                        .sorted(Collator.getInstance(Locale.forLanguageTag(languageTag)))
                        .distinct()
                        .iterator();
                while (iterator.hasNext()) {

                    if (batch.size == batch.getMaxSize()) {
                        writer.addRowBatch(batch);
                        batch.reset();
                    }
                    labelsCol.setVal(batch.size, iterator.next().getBytes());
                    datatypesCol.vector[batch.size] = datatypeCode;
                    languagesCol.setVal(batch.size, languageTag.getBytes());
                    batch.size++;
                }
            }
        }
    }

    private void writeDatatypesStrings(VectorizedRowBatch batch, Writer writer, BytesColumnVector labelsCol, LongColumnVector datatypesCol, BytesColumnVector languagesCol) throws IOException {
        for (IRI lang : datatypes.keySet()) {

            int datatypeCode = RoLiteralDict.datatypeCode(lang);
            try (BufferedReader br = Files.newBufferedReader(new File(out, FileNameEncoderFunctions.encodeIRI(lang)).toPath())) {

                //br returns as stream and convert it into a List
                Iterator<String> iterator = br.lines()
                        .sorted()
                        .distinct()
                        .iterator();
                while (iterator.hasNext()) {

                    if (batch.size == batch.getMaxSize()) {
                        writer.addRowBatch(batch);
                        batch.reset();
                    }
                    labelsCol.setVal(batch.size, iterator.next().getBytes());
                    datatypesCol.vector[batch.size] = datatypeCode;
                    languagesCol.setVal(batch.size, new byte[]{});
                    batch.size++;
                }
                if (batch.size != 0) {
                    writer.addRowBatch(batch);
                }
            }
        }
    }
}
