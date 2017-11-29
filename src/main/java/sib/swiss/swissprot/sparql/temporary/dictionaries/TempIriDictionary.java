package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Collator;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.orc.CompressionKind;
import org.apache.orc.OrcConf;
import org.apache.orc.OrcFile;
import org.apache.orc.Reader;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;

import org.eclipse.rdf4j.model.IRI;
import org.roaringbitmap.RoaringBitmap;

import sib.swiss.swissprot.sparql.ro.FileNameEncoderFunctions;
import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;

public class TempIriDictionary extends TempDictionary {

    protected static class DataDistribution {

        private String longestCommonPrefix;
        private boolean hasCommonPrefix = true;
        private int minLength = Integer.MAX_VALUE;
        private int maxLength = Integer.MIN_VALUE;
        private boolean allNumericAfterLongestCommonPrefix = true;

        public String measure(String s) {
            minLength = Math.min(minLength, s.length());
            maxLength = Math.max(maxLength, s.length());
            char[] a = s.toCharArray();
            if (longestCommonPrefix == null) {
                for (int i = a.length - 1; i >= 0; i--) {
                    char c = a[i];
                    if (!Character.isDigit(c) && c < 256) { // Only ascii
                        // codepoints for
                        // now
                        String prefix = s.substring(0, i + 1);
                        if (longestCommonPrefix == null) {
                            longestCommonPrefix = prefix;
                            if (i == a.length - 1) {
                                hasCommonPrefix = false;
                                allNumericAfterLongestCommonPrefix = false;
                            }
                        } else if (!prefix.equals(longestCommonPrefix)) {
                            hasCommonPrefix = false;
                            allNumericAfterLongestCommonPrefix = false;
                        }

                        return s;
                    }
                }
            } else if (allNumericAfterLongestCommonPrefix && hasCommonPrefix
                    && s.startsWith(longestCommonPrefix)) {
                try {
                    Integer.parseInt(s.substring(longestCommonPrefix.length()));
                } catch (NumberFormatException e) {
                    allNumericAfterLongestCommonPrefix = false;
                    hasCommonPrefix = false;
                }
            }
            return s;
        }

        public boolean allNumericWithPrefix() {
            return hasCommonPrefix && allNumericAfterLongestCommonPrefix
                    && minLength == maxLength;
        }
    }

    private final Map<String, FileOutputStream> namespaces_fos = new HashMap<>();
    private final Map<String, String> namespaces_prefix = new HashMap<>();

    public TempIriDictionary(File out) {
        super(out);
    }

    public void add(IRI subject) throws IOException {
        final String namespace = subject.getNamespace();
        FileOutputStream fos = namespaces_fos.get(namespace);
        if (fos == null) {
            File file = new File(out, "temp-"
                    + FileNameEncoderFunctions.encodeNamespace(namespace));
            Files.createFile(file.toPath());
            fos = new FileOutputStream(file);
            namespaces_fos.put(namespace, fos);
        }
        fos.write(
                StandardCharsets.UTF_8.encode(subject.getLocalName()).array());
        fos.write('\n');
    }

    @Override
    public void close() throws IOException {
        for (FileOutputStream fos : namespaces_fos.values()) {
            fos.close();
        }
    }

    public RoIriDictionary load() throws IOException {
        final List<NamespaceFilePair> bigestNamespaceFirst = namespaces_fos
                .keySet().stream()
                .map(s -> new NamespaceFilePair(s)).sorted((f1, f2) -> Long
                .compare(f1.file.length(), f2.file.length()))
                .collect(Collectors.toList());

        RoNamespaces namespaces = new RoNamespaces();
        RoIriDictionary iriDictionary = new RoIriDictionary(namespaces);

        Configuration conf = new Configuration();
        conf.set(OrcConf.SARG_COLUMNS.getHiveConfName(), RoIriDictionary.IRI_VALUE);
        conf.set(OrcConf.ENABLE_INDEXES.getHiveConfName(), "true");
        for (NamespaceFilePair namespaceTempFile : bigestNamespaceFirst) {
            String namespace = namespaceTempFile.namespace;
            RoNamespace roNamespace = namespaces.add(null,
                    namespace);
            File tempFile = namespaceTempFile.file;

            final Path iripath = new Path(out.getAbsolutePath(), roNamespace.getId() + "-iris");
            File lengthString = new File(out, roNamespace.getId() + "-prefixed-iris");
            lengthString.createNewFile();
            File offsetsFile = new File(roNamespace.getId() + "-offsets");
            offsetsFile.createNewFile();
            DataDistribution data = new DataDistribution();
            {
                final List<String> tempNodes = readTempFileIntoMemory(tempFile,
                        data);
                writeNamespacesToDisk(tempNodes, offsetsFile, lengthString, iripath,
                        data, conf);
            }
            if (data.allNumericWithPrefix()) {

                iriDictionary.addPredixFollowedByBNumberDictionary(offsetsFile, lengthString, roNamespace);
            } else {

                File iriFile = new File(out.getAbsolutePath(), roNamespace.getId() + "-iris");
                if (!iriFile.exists()) {
                    iriFile.createNewFile();
                }

                Reader irireader = OrcFile.createReader(iripath, OrcFile.readerOptions(conf));
                iriDictionary.addBasicRoIriNamespaceDictionary(irireader, roNamespace);
            }

        }
        return iriDictionary;
    }

    private List<String> readTempFileIntoMemory(File tempFile,
            DataDistribution data) throws IOException {

        return Files.lines(tempFile.toPath(), StandardCharsets.UTF_8)
                .sorted(Collator.getInstance(Locale.US))
                .distinct()
                .peek(data::measure)
                .collect(Collectors.toList());

    }

    private void writeNamespacesToDisk(final List<String> tempNodes, File offsetsFile, File lengthString, Path iriPath, DataDistribution data2, Configuration conf)
            throws IOException, FileNotFoundException {
        if (data2.allNumericAfterLongestCommonPrefix) {
            writeNamespaceWithPrefixedNumbersToDisk(tempNodes, lengthString,
                    offsetsFile, data2);
        } else {
            writeNamespaceWithLongIdsToDisk(tempNodes, iriPath, conf);
        }
    }

    private void writeNamespaceWithLongIdsToDisk(final List<String> tempNodes, Path iriPath, Configuration conf)
            throws IOException, FileNotFoundException {
        TypeDescription schema = TypeDescription.createStruct()
                .addField(RoIriDictionary.IRI_VALUE, TypeDescription.createString());
        VectorizedRowBatch batch = schema.createRowBatch();
        BytesColumnVector col = (BytesColumnVector) batch.cols[0];
        try (Writer writer = OrcFile.createWriter(iriPath,
                OrcFile.writerOptions(conf)
                        .setSchema(schema)
                        .compress(CompressionKind.NONE))) {
            Iterator<String> iterator = tempNodes
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

    private void writeNamespaceWithPrefixedNumbersToDisk(
            final List<String> tempNodes, File lengthString, File offsetsFile,
            DataDistribution data2) throws IOException, FileNotFoundException {
        RoaringBitmap map = new RoaringBitmap();
        int start = data2.longestCommonPrefix.length();
        for (int i = 0; i < tempNodes.size(); i++) {
            map.add(Integer.parseInt(tempNodes.get(i).substring(start)));
        }
        try (DataOutputStream dos = new DataOutputStream(
                new FileOutputStream(lengthString))) {
            map.serialize(dos);
        }
        List<String> info = Arrays.asList(new String[]{
            String.valueOf(data2.longestCommonPrefix.length()),
            data2.longestCommonPrefix, String.valueOf(data2.minLength),
            String.valueOf(data2.maxLength)});
        Files.write(offsetsFile.toPath(), info, StandardCharsets.UTF_8);

    }

    private final class NamespaceFilePair {

        private final String namespace;

        private final File file;

        public NamespaceFilePair(String s) {
            this.namespace = s;
            this.file = new File(out,
                    "temp-" + FileNameEncoderFunctions.encodeNamespace(s));
        }
    }

}
