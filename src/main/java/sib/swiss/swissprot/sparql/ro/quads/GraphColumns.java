package sib.swiss.swissprot.sparql.ro.quads;

import java.util.ArrayList;
import java.util.List;

import sib.swiss.swissprot.sparql.ro.ParquetTable;
import sib.swiss.swissprot.sparql.ro.Term;
import sib.swiss.swissprot.sparql.ro.dictionaries.RoIriDictionary;
import sib.swiss.swissprot.sparql.ro.values.RoBnode;
import sib.swiss.swissprot.sparql.ro.values.RoIri;
import sib.swiss.swissprot.sparql.ro.values.RoResource;

/**
 * The graphs of the triples of a list, as a boolean column per graph next to
 * the subject and object: true if the triple of that row is in the graph. A
 * list in the default graph only has none. A list with triples in named
 * graphs also has {@link #DEFAULT}, for the triples in the default graph.
 *
 * <p>
 * The column of a graph is named after it: {@code g_i<id>} for an IRI,
 * {@code g_b<id>} for a blank node. In a wide table the graph columns of a
 * list are prefixed with its object column, {@code p0_g_i<id>}, so that every
 * list in it keeps its own graphs.
 */
public final class GraphColumns {

    private static final String PREFIX = "g_";
    private static final String IRI = PREFIX + "i";
    private static final String BNODE = PREFIX + "b";
    /**
     * The column of the triples in the default graph.
     */
    public static final String DEFAULT = PREFIX + "default";

    private GraphColumns() {
    }

    /**
     * @param graph null for the default graph
     * @return the name of the column of the graph
     */
    static String name(RoResource graph) {
        if (graph == null) {
            return DEFAULT;
        } else if (graph instanceof RoIri) {
            return IRI + graph.getLongId();
        } else {
            return BNODE + graph.getLongId();
        }
    }

    /**
     * @param graph the graph as stored, null for the default graph
     * @return the name of the column of the graph
     */
    public static String name(Term graph) {
        if (graph == null) {
            return DEFAULT;
        }
        return (graph.kind() == Term.Kind.IRI ? IRI : BNODE) + graph.id();
    }

    /**
     * @return the graph of a column, null for the default graph
     */
    static RoResource graph(String name, RoIriDictionary iris) {
        if (name.equals(DEFAULT)) {
            return null;
        } else if (name.startsWith(IRI)) {
            return new RoIri(Long.parseLong(name.substring(IRI.length())), iris);
        } else {
            return new RoBnode(Long.parseLong(name.substring(BNODE.length())));
        }
    }

    /**
     * @return the graph columns of a list in a table: its own file, or with
     * the prefix of its object column in a wide table. Without the prefix.
     */
    static List<String> in(ParquetTable table, String prefix) {
        List<String> graphs = new ArrayList<>();
        for (String column : table.columnNames()) {
            if (column.startsWith(prefix + PREFIX)) {
                graphs.add(column.substring(prefix.length()));
            }
        }
        return graphs;
    }

    /**
     * @return the prefix of the graph columns of a list whose objects are in
     * this column of a wide table
     */
    static String prefix(String wideColumn) {
        return wideColumn + "_";
    }
}
