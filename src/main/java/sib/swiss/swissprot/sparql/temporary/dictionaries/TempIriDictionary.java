package sib.swiss.swissprot.sparql.temporary.dictionaries;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.CacheWriter;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.roaringbitmap.RoaringBitmap;

import sib.swiss.swissprot.sparql.ro.FileNameEncoderFunctions;
import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;

public class TempIriDictionary extends TempDictionary {

    protected static class DataDistribution {

        private String longestCommonPrefix;
        private boolean hasCommonPrefix = true;
        private int minLength = Integer.MAX_VALUE;
        private int maxLength = Integer.MIN_VALUE;
        private boolean allNumericAfterLongestCommonPrefix = true;

        public String measure(String s) {
            if (s.isEmpty()) {
                hasCommonPrefix = false;
            }
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

    private class ClosingCacheWriter implements CacheWriter<File, FileOutputStream> {

        private final Map<File, FileOutputStream> fileFosToClose;

        private ClosingCacheWriter(Map<File, FileOutputStream> fileFosToClose) {
            this.fileFosToClose = fileFosToClose;

        }

        @Override
        public void write(File key, FileOutputStream value) {
        }

        @Override
        public void delete(File key, FileOutputStream value, RemovalCause cause) {
            fileFosToClose.put(key, value);
        }
    }
    private final Map<String, File> namespacesFile = new HashMap<>();
    private final Map<File, FileOutputStream> fileFosToClose = new ConcurrentHashMap<>();
    private final Cache<File, FileOutputStream> fileFosCache = Caffeine.<File, FileOutputStream>newBuilder()
            .maximumSize(1_000)
            .initialCapacity(1_000)
            .writer(new ClosingCacheWriter(fileFosToClose))
            .build(key -> new FileOutputStream(key, true));
    private final Cache<IRI, Boolean> iriCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .writer(new CacheWriter<IRI, Boolean>() {
                @Override
                public void write(IRI key, Boolean value) {

                }

                @Override
                public void delete(IRI key, Boolean value, RemovalCause cause) {

                }
            })
            .build();

    public TempIriDictionary(File out, Configuration conf) {
        super(out, conf);
    }

    public void add(IRI subject) throws IOException {
        if (iriCache.getIfPresent(subject) != null) {
            return;
        }
        iriCache.put(subject, Boolean.TRUE);
        String namespace = realNamespace(subject);
        File file = getFileForNamespace(namespace);

        FileOutputStream fos = getFileOutPUtStreamForFile(file);
        String localname = ensureLocalName(subject, namespace);
        fos.write(StandardCharsets.UTF_8.encode(localname).array());
        fos.write('\n');
        closeEvictedFileOutputStream();

    }

    private void closeEvictedFileOutputStream() throws IOException {
        for (Iterator<FileOutputStream> it = fileFosToClose.values().iterator(); it.hasNext();) {
            FileOutputStream toClose = it.next();
            toClose.close();
            it.remove();
        }
    }

    private static String ensureLocalName(IRI subject, String namespace) {
        String localname = subject.getLocalName();
        if (localname.isEmpty()) {
            localname = subject.getNamespace().substring(namespace.length());
        }
        return localname;
    }

    private FileOutputStream getFileOutPUtStreamForFile(File file) {
        FileOutputStream fos = fileFosCache.get(file, (f) -> {
            try {
                return new FileOutputStream(f, true);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(TempIriDictionary.class.getName()).log(Level.SEVERE, null, ex);
                throw new RuntimeException(ex);
            }
        });
        return fos;
    }

    public File getFileForNamespace(String namespace) throws IOException {
        File file = namespacesFile.get(namespace);
        if (file == null) {
            File dir = new File(out, "temp-"
                    + FileNameEncoderFunctions.encodeNamespace(namespace));
            dir.mkdir();

            File namespaceFile = new File(dir, "namespace");
            Files.write(namespaceFile.toPath(), Arrays.asList(namespace));
            file = new File(dir, "data");
            namespacesFile.put(namespace, file);

        }
        return file;
    }

    public String realNamespace(IRI subject) {
        String namespace = subject.getNamespace();
        if (subject.getLocalName().isEmpty()) {
            namespace = namespace.substring(0, namespace.lastIndexOf("/", namespace.length() - 2) + 1);
        }
        return namespace;
    }

    @Override
    public void close() throws IOException {
        fileFosCache.invalidateAll();
        closeEvictedFileOutputStream();
    }

    public RoIriDictionary load() throws IOException {
        final List<NamespaceFilePair> bigestNamespaceFirst = namespacesFile
                .entrySet()
                .stream()
                .map(s -> new NamespaceFilePair(s.getKey(), s.getValue())).sorted((f1, f2) -> Long
                .compare(f2.file.length(), f1.file.length()))
                .collect(Collectors.toList());

        RoNamespaces namespaces = new RoNamespaces();
        RoIriDictionary iriDictionary = new RoIriDictionary(namespaces);

        for (NamespaceFilePair namespaceTempFile : bigestNamespaceFirst) {
            String namespace = namespaceTempFile.namespace;
            RoNamespace roNamespace = namespaces.add(null,
                    namespace);
            File tempFile = namespaceTempFile.file;

            final Path iripath = new Path(out.getAbsolutePath(), roNamespace.getId() + "-iris");
            File lengthString = new File(out, roNamespace.getId() + "-prefixed-iris");

            File offsetsFile = new File(out, roNamespace.getId() + "-offsets");
            DataDistribution data = new DataDistribution();
            {
                final List<String> tempNodes = readTempFileIntoMemory(tempFile,
                        data);
                writeNamespacesToDisk(tempNodes, offsetsFile, lengthString, iripath,
                        data, conf);
            }
            writeToDisk(data, iriDictionary, offsetsFile, lengthString, roNamespace, iripath);
            removeTempFiles(tempFile);
        }
        return iriDictionary;
    }

    private void removeTempFiles(File tempFile) {
        File parentFile = tempFile.getParentFile();
        if (!new File(parentFile,"namespace").delete())
        {
            Logger.getAnonymousLogger().warning("Could not delete " + tempFile.getAbsolutePath());
        }
        if (!tempFile.delete()) {
            Logger.getAnonymousLogger().warning("Could not delete " + tempFile.getAbsolutePath());
        }
        if (!parentFile.delete()) {
            Logger.getAnonymousLogger().warning("Could not delete " + tempFile.getAbsolutePath());
        }
    }

    private void writeToDisk(DataDistribution data, RoIriDictionary iriDictionary, File offsetsFile, File lengthString, RoNamespace roNamespace, final Path iripath) throws IOException {
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

    private List<String> readTempFileIntoMemory(File tempFile,
            DataDistribution data) throws IOException {
        try (final Stream<String> lines = Files.lines(tempFile.toPath(), StandardCharsets.UTF_8)) {

            return lines
                    .sorted(Collator.getInstance(Locale.US))
                    .distinct()
                    .peek(data::measure)
                    .collect(Collectors.toList());
        }
    }

    private void writeNamespacesToDisk(final List<String> tempNodes, File offsetsFile, File lengthString, Path iriPath, DataDistribution data2, Configuration conf)
            throws IOException, FileNotFoundException {
        if (data2.allNumericWithPrefix()) {
            offsetsFile.createNewFile();
            lengthString.createNewFile();
            writeNamespaceWithPrefixedNumbersToDisk(tempNodes, lengthString,
                    offsetsFile, data2);
        } else {
            writeNamespaceWithLongIdsToDisk(tempNodes, iriPath, conf);
        }
    }

    private void writeNamespaceWithLongIdsToDisk(final List<String> tempNodes, Path iriPath, Configuration conf)
            throws IOException, FileNotFoundException {
        TypeDescription schema = TypeDescription
                .createStruct()
                .addField(RoDictionary.ID, TypeDescription.createLong())
                .addField(RoIriDictionary.IRI_VALUE, TypeDescription.createString());
        VectorizedRowBatch batch = schema.createRowBatch();

        Iterator<String> iterator = tempNodes.iterator();

        try (Writer writer = OrcFile.createWriter(iriPath,
                OrcFile.writerOptions(conf)
                        .bufferSize(500)
                        .compress(CompressionKind.LZO)
                        .bloomFilterColumns(RoIriDictionary.IRI_VALUE)
                        .rowIndexStride(1000)
                        .setSchema(schema))) {
            long i = 0;
            LongColumnVector idcol = (LongColumnVector) batch.cols[RoDictionary.ID_COL];
            BytesColumnVector col = (BytesColumnVector) batch.cols[RoDictionary.VALUE_COL];
            while (iterator.hasNext()) {
                if (batch.size == batch.getMaxSize()) {
                    writer.addRowBatch(batch);
                    batch.reset();
                    idcol = (LongColumnVector) batch.cols[RoDictionary.ID_COL];
                    col = (BytesColumnVector) batch.cols[RoDictionary.VALUE_COL];
                }

                idcol.vector[batch.size] = i++;
                final byte[] bytes = iterator.next().getBytes();
                col.setVal(batch.size++, bytes);
            }
            writer.addRowBatch(batch);
        }
    }

    private void writeNamespaceWithPrefixedNumbersToDisk(
            final List<String> tempNodes, File lengthString, File offsetsFile,
            DataDistribution data2) throws IOException, FileNotFoundException {
        RoaringBitmap map = new RoaringBitmap();
        int start = 0;
        String longestCommonPrefix = data2.longestCommonPrefix;
        if (longestCommonPrefix != null) {
            start = data2.longestCommonPrefix.length();
        } else {
            longestCommonPrefix = "";
        }
        for (int i = 0; i < tempNodes.size(); i++) {
            map.add(Integer.parseInt(tempNodes.get(i).substring(start)));
        }
        try (DataOutputStream dos = new DataOutputStream(
                new FileOutputStream(lengthString))) {
            map.serialize(dos);
        }
        List<String> info = Arrays.asList(new String[]{
            String.valueOf(longestCommonPrefix.length()),
            longestCommonPrefix, String.valueOf(data2.minLength),
            String.valueOf(data2.maxLength)});
        Files.write(offsetsFile.toPath(), info, StandardCharsets.UTF_8);

    }

    private final class NamespaceFilePair {

        private final String namespace;

        private final File file;

        public NamespaceFilePair(String s, File file) {
            this.namespace = s;
            this.file = file;
        }
    }

}
