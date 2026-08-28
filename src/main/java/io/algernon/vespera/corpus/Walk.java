package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.OccurrencePath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;

/**
 * One observation of a filesystem, producing file occurrences.
 *
 * <p>The walk is accountable for every entry beneath the corpus root: each one is recorded as a file
 * occurrence, recorded as a walk anomaly, or — a readable directory only — descended into. That is
 * what makes "excludes nothing" a claim anyone can check rather than assert, and {@link Outcome}
 * carries the three counts the check needs.
 *
 * <p>Two implementation choices are load-bearing rather than incidental.
 *
 * <p>It uses {@link Files#walkFileTree} and not {@code Files.walk}. The latter surfaces a failure
 * partway through as an {@code UncheckedIOException} from the terminal stream operation, with no
 * per-entry hook — so one unreadable subdirectory ends a traversal of hundreds of gigabytes and
 * loses everything after it. {@code walkFileTree} reports that entry through
 * {@link FileVisitor#visitFileFailed} and carries on, which is also the only place an anomaly can be
 * observed at all.
 *
 * <p>It does not follow links, and does not defend against concurrent modification. Soft links are
 * skipped and recorded (ADR-051); exclusive access to the corpus is assumed (ADR-050), so there are
 * no sharing-violation retries and no re-stat before use.
 */
public final class Walk {

    private Walk() {}

    /** Receives what the walk finds. Persistence is deliberately not this component's business. */
    public interface Observer {

        void fileOccurrence(OccurrencePath path, long sizeInBytes, Instant lastModified);

        /**
         * An entry that did not become a file occurrence: its kind (ADR-053) plus a nullable
         * free-text detail, for an operator to read, never for code to branch on.
         */
        void anomaly(String pathRendering, WalkAnomalyKind kind, String detail);
    }

    /**
     * What one walk observed. {@code entriesSeen} counts every entry beneath the root, so the
     * accounting identity is {@code entriesSeen == occurrences + anomalies + directoriesEntered - 1}
     * — the root itself being entered but not an entry beneath itself.
     */
    public record Outcome(
            Path corpusRoot,
            long entriesSeen,
            long directoriesEntered,
            long occurrences,
            long anomalies,
            boolean finished) {}

    /**
     * Walks {@code corpusRoot}, reporting to {@code observer}.
     *
     * <p>The root is canonicalised once here, because it is the one component a person typed: it can
     * carry the wrong case, a {@code ..}, a trailing separator, or a mapped drive. Entry paths
     * beneath it are never canonicalised — for an entry found by traversal the directory entry
     * already is the authoritative on-disk spelling.
     *
     * @throws IllegalArgumentException if the root does not exist or is not a directory
     */
    public static Outcome walk(Path corpusRoot, Observer observer) throws IOException {
        Path root;
        try {
            root = corpusRoot.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("corpus root cannot be resolved: " + corpusRoot, e);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("corpus root is not a directory: " + root);
        }

        Counter counter = new Counter();
        boolean finished;
        try {
            Files.walkFileTree(root, new Visitor(root, observer, counter));
            finished = true;
        } catch (IOException e) {
            // A walk that stopped early must never look complete: a partial walk reported as
            // finished curates a fraction of the corpus and reports success.
            observer.anomaly(render(root, root), WalkAnomalyKind.UNPROCESSABLE, "the walk did not finish: " + e);
            counter.anomalies++;
            finished = false;
        }

        return new Outcome(
                root, counter.entriesSeen, counter.directoriesEntered, counter.occurrences, counter.anomalies, finished);
    }

    private static final class Counter {
        long entriesSeen;
        long directoriesEntered;
        long occurrences;
        long anomalies;
    }

    /**
     * A rendering safe to store and show. It goes through the same UTF-8 round trip the stored path
     * does, so an anomaly about an unstorable name is not itself unstorable.
     */
    private static String render(Path root, Path entry) {
        String raw;
        try {
            raw = root.equals(entry) ? entry.toString() : root.relativize(entry).toString();
        } catch (IllegalArgumentException e) {
            raw = entry.toString();
        }
        return new String(raw.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private record Visitor(Path root, Observer observer, Counter counter) implements FileVisitor<Path> {

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (!dir.equals(root)) {
                counter.entriesSeen++;
                if (isSoftLink(attrs)) {
                    report(dir, WalkAnomalyKind.SOFT_LINK_NOT_FOLLOWED, "a soft link was skipped rather than followed");
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }
            counter.directoriesEntered++;
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            counter.entriesSeen++;

            if (isSoftLink(attrs)) {
                report(file, WalkAnomalyKind.SOFT_LINK_NOT_FOLLOWED, "a soft link was skipped rather than followed");
                return FileVisitResult.CONTINUE;
            }
            if (!attrs.isRegularFile()) {
                report(file, WalkAnomalyKind.UNPROCESSABLE, "not a regular file");
                return FileVisitResult.CONTINUE;
            }

            switch (OccurrencePath.relativize(root, file)) {
                case OccurrencePath.Stored(OccurrencePath path) -> {
                    counter.occurrences++;
                    observer.fileOccurrence(path, attrs.size(), attrs.lastModifiedTime().toInstant());
                }
                case OccurrencePath.Unstorable(String lossyRendering, String reason) -> {
                    counter.anomalies++;
                    observer.anomaly(lossyRendering, WalkAnomalyKind.UNENCODABLE_PATH, reason);
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            counter.entriesSeen++;
            report(file, WalkAnomalyKind.UNPROCESSABLE, exc.getClass().getSimpleName() + ": " + exc.getMessage());
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
            if (exc != null) {
                // The directory was entered but its listing did not complete, so entries below it
                // may be missing without any of them having been seen.
                report(dir, WalkAnomalyKind.UNPROCESSABLE, "listing this directory did not complete: " + exc);
            }
            return FileVisitResult.CONTINUE;
        }

        /**
         * A junction and a volume mount point share a reparse tag and read as {@code isOther()},
         * not {@code isSymbolicLink()} — so both must be tested, or a junction is silently treated
         * as an ordinary entry.
         */
        private static boolean isSoftLink(BasicFileAttributes attrs) {
            return attrs.isSymbolicLink() || attrs.isOther();
        }

        private void report(Path entry, WalkAnomalyKind kind, String detail) {
            counter.anomalies++;
            observer.anomaly(render(root, entry), kind, detail);
        }
    }
}
