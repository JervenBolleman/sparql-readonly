package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.common.type.HiveDecimal;
import org.apache.hadoop.hive.ql.exec.vector.DecimalColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.hadoop.hive.serde2.io.HiveDecimalWritable;
import org.apache.orc.CompressionKind;
import org.apache.orc.OrcFile;
import org.apache.orc.Reader;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;

import org.eclipse.rdf4j.model.impl.IntegerLiteral;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIntegerDict;
import static sib.swiss.swissprot.sparql.ro.dictionaries.RoIntegerDict.INT_LONG_VALUE;
import static sib.swiss.swissprot.sparql.ro.dictionaries.RoDictionary.SECOND_BYTE_TRUE;
public class TempIntegerDictionary extends TempDictionary {

    private final Set<BigInteger> set = new HashSet<>();

    public TempIntegerDictionary(File out) {
        super(out);
    }

    public void add(IntegerLiteral subject) throws IOException {
        try {
            if ((SECOND_BYTE_TRUE & subject.integerValue().longValueExact()) == SECOND_BYTE_TRUE) {
                set.add(subject.integerValue());
            }
        } catch (ArithmeticException e) {
            set.add(subject.integerValue());
        }

    }

    @Override
    public void close() throws IOException {
        try (ObjectOutputStream dos = new ObjectOutputStream(new FileOutputStream(new File(out, "temp")))) {
            dos.writeInt(set.size());
            for (BigInteger bi : set) {
                dos.writeObject(bi);
            }
        }
    }

    public RoIntegerDict load() throws IOException {
        Configuration conf = new Configuration();
        TypeDescription schema;
        schema = TypeDescription.createStruct()
                .addField(INT_LONG_VALUE, TypeDescription.createDecimal());
        VectorizedRowBatch batch = schema.createRowBatch();
        File file = new File(out.getAbsolutePath(), RoIntegerDict.PATH_NAME);
        if (file.exists()) {
            file.delete();
        }
        final Path path = new Path(out.getAbsolutePath(), RoIntegerDict.PATH_NAME);
        try (Writer writer = OrcFile.createWriter(path,
                OrcFile.writerOptions(conf)
                        .setSchema(schema)
                        .compress(CompressionKind.LZ4))) {

            DecimalColumnVector datatypesCol = (DecimalColumnVector) batch.cols[0];
            File f = new File(out, "temp");
            if (f.exists()) {
                ObjectInputStream in = new ObjectInputStream(new FileInputStream(f));
                int size = in.readInt();
                for (int i = 0; i < size; i++) {
                    {
                        if (batch.size == batch.getMaxSize()) {
                            writer.addRowBatch(batch);
                            batch.reset();
                        }
                        BigInteger read = (BigInteger) in.readObject();
                        datatypesCol.vector[batch.size] = new HiveDecimalWritable(HiveDecimal.create(read));
                    }
                }
            }
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(TempIntegerDictionary.class.getName()).log(Level.SEVERE, null, ex);
        }

        Reader reader = OrcFile.createReader(path, OrcFile.readerOptions(conf));
        return new RoIntegerDict(reader);
    }
}
