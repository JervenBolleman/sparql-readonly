package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.hardwood.Hardwood;
import dev.hardwood.metadata.LogicalType;
import dev.hardwood.metadata.PhysicalType;
import dev.hardwood.metadata.RepetitionType;
import dev.hardwood.schema.FileSchema;
import org.eclipse.rdf4j.model.IRI;
import org.roaringbitmap.RoaringBitmap;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.RoNamespace;
import sib.swiss.swissprot.sparql.ro.RoNamespaces;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoDictionary;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;

public class TempIriDictionary extends TempDictionary {

    private static final int BATCH_SIZE = 64 * 1024;
    private static final FileSchema SCHEMA = FileSchema.builder("iris")
            .addColumn(RoDictionary.ID, PhysicalType.INT64, RepetitionType.REQUIRED)
            .addColumn(RoIriDictionary.IRI_VALUE, PhysicalType.BYTE_ARRAY, RepetitionType.REQUIRED, new LogicalType.StringType())
            .build();

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

    private final Map<String, ProvisionalGroup<String>> byNamespace = new HashMap<>();
    private final List<ProvisionalGroup<String>> groups = new ArrayList<>();
    private final List<String> namespaceOfGroup = new ArrayList<>();

    public TempIriDictionary(File out, Hardwood hardwood) {
        super(out, hardwood);
    }

    /**
     * @return the provisional id of the IRI, see {@link #finalId(long)}
     */
    public long add(IRI iri) {
        String namespace = RoIriDictionary.storedNamespace(iri);
        ProvisionalGroup<String> group = byNamespace.get(namespace);
        if (group == null) {
            group = new ProvisionalGroup<>(groups.size());
            byNamespace.put(namespace, group);
            groups.add(group);
            namespaceOfGroup.add(namespace);
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
     * Writes a dictionary per namespace, the largest first, and assigns the
     * final ids.
     */
    public RoIriDictionary load() throws IOException {
        List<ProvisionalGroup<String>> biggestFirst = new ArrayList<>(groups);
        biggestFirst.sort(Comparator.comparingInt((ProvisionalGroup<String> g) -> g.size()).reversed());

        RoNamespaces namespaces = new RoNamespaces();
        RoIriDictionary iriDictionary = new RoIriDictionary(namespaces);
        for (ProvisionalGroup<String> group : biggestFirst) {
            RoNamespace roNamespace = namespaces.add(null, namespaceOfGroup.get(group.index()));
            final long namespaceBits = roNamespace.getId() << RoNamespaces.BITS_FOR_NAMESPACES;
            List<String> sorted = group.sorted();
            DataDistribution data = new DataDistribution();
            sorted.forEach(data::measure);

            File iriFile = new File(out, roNamespace.getId() + "-iris");
            File bitmapFile = new File(out, roNamespace.getId() + "-prefixed-iris");
            File infoFile = new File(out, roNamespace.getId() + "-offsets");
            if (data.allNumericWithPrefix()) {
                writeNamespaceWithPrefixedNumbersToDisk(sorted, bitmapFile, infoFile, data);
                // the id is the number after the prefix
                final int prefixLength = data.longestCommonPrefix == null ? 0 : data.longestCommonPrefix.length();
                group.assignFinalIds(localName -> namespaceBits
                        | Integer.parseInt(localName.substring(prefixLength)));
                iriDictionary.addPredixFollowedByBNumberDictionary(infoFile, bitmapFile, roNamespace);
            } else {
                writeNamespaceWithLongIdsToDisk(sorted, iriFile);
                // the id is the row in the sorted dictionary
                group.assignFinalIds(sorted, position -> namespaceBits | position);
                iriDictionary.addBasicRoIriNamespaceDictionary(ParquetTable.open(hardwood, iriFile), roNamespace);
            }
            group.releaseValues();
        }
        return iriDictionary;
    }

    private void writeNamespaceWithLongIdsToDisk(final List<String> sorted, File iriFile)
            throws IOException {
        try (ParquetTable.Writer writer = new ParquetTable.Writer(iriFile, SCHEMA, ParquetTable.LOOKUP_CONFIG)) {
            for (int start = 0; start < sorted.size(); start += BATCH_SIZE) {
                final int size = Math.min(BATCH_SIZE, sorted.size() - start);
                final long[] ids = new long[size];
                final byte[][] values = new byte[size][];
                for (int i = 0; i < size; i++) {
                    ids[i] = start + i;
                    values[i] = sorted.get(start + i).getBytes(StandardCharsets.UTF_8);
                }
                writer.writeBatch(b -> b.longs(RoDictionary.ID, ids)
                        .bytes(RoIriDictionary.IRI_VALUE, values));
            }
        }
    }

    private void writeNamespaceWithPrefixedNumbersToDisk(
            final List<String> sorted, File bitmapFile, File infoFile,
            DataDistribution data2) throws IOException {
        RoaringBitmap map = new RoaringBitmap();
        int start = 0;
        String longestCommonPrefix = data2.longestCommonPrefix;
        if (longestCommonPrefix != null) {
            start = data2.longestCommonPrefix.length();
        } else {
            longestCommonPrefix = "";
        }
        for (int i = 0; i < sorted.size(); i++) {
            map.add(Integer.parseInt(sorted.get(i).substring(start)));
        }
        try (DataOutputStream dos = new DataOutputStream(
                new FileOutputStream(bitmapFile))) {
            map.serialize(dos);
        }
        List<String> info = Arrays.asList(new String[]{
            String.valueOf(longestCommonPrefix.length()),
            longestCommonPrefix, String.valueOf(data2.minLength),
            String.valueOf(data2.maxLength)});
        Files.write(infoFile.toPath(), info, StandardCharsets.UTF_8);

    }
}
