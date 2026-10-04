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

    private final SpillContext spill;
    private final Map<String, ProvisionalGroup<String>> byNamespace = new HashMap<>();
    private final List<ProvisionalGroup<String>> groups = new ArrayList<>();
    private final List<String> namespaceOfGroup = new ArrayList<>();
    private final List<DataDistribution> distributionOfGroup = new ArrayList<>();

    public TempIriDictionary(File out, Hardwood hardwood) {
        this(out, hardwood, SpillContext.unbounded(hardwood));
    }

    public TempIriDictionary(File out, Hardwood hardwood, SpillContext spill) {
        super(out, hardwood);
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
     * Writes a dictionary per namespace, the largest first, and assigns the
     * final ids.
     */
    public RoIriDictionary load() throws IOException {
        List<ProvisionalGroup<String>> biggestFirst = new ArrayList<>(groups);
        biggestFirst.sort(Comparator.comparingInt((ProvisionalGroup<String> g) -> g.provisionalIds()).reversed());

        RoNamespaces namespaces = new RoNamespaces();
        RoIriDictionary iriDictionary = new RoIriDictionary(namespaces);
        for (ProvisionalGroup<String> group : biggestFirst) {
            RoNamespace roNamespace = namespaces.add(null, namespaceOfGroup.get(group.index()));
            final long namespaceBits = roNamespace.getId() << RoNamespaces.BITS_FOR_NAMESPACES;
            DataDistribution data = distributionOfGroup.get(group.index());

            File iriFile = new File(out, roNamespace.getId() + "-iris");
            File bitmapFile = new File(out, roNamespace.getId() + "-prefixed-iris");
            File infoFile = new File(out, roNamespace.getId() + "-offsets");
            if (data.allNumericWithPrefix()) {
                // the id is the number after the prefix
                final int prefixLength = data.longestCommonPrefix == null ? 0 : data.longestCommonPrefix.length();
                final RoaringBitmap numbers = new RoaringBitmap();
                group.finish(localName -> {
                    int number = Integer.parseInt(localName.substring(prefixLength));
                    numbers.add(number);
                    return namespaceBits | number;
                });
                writeNamespaceWithPrefixedNumbersToDisk(numbers, bitmapFile, infoFile, data);
                iriDictionary.addPredixFollowedByBNumberDictionary(infoFile, bitmapFile, roNamespace);
            } else {
                // the id is the row in the sorted dictionary
                try (IdValueWriter writer = new IdValueWriter(iriFile, SCHEMA, RoIriDictionary.IRI_VALUE)) {
                    group.finish(localName -> namespaceBits | writer.add(localName));
                }
                iriDictionary.addBasicRoIriNamespaceDictionary(ParquetTable.open(hardwood, iriFile), roNamespace);
            }
        }
        return iriDictionary;
    }

    private void writeNamespaceWithPrefixedNumbersToDisk(RoaringBitmap numbers, File bitmapFile,
            File infoFile, DataDistribution data) throws IOException {
        String longestCommonPrefix = data.longestCommonPrefix == null ? "" : data.longestCommonPrefix;
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(bitmapFile))) {
            numbers.serialize(dos);
        }
        List<String> info = Arrays.asList(new String[]{
            String.valueOf(longestCommonPrefix.length()),
            longestCommonPrefix, String.valueOf(data.minLength),
            String.valueOf(data.maxLength)});
        Files.write(infoFile.toPath(), info, StandardCharsets.UTF_8);
    }
}
