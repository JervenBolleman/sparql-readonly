package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.hardwood.Hardwood;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;

import sib.swiss.swissprot.sparql.ro.dictionaries.HashIndex;
import sib.swiss.swissprot.sparql.ro.dictionaries.CompressedValues;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoLiteralDict;

public class TempLiteralDictionary extends TempDictionary {

    /**
     * Literals with a language are grouped per language, the others per
     * datatype.
     */
    private final Map<String, ProvisionalGroup<String>> byLanguage = new HashMap<>();
    private final Map<IRI, ProvisionalGroup<String>> byDatatype = new HashMap<>();
    private final List<ProvisionalGroup<String>> groups = new ArrayList<>();
    private final List<Literal> exampleOfGroup = new ArrayList<>();
    private final SpillContext spill;

    public TempLiteralDictionary(File out, Hardwood hardwood) {
        this(out, hardwood, SpillContext.unbounded(hardwood));
    }

    public TempLiteralDictionary(File out, Hardwood hardwood, SpillContext spill) {
        super(out, hardwood);
        this.spill = spill;
    }

    /**
     * @return the provisional id of the literal, see {@link #finalId(long)}
     */
    public long add(Literal literal) throws IOException {
        final Optional<String> language = literal.getLanguage();
        ProvisionalGroup<String> group = language.isPresent()
                ? byLanguage.computeIfAbsent(language.get(), l -> newGroup(literal))
                : byDatatype.computeIfAbsent(literal.getDatatype(), d -> newGroup(literal));
        return group.add(literal.getLabel());
    }

    private ProvisionalGroup<String> newGroup(Literal literal) {
        ProvisionalGroup<String> group = new ProvisionalGroup<>(groups.size(), ProvisionalGroup.STRINGS, spill);
        groups.add(group);
        exampleOfGroup.add(literal);
        return group;
    }

    /**
     * @return the id in the dictionary {@link #load()} wrote, of the literal
     * that got this provisional id
     */
    public long finalId(long provisionalId) {
        return groups.get(ProvisionalGroup.group(provisionalId)).finalId(provisionalId);
    }

    @Override
    public void close() {
    }

    /**
     * Writes all literals into one dictionary, sorted per group, and its
     * index. The id of a literal is that of its label in the compressed
     * labels, see {@link CompressedValues}.
     */
    public RoLiteralDict load() throws IOException {
        File labels = new File(out, RoLiteralDict.PATH_NAME);
        File groupsFile = new File(out, RoLiteralDict.GROUPS_FILE);
        File index = new File(out, RoLiteralDict.INDEX_FILE);
        for (File file : List.of(labels, groupsFile, index)) {
            Files.deleteIfExists(file.toPath());
        }
        if (groups.isEmpty()) {
            // without literals there are no files
            return RoLiteralDict.open(out);
        }
        long values = 0;
        for (ProvisionalGroup<String> group : groups) {
            values += group.provisionalIds();
        }
        List<RoLiteralDict.Group> written = new ArrayList<>();
        // the hash of a label is of its group too: the index of the group
        final long[] groupIndex = new long[1];
        try (HashIndex.Writer hashes = new HashIndex.Writer(index, values);
                CompressedValues.Writer writer = new CompressedValues.Writer(labels,
                        (label, id) -> hashes.add(HashIndex.hash(label, groupIndex[0]), id))) {
            for (ProvisionalGroup<String> group : groups) {
                Literal example = exampleOfGroup.get(group.index());
                groupIndex[0] = written.size();
                written.add(new RoLiteralDict.Group(writer.position(), example.getDatatype(),
                        example.getLanguage().orElse(null)));
                // the index of a label in the group, its id once written
                group.finish(label -> writer.add(label.getBytes(StandardCharsets.UTF_8)));
                CompressedValues.Ids ids = writer.endGroup();
                group.mapFinalIds(ids::id);
            }
        }
        RoLiteralDict.writeGroups(groupsFile, written);
        return RoLiteralDict.open(out);
    }
}
