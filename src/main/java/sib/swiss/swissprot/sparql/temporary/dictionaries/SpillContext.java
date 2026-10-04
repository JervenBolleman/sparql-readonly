package sib.swiss.swissprot.sparql.temporary.dictionaries;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import dev.hardwood.Hardwood;

/**
 * What the dictionary groups of one load share: how much memory their values
 * may use before the largest group spills a sorted run to disk, where the runs
 * go, and the thread that merges runs in the background.
 *
 * <p>
 * Only the parsing thread adds values, so the memory accounting is not
 * synchronized. The runs of a group are shared with the merge thread, see
 * {@link ProvisionalGroup}.
 */
public final class SpillContext implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(SpillContext.class.getName());

    private final long maxBytes;
    private final File dir;
    private final Hardwood hardwood;
    private final ExecutorService merger;
    private final List<ProvisionalGroup<?>> groups = new ArrayList<>();
    private final AtomicInteger runs = new AtomicInteger();
    private long usedBytes;

    /**
     * @param maxBytes the estimated memory the values of all groups may use
     * @param dir where to write runs, created if needed and deleted on close
     * @param backgroundMerges whether a thread merges runs while parsing goes
     * on, otherwise they are only merged when a dictionary is finished
     */
    public SpillContext(long maxBytes, File dir, Hardwood hardwood, boolean backgroundMerges) {
        this.maxBytes = maxBytes;
        this.dir = dir;
        this.hardwood = hardwood;
        this.merger = backgroundMerges ? Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "dictionary-run-merger");
            thread.setDaemon(true);
            return thread;
        }) : null;
    }

    /**
     * Never spills.
     */
    public static SpillContext unbounded(Hardwood hardwood) {
        return new SpillContext(Long.MAX_VALUE, null, hardwood, false);
    }

    Hardwood hardwood() {
        return hardwood;
    }

    void register(ProvisionalGroup<?> group) {
        groups.add(group);
    }

    /**
     * A group took more memory, spills the largest groups until at most half
     * of the budget is used, so that spills come in batches.
     */
    void added(long bytes) throws IOException {
        usedBytes += bytes;
        if (usedBytes > maxBytes) {
            while (usedBytes > maxBytes / 2) {
                ProvisionalGroup<?> largest = groups.stream()
                        .max(Comparator.comparingLong(ProvisionalGroup::bytesInMemory))
                        .orElseThrow();
                if (largest.bytesInMemory() == 0) {
                    return;
                }
                largest.spill();
            }
        }
    }

    void released(long bytes) {
        usedBytes -= bytes;
    }

    File newRunFile() throws IOException {
        Files.createDirectories(dir.toPath());
        return new File(dir, "run-" + runs.incrementAndGet());
    }

    /**
     * @return null when merges only happen on finishing
     */
    Future<?> submitMerge(Runnable merge) {
        return merger == null ? null : merger.submit(merge);
    }

    int runFilesWritten() {
        return runs.get();
    }

    @Override
    public void close() throws IOException {
        if (runs.get() > 0) {
            LOGGER.log(Level.INFO, "Dictionaries spilled and merged {0} runs", runs.get());
        }
        if (merger != null) {
            merger.shutdownNow();
            try {
                merger.awaitTermination(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (dir != null && dir.exists()) {
            try (Stream<java.nio.file.Path> files = Files.walk(dir.toPath())) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
}
