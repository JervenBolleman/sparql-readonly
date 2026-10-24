package sib.swiss.swissprot.sparql.ro.dictionaries;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.util.Values;

import sib.swiss.swissprot.sparql.ro.values.RoLiteral;
import sib.swiss.swissprot.sparql.ro.values.RoSimpleLiteral;

/**
 * The literals that are not stored as themselves in the triple lists. Their
 * labels are in {@link CompressedValues}, grouped by language, or by
 * datatype for literals without one, and sorted within a group. The id of a
 * literal is the id of its label there. The groups are in a small file of
 * their own, {@link #GROUPS_FILE}, and a {@link HashIndex} finds the id of a
 * literal.
 */
public class RoLiteralDict implements Closeable {

    /**
     * The labels.
     */
    public static final String PATH_NAME = "literals";
    /**
     * Per group, in the order of the labels: the offset where its labels
     * start, its datatype and its language, if it has one.
     */
    public static final String GROUPS_FILE = "literal-groups";
    /**
     * The {@link HashIndex} of the labels.
     */
    public static final String INDEX_FILE = "literals-index";

    /**
     * A datatype or language and the offset where its labels start.
     */
    public record Group(long start, IRI datatype, String language) {
    }

    private final CompressedValues labels;
    private final HashIndex index;
    private final long[] starts;
    private final List<Group> groups;
    private final Map<String, Integer> byLanguage = new HashMap<>();
    private final Map<String, Integer> byDatatype = new HashMap<>();

    private RoLiteralDict(CompressedValues labels, HashIndex index, List<Group> groups) {
        this.labels = labels;
        this.index = index;
        this.groups = groups;
        this.starts = new long[groups.size()];
        for (int g = 0; g < groups.size(); g++) {
            Group group = groups.get(g);
            starts[g] = CompressedValues.idAt(group.start());
            if (group.language() != null) {
                byLanguage.put(group.language(), g);
            } else {
                byDatatype.put(group.datatype().stringValue(), g);
            }
        }
    }

    /**
     * @return the dictionary in the directory, empty if it has none
     */
    public static RoLiteralDict open(File directory) throws IOException {
        List<Group> groups = readGroups(new File(directory, GROUPS_FILE));
        CompressedValues labels = CompressedValues.open(new File(directory, PATH_NAME));
        try {
            return new RoLiteralDict(labels, HashIndex.open(new File(directory, INDEX_FILE)), groups);
        } catch (IOException | RuntimeException e) {
            labels.close();
            throw e;
        }
    }

    private static List<Group> readGroups(File file) throws IOException {
        List<Group> groups = new ArrayList<>();
        if (!file.exists()) {
            return groups;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int size = in.readInt();
            for (int g = 0; g < size; g++) {
                long start = in.readLong();
                IRI datatype = Values.iri(in.readUTF());
                String language = in.readBoolean() ? in.readUTF() : null;
                groups.add(new Group(start, datatype, language));
            }
        }
        return groups;
    }

    /**
     * Writes the groups, see {@link #GROUPS_FILE}.
     */
    public static void writeGroups(File file, List<Group> groups) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeInt(groups.size());
            for (Group group : groups) {
                out.writeLong(group.start());
                out.writeUTF(group.datatype().stringValue());
                out.writeBoolean(group.language() != null);
                if (group.language() != null) {
                    out.writeUTF(group.language());
                }
            }
        }
    }

    /**
     * @return the group of a literal for the {@link HashIndex}: its language,
     * or its datatype if it has none, null if no stored literal has it
     */
    private Integer groupOf(Literal value) {
        Optional<String> language = value.getLanguage();
        return language.isPresent() ? byLanguage.get(language.get())
                : byDatatype.get(value.getDatatype().stringValue());
    }

    public Optional<RoLiteral> find(Literal value) {
        Integer group = groupOf(value);
        if (group == null) {
            return Optional.empty();
        }
        byte[] label = value.getLabel().getBytes(StandardCharsets.UTF_8);
        long start = starts[group];
        long end = group + 1 < starts.length ? starts[group + 1] : CompressedValues.idAt(labels.size());
        long id = index.find(HashIndex.hash(label, group),
                at -> at >= start && at < end && labels.equalsAt(at, label));
        return id < 0 ? Optional.empty() : Optional.of(new RoSimpleLiteral(id, this));
    }

    /**
     * @return the group of the literal with this id
     */
    private Group group(long id) {
        int at = Arrays.binarySearch(starts, id);
        // between two starts: the group of the one before
        return groups.get(at >= 0 ? at : -at - 2);
    }

    public String stringValue(long id) {
        return labels.string(id);
    }

    public IRI dataType(long id) {
        return group(id).datatype();
    }

    public RoLiteral get(long id) {
        return new RoSimpleLiteral(id, this);
    }

    public Optional<String> language(long id) {
        return Optional.ofNullable(group(id).language());
    }

    @Override
    public void close() throws IOException {
        labels.close();
        index.close();
    }
}
