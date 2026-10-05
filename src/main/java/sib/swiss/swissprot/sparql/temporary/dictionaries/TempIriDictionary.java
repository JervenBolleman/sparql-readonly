package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.hardwood.Hardwood;
import org.eclipse.rdf4j.model.IRI;
import org.roaringbitmap.RoaringBitmap;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.dictionaries.HashIndex;
import sib.swiss.swissprot.sparql.ro.dictionaries.LinearValues;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;

public class TempIriDictionary extends TempDictionary {

    /**
     * The file of the local names of the namespaces that are not a prefix and
     * a number, see {@link LinearValues}.
     */
    public static final String IRIS_FILE = "iris";
    /**
     * The {@link HashIndex} of {@link #IRIS_FILE}.
     */
    public static final String IRIS_INDEX_FILE = "iris-index";
    /**
     * The file of the namespaces.
     */
    public static final String NAMESPACES_FILE = "iri-namespaces";

    protected static class DataDistribution {

        private String longestCommonPrefix;
        private boolean hasCommonPrefix = true;
        private int minLength = Integer.MAX_VALUE;
        private int maxLength = Integer.MIN_VALUE;
        private boolean allNumericAfterLongestCommonPrefix = true;
        private boolean seenOnlyDigits = false;

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
                            // earlier values had no prefix at all
                            if (i == a.length - 1 || seenOnlyDigits
                                    || !fitsInInt(s.substring(i + 1))) {
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
                seenOnlyDigits = true;
                if (!fitsInInt(s)) {
                    allNumericAfterLongestCommonPrefix = false;
                    hasCommonPrefix = false;
                }
            } else if (allNumericAfterLongestCommonPrefix && hasCommonPrefix) {
                if (!s.startsWith(longestCommonPrefix)) {
                    allNumericAfterLongestCommonPrefix = false;
                    hasCommonPrefix = false;
                    return s;
                }
                if (!fitsInInt(s.substring(longestCommonPrefix.length()))) {
                    allNumericAfterLongestCommonPrefix = false;
                    hasCommonPrefix = false;
                }
            }
            return s;
        }

        /**
         * The numbers are stored in an int bitmap.
         */
        private static boolean fitsInInt(String number) {
            try {
                Integer.parseInt(number);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        public boolean allNumericWithPrefix() {
            return hasCommonPrefix && allNumericAfterLongestCommonPrefix
                    && minLength == maxLength;
        }
    }

    private final SpillContext spill;
    private final File namespacesDir;
    private final Map<String, ProvisionalGroup<String>> byNamespace = new HashMap<>();
    private final List<ProvisionalGroup<String>> groups = new ArrayList<>();
    private final List<String> namespaceOfGroup = new ArrayList<>();
    private final List<DataDistribution> distributionOfGroup = new ArrayList<>();

    public TempIriDictionary(File out, Hardwood hardwood) {
        this(out, out, hardwood, SpillContext.unbounded(hardwood));
    }

    /**
     * @param out where the local names are written, see {@link #IRIS_FILE}
     * @param namespacesDir where the namespaces are written, see
     * {@link #NAMESPACES_FILE}
     */
    public TempIriDictionary(File out, File namespacesDir, Hardwood hardwood, SpillContext spill) {
        super(out, hardwood);
        this.namespacesDir = namespacesDir;
        this.spill = spill;
    }

    /**
     * @return the provisional id of the IRI, see {@link #finalId(long)}
     */
    public long add(IRI iri) throws IOException {
        String namespace = RoIriDictionary.storedNamespace(iri);
        ProvisionalGroup<String> group = byNamespace.get(namespace);
        if (group == null) {
            // measured as values come in: the kind of dictionary has to be
            // known before the merged values are written
            DataDistribution distribution = new DataDistribution();
            group = new ProvisionalGroup<>(groups.size(), ProvisionalGroup.STRINGS, spill,
                    distribution::measure);
            byNamespace.put(namespace, group);
            groups.add(group);
            namespaceOfGroup.add(namespace);
            distributionOfGroup.add(distribution);
        }
        return group.add(RoIriDictionary.storedLocalName(iri));
    }

    /**
     * @return the id in the dictionary {@link #load()} wrote, of the IRI that
     * got this provisional id
     */
    public long finalId(long provisionalId) {
        return groups.get(ProvisionalGroup.group(provisionalId)).finalId(provisionalId);
    }

    @Override
    public void close() {
    }

    /**
     * Writes the namespaces, the largest first, and the local names of all of
     * them into one file, and assigns the final ids.
     */
    public RoIriDictionary load() throws IOException {
        List<ProvisionalGroup<String>> biggestFirst = new ArrayList<>(groups);
        biggestFirst.sort(Comparator.comparingInt((ProvisionalGroup<String> g) -> g.provisionalIds()).reversed());

        File irisFile = new File(out, IRIS_FILE);
        File indexFile = new File(out, IRIS_INDEX_FILE);
        File namespacesFile = new File(namespacesDir, NAMESPACES_FILE);
        Files.createDirectories(namespacesDir.toPath());
        Files.deleteIfExists(irisFile.toPath());
        Files.deleteIfExists(indexFile.toPath());
        NamespaceRows namespaces = new NamespaceRows(biggestFirst.size());
        long names = 0;
        for (ProvisionalGroup<String> group : groups) {
            if (!distributionOfGroup.get(group.index()).allNumericWithPrefix()) {
                names += group.provisionalIds();
            }
        }
        try (NameWriter iris = new NameWriter(irisFile, indexFile, names)) {
            for (int id = 0; id < biggestFirst.size(); id++) {
                ProvisionalGroup<String> group = biggestFirst.get(id);
                final long namespaceBits = ((long) id) << RoNamespaces.BITS_FOR_NAMESPACES;
                final long namespaceId = id;
                DataDistribution data = distributionOfGroup.get(group.index());
                String namespace = namespaceOfGroup.get(group.index());
                if (data.allNumericWithPrefix()) {
                    // the id is the number after the prefix
                    final String prefix = data.longestCommonPrefix == null ? "" : data.longestCommonPrefix;
                    final RoaringBitmap numbers = new RoaringBitmap();
                    group.finish(localName -> {
                        int number = Integer.parseInt(localName.substring(prefix.length()));
                        numbers.add(number);
                        return namespaceBits | number;
                    });
                    namespaces.addNumbers(id, namespace, prefix, data.minLength, numbers);
                } else {
                    // the id is the offset of the name from the first name of
                    // the namespace
                    final long start = iris.position();
                    group.finish(localName -> namespaceBits | iris.add(namespaceId, start, localName));
                    namespaces.addNames(id, namespace, start, iris.position());
                }
            }
        }
        namespaces.write(namespacesFile);
        return RoIriDictionary.open(namespacesFile, irisFile, indexFile, hardwood);
    }

    /**
     * The rows of the namespace table, see
     * {@link RoIriDictionary#NAMESPACES_SCHEMA}.
     */
    private static final class NamespaceRows {

        private final long[] ids;
        private final byte[][] namespaces;
        private final byte[][] prefixes;
        private final int[] lengths;
        private final byte[][] numbers;
        private final boolean[] names;
        private final long[] starts;
        private final long[] ends;
        private int size;

        private NamespaceRows(int capacity) {
            ids = new long[capacity];
            namespaces = new byte[capacity][];
            prefixes = new byte[capacity][];
            lengths = new int[capacity];
            numbers = new byte[capacity][];
            names = new boolean[capacity];
            starts = new long[capacity];
            ends = new long[capacity];
        }

        /**
         * @param start the offset of the first name of the namespace
         * @param end the offset after its last name
         */
        void addNames(long id, String namespace, long start, long end) {
            ids[size] = id;
            namespaces[size] = namespace.getBytes(StandardCharsets.UTF_8);
            names[size] = true;
            starts[size] = start;
            ends[size] = end;
            size++;
        }

        void addNumbers(long id, String namespace, String prefix, int length, RoaringBitmap bitmap) {
            ids[size] = id;
            namespaces[size] = namespace.getBytes(StandardCharsets.UTF_8);
            prefixes[size] = prefix.getBytes(StandardCharsets.UTF_8);
            lengths[size] = length;
            bitmap.runOptimize();
            ByteBuffer serialized = ByteBuffer.allocate(bitmap.serializedSizeInBytes());
            bitmap.serialize(serialized);
            numbers[size] = serialized.array();
            size++;
        }

        /**
         * @return true for the namespaces of numbers, which have no names
         */
        private boolean[] numbersNotNames() {
            boolean[] numbers = new boolean[names.length];
            for (int i = 0; i < names.length; i++) {
                numbers[i] = !names[i];
            }
            return numbers;
        }

        void write(File file) throws IOException {
            if (size == 0) {
                Files.deleteIfExists(file.toPath());
                return;
            }
            try (ParquetTable.Writer writer = new ParquetTable.Writer(file, RoIriDictionary.NAMESPACES_SCHEMA)) {
                writer.writeBatch(b -> b.longs(RoIriDictionary.NAMESPACE_ID, ids)
                        .bytes(RoIriDictionary.NAMESPACE_IRI, namespaces)
                        .bytes(RoIriDictionary.NUMBER_PREFIX, prefixes, names)
                        .ints(RoIriDictionary.NAME_LENGTH, lengths, names)
                        .bytes(RoIriDictionary.NUMBERS, numbers, names)
                        .longs(RoIriDictionary.NAMES_START, starts, numbersNotNames())
                        .longs(RoIriDictionary.NAMES_END, ends, numbersNotNames()));
            }
        }
    }

    /**
     * Writes the local names, see {@link LinearValues}, and their
     * {@link HashIndex}. Without names there are no files.
     */
    private static final class NameWriter implements java.io.Closeable {

        private final File file;
        private final LinearValues.Writer names;
        private final HashIndex.Writer index;
        private boolean any;

        private NameWriter(File file, File indexFile, long expected) throws IOException {
            this.file = file;
            this.names = new LinearValues.Writer(file);
            this.index = expected == 0 ? null : new HashIndex.Writer(indexFile, expected);
        }

        /**
         * @param start the offset of the first name of the namespace
         * @return the local id of the name: its offset from start
         */
        long add(long namespace, long start, String localName) throws IOException {
            byte[] bytes = localName.getBytes(StandardCharsets.UTF_8);
            long offset = names.add(bytes);
            index.add(HashIndex.hash(bytes, namespace), offset);
            any = true;
            long local = offset - start;
            if (local > 0xFFFF_FFFFL) {
                throw new IOException("The local names of namespace " + namespace
                        + " take more than 4 GiB, which a local id can not address");
            }
            return local;
        }

        long position() {
            return names.position();
        }

        @Override
        public void close() throws IOException {
            names.close();
            if (index != null) {
                index.close();
            }
            if (!any) {
                Files.deleteIfExists(file.toPath());
            }
        }
    }
}
