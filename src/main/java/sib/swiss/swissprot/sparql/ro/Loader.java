package sib.swiss.swissprot.sparql.ro;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Bulk loads RDF files into a new read only store.
 *
 * <pre>
 * Loader &lt;store-directory&gt; &lt;rdf-file&gt;...
 * </pre>
 *
 * The system property {@code sparql.ro.loadMemoryMB} sets how much memory
 * the dictionaries may use before they spill to disk, by default a quarter of
 * the heap.
 */
public final class Loader {

    private Loader() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: Loader <store-directory> <rdf-file>...");
            System.exit(1);
        }
        // Large RDF/XML files use more XML entities than the JDK allows by default
        for (String limit : new String[]{"jdk.xml.maxGeneralEntitySizeLimit",
            "jdk.xml.totalEntitySizeLimit", "jdk.xml.entityExpansionLimit"}) {
            if (System.getProperty(limit) == null) {
                System.setProperty(limit, "0");
            }
        }
        File[] files = Arrays.stream(args, 1, args.length)
                .map(File::new)
                .toArray(File[]::new);
        for (File file : files) {
            if (!file.canRead()) {
                System.err.println("Can not read " + file);
                System.exit(1);
            }
        }
        RoStore store = new RoStore();
        store.setDataDir(new File(args[0]));
        String loadMemory = System.getProperty("sparql.ro.loadMemoryMB");
        if (loadMemory != null) {
            store.setLoadMemory(Long.parseLong(loadMemory) * 1024 * 1024);
        }
        store.init();
        try {
            store.load(files);
        } finally {
            store.shutDown();
        }
    }
}
