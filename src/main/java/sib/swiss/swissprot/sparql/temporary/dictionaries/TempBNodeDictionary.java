package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.Collator;
import java.util.Locale;
import java.nio.file.Files;

import org.eclipse.rdf4j.model.BNode;

import sib.swiss.swissprot.sparql.ro.dictionaries.RoBnodeDictionary;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;

import java.io.BufferedReader;
import java.util.Iterator;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.CompressionKind;
import org.apache.orc.OrcFile;
import org.apache.orc.Reader;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;

public class TempBNodeDictionary extends TempDictionary {

    private static final String BNODE_STRING_VALUE = "bnode_string_value";
    protected static final long SECOND_BYTE_TRUE = 1L << 63;
    private final FileOutputStream fos;

    public TempBNodeDictionary(File out) throws FileNotFoundException {
        super(out);
        fos = new FileOutputStream(new File(out, "tempbnodes"));
    }

    public void add(BNode subject) throws IOException {
        try {
            long pid = Long.parseLong(subject.getID());
            if ((SECOND_BYTE_TRUE & pid) == SECOND_BYTE_TRUE) {
                add(new RoBnode(pid));
            }
        } catch (NumberFormatException e) {
            add(subject.getID());
        }
    }

    private void add(String id) throws IOException {
        fos.write(stringAsUtf8ByteArray(id));
        fos.write('\n');
    }

    @Override
    public void close() throws IOException {
        fos.close();

    }

    public RoBnodeDictionary load() throws IOException {
        Configuration conf = new Configuration();
        TypeDescription schema = TypeDescription.createStruct()
                .addField(BNODE_STRING_VALUE, TypeDescription.createString());
        VectorizedRowBatch batch = schema.createRowBatch();
        File file = new File(out.getAbsolutePath(), RoBnodeDictionary.PATH_NAME);
        if (file.exists())
            file.delete();
        final Path path = new Path(out.getAbsolutePath(), RoBnodeDictionary.PATH_NAME);
        try (Writer writer = OrcFile.createWriter(path,
                OrcFile.writerOptions(conf)
                        .setSchema(schema)
                        .compress(CompressionKind.LZ4))) {

            BytesColumnVector col = (BytesColumnVector) batch.cols[0];
            try (BufferedReader br = Files.newBufferedReader(new File(out, "tempbnodes").toPath())) {

                //br returns as stream and convert it into a List
                Iterator<String> iterator = br.lines()
                        .filter(s -> {
                            try {
                                int parseInt = Integer.parseInt(s);
                                if ((SECOND_BYTE_TRUE & parseInt) == SECOND_BYTE_TRUE) {
                                    return true;
                                }
                                return false;
                            } catch (NumberFormatException e) {
                                return true;
                            }
                        })
                        .sorted(Collator.getInstance(Locale.US))
                        .distinct()
                        .iterator();
                while (iterator.hasNext()) {

                    if (batch.size == batch.getMaxSize()) {
                        writer.addRowBatch(batch);
                        batch.reset();
                    }
                    col.setVal(batch.size++, iterator.next().getBytes());
                }
                if (batch.size != 0) {
                    writer.addRowBatch(batch);
                }
            }
        }
        Reader reader = OrcFile.createReader(path, OrcFile.readerOptions(conf));
        return new RoBnodeDictionary(reader);
    }

}
