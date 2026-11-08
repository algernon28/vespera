package io.algernon.vespera.engine.corpus;

import io.algernon.vespera.engine.ledger.OccurrencePath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One observation of a filesystem, producing file occurrences.
 *
 * <p>The walk is accountable for every entry beneath the root it was given: each one is recorded as
 * a file occurrence, recorded as a walk anomaly, or — a readable directory only — descended into, or
 * counted as a folder the profile's {@code foldersNotWalked} names and not entered (ADR-340 section 8.2).
 * That is what makes "excludes nothing" a claim anyone can check rather than assert, and
 * {@link Progress} carries the counts the check needs (ADR-056).
 *
 * <p>The root is a root and not a corpus root: the same instrument walks a seed folder, and only its
 * naming ever pretended otherwise (ADR-064).
 *
 * <p>Three implementation choices are load-bearing rather than incidental.
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
 *
 * <p>It is resumable, not atomic (ADR-055). A walk that stopped announces the last directory it
 * completed, and a later walk over the same root is handed that {@link Checkpoint} and skips past it
 * — entering the ancestors on the way down without recounting them, and skipping every completed
 * subtree whole. Only stable traversal order makes that sound, so the checkpoint carries the path it
 * pointed at and the walk fails loudly if the tree no longer agrees.
 */
public final class Walk {

    private Walk() {}

    /** Receives what the walk finds. Persistence is deliberately not this component's business. */
    interface Observer {

        void fileOccurrence(OccurrencePath path, long sizeInBytes, Instant lastModified, Instant creationTime);

        /**
         * An entry that did not become a file occurrence: its kind (ADR-053) plus a nullable
         * free-text detail, for an operator to read, never for code to branch on.
         */
        void anomaly(String pathRendering, WalkAnomalyKind kind, String detail);

        /**
         * A point the walk could be resumed from: every entry up to and including {@code at}'s
         * subtree has been reported, and {@code progress} is what the walk had counted by then.
         *
         * <p>Offered at every completed directory, which is far more often than anything wants to
         * write. Whether a given offer becomes durable is the observer's call, because it is the
         * observer that knows what a commit costs — and an offer not taken costs only the entries
         * that have to be walked again.
         *
         * <p>A no-op by default: an observer that holds nothing has nothing to resume.
         */
        default void checkpoint(Checkpoint at, Progress progress) {}

        /**
         * An entry beneath the root has been counted and its handling is done: recorded, reported as an
         * anomaly, or, for a directory, entered. {@code progress} is what the walk had counted by then,
         * this session's only. Called once for every entry the walk counts and never for the root, nor
         * for an entry an earlier session already counted (ADR-192 section 6).
         *
         * <p>This is what the census's running line is written from, so that a flat root or one large
         * directory is not silent until the walk ends. It is not a point to resume from and an observer
         * commits nothing on it.
         *
         * <p>A no-op by default: an observer that reports nothing has nothing to say.
         */
        default void entryWalked(Progress progress) {}

        /**
         * A folder of the corpus the walk met and did not enter because the profile's {@code foldersNotWalked} names it
         * (ADR-340 section 8.3), by its path from the root with {@code /} between its elements. Written with the entries
         * around it, so that a walk killed before its checkpoint takes it back with them.
         */
        default void folderNotEntered(String path) {}
    }

    /**
     * What a walk has counted. {@code entriesSeen} counts every entry beneath the root, so the
     * accounting identity is
     * {@code entriesSeen == occurrences + anomalies + directoriesEntered + foldersNotWalked - 1}
     * — the root itself being entered but not an entry beneath itself, and a folder not walked being
     * an entry the walk saw and did not enter (ADR-340 section 8.3).
     *
     * <p>These are one session's counts. Across a resumed walk they are added to what earlier
     * sessions recorded, which is why a resumed session counts neither the root nor anything it
     * skipped: those were counted once already.
     */
    record Progress(long entriesSeen, long directoriesEntered, long occurrences, long anomalies, long foldersNotWalked) {

        /** The counts of a walk that met no folder it did not enter. */
        Progress(long entriesSeen, long directoriesEntered, long occurrences, long anomalies) {
            this(entriesSeen, directoriesEntered, occurrences, anomalies, 0);
        }

        /**
         * Whether the accounting identity holds: every entry met is one of the three things.
         *
         * <p>True of a whole walk's counts, which for a resumed walk means the sum across its
         * sessions — a resumed session on its own counts neither the root nor anything it skipped.
         */
        public boolean accountsForEveryEntry() {
            return unaccountedFor() == 0;
        }

        /**
         * How many entries the identity cannot place: positive where the walk met more than it
         * reported, negative where it reported more than it met.
         *
         * <p>The identity is written out here and nowhere else. Every other site — the check at the
         * finish, the failure it raises — asks this record rather than restating the arithmetic,
         * because a rule spelled out twice is a rule that can come apart.
         */
        public long unaccountedFor() {
            return entriesSeen - (occurrences + anomalies + directoriesEntered + foldersNotWalked - 1);
        }
    }

    /**
     * What one session of a walk observed.
     *
     * @param stoppedBecause why the traversal did not finish, or null where it did. An unfinished
     *     walk is a state to resume from rather than an anomaly to record (ADR-055), so this is
     *     reported here rather than through {@link Observer#anomaly}.
     */
    record Outcome(Path root, Progress progress, boolean finished, String stoppedBecause) {}

    /**
     * The one canonicalisation a walk performs, exposed because deciding whether a walk of this root
     * is already under way needs the same spelling the walk will record (ADR-055).
     *
     * <p>The root is the one component a person typed: it can carry the wrong case, a {@code ..}, a
     * trailing separator, or a mapped drive. Entries beneath it are never canonicalised — for an
     * entry found by traversal the directory entry already is the authoritative on-disk spelling.
     *
     * @throws IllegalArgumentException if the root does not exist or is not a directory
     */
    public static Path canonicalRoot(Path root) {
        Path canonical;
        try {
            canonical = root.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("root cannot be resolved: " + root, e);
        }
        if (!Files.isDirectory(canonical)) {
            throw new IllegalArgumentException("root is not a directory: " + canonical);
        }
        return canonical;
    }

    /** Walks {@code root} from the beginning, reporting to {@code observer}. */
    static Outcome walk(Path root, Observer observer) throws IOException {
        return walk(root, observer, (Checkpoint) null);
    }

    /**
     * Walks {@code root}, reporting to {@code observer}, skipping whatever {@code resumeFrom} says
     * an earlier session already recorded.
     *
     * @param resumeFrom what an earlier session already recorded, or {@code null} to walk the whole tree
     * @throws IllegalArgumentException if the root does not exist or is not a directory
     * @throws CheckpointMismatchException if the tree no longer matches the checkpoint
     */
    static Outcome walk(Path root, Observer observer, Checkpoint resumeFrom) throws IOException {
        return walk(root, observer, resumeFrom, null);
    }

    /**
     * A folder's real path with a slash between its elements, which is how an occurrence below a code set folder
     * outside the corpus root is spelt (ADR-334 section 5.4): a drive letter and a colon first on Windows, two slashes
     * and the server's name for a share.
     */
    public static String spellingOf(Path canonicalFolder) {
        return canonicalFolder.toString().replace(java.io.File.separatorChar, '/');
    }

    /**
     * A folder outside the corpus root that a walk also reads (ADR-334 section 5.3).
     *
     * @param ordinal its place among the folders, from {@code 1}, the corpus root being {@code 0}
     * @param spelling its real path with {@code /} between its elements, which leads the path of every occurrence
     *     found below it and is the spelling of every anomaly there (section 5.4)
     */
    record Folder(int ordinal, String spelling) {}

    /**
     * Walks {@code root} as {@link #walk(Path, Observer, Checkpoint)} does, as a folder outside the corpus root where
     * {@code folder} is given: its occurrences are spelt by the folder's real path, absolute, and the folder is
     * itself an entry of the walk it is read with, counted as seen and as entered (section 5.3), so that the
     * identity of {@link Progress#accountsForEveryEntry()} holds of the whole.
     *
     * @param folder the folder this root is, or {@code null} for the corpus root
     */
    static Outcome walk(Path root, Observer observer, Checkpoint resumeFrom, Folder folder) throws IOException {
        return walk(root, observer, resumeFrom, folder, List.of());
    }

    /**
     * Walks {@code root} as {@link #walk(Path, Observer, Checkpoint, Folder)} does, counting as an entry and not
     * entering each directory beneath it whose path from {@code root}, compared element by element and without regard to
     * case on Windows, is one of {@code foldersNotWalked} (ADR-340 section 8.2). A folder outside the corpus root is
     * walked as before, a relative piece naming nothing in it.
     *
     * @param foldersNotWalked folders relative to the root with {@code /} between their elements; none for a walk of a
     *     folder outside the corpus root
     */
    static Outcome walk(Path root, Observer observer, Checkpoint resumeFrom, Folder folder,
            List<String> foldersNotWalked) throws IOException {
        Path canonical = canonicalRoot(root);

        Visitor visitor = new Visitor(canonical, observer, resumeFrom, folder, foldersNotWalked);
        try {
            Files.walkFileTree(canonical, visitor);
        } catch (IOException e) {
            // A walk that stopped early must never look complete: a partial walk reported as
            // finished curates a fraction of the corpus and reports success.
            return new Outcome(canonical, visitor.progress(), false, "the walk did not finish: " + e);
        }
        if (resumeFrom != null && !visitor.foundTheCheckpoint) {
            throw new CheckpointMismatchException(
                    "the tree no longer holds the directory this walk was checkpointed at: " + resumeFrom);
        }
        return new Outcome(canonical, visitor.progress(), true, null);
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

    /**
     * Where the traversal is standing, and what it has counted.
     *
     * <p>{@code position} is the ordinal path of the directory currently being visited, and
     * {@code nextChild} the ordinal the next entry of each open directory will take. Together they
     * name every entry the same way twice: a resumed walk over an unchanged tree computes the same
     * ordinal for the same entry, which is the whole basis for skipping past a checkpoint. Entries
     * that failed to be read take an ordinal too, or a failure would shift every ordinal after it.
     */
    private static final class Visitor implements FileVisitor<Path> {

        private final Path root;
        private final Observer observer;
        private final Checkpoint resumeFrom;

        private final List<Integer> position = new ArrayList<>();
        private final List<Integer> nextChild = new ArrayList<>();

        private long entriesSeen;
        private long directoriesEntered;
        private long occurrences;
        private long anomalies;
        private long foldersNotWalked;

        /** The folders not walked, each as its elements, compared with a directory's path from the root. */
        private final List<List<String>> notWalked;

        /** Whether the checkpointed directory was met, which is how a changed tree is caught. */
        private boolean foundTheCheckpoint;

        /** The folder outside the corpus root this walk is of, or {@code null} for the corpus root. */
        private final Folder folder;

        private Visitor(Path root, Observer observer, Checkpoint resumeFrom, Folder folder,
                List<String> foldersNotWalked) {
            this.root = root;
            this.notWalked = foldersNotWalked.stream().map(piece -> List.of(piece.split("/"))).toList();
            this.observer = observer;
            this.resumeFrom = resumeFrom;
            this.folder = folder;
            this.foundTheCheckpoint = resumeFrom == null;
        }

        Progress progress() {
            return new Progress(entriesSeen, directoriesEntered, occurrences, anomalies, foldersNotWalked);
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (dir.equals(root)) {
                // A resumed session must not count the root again: directoriesEntered is cumulative
                // across sessions, and the accounting identity subtracts the root exactly once.
                if (resumeFrom == null) {
                    directoriesEntered++;
                    if (folder != null) {
                        // A folder outside the corpus root is an entry of the walk it is read with.
                        entriesSeen++;
                    }
                }
                descend(null);
                return FileVisitResult.CONTINUE;
            }

            List<Integer> entry = takeOrdinal();
            switch (positionOf(entry)) {
                case DONE -> {
                    if (resumeFrom.isTheCheckpointedDirectory(entry)) {
                        verifyTheTreeStillAgrees(dir, entry);
                    }
                    return FileVisitResult.SKIP_SUBTREE;
                }
                case ANCESTOR -> {
                    descend(entry);
                    return FileVisitResult.CONTINUE;
                }
                case PENDING -> {
                    entriesSeen++;
                    if (isSoftLink(attrs)) {
                        report(dir, WalkAnomalyKind.SOFT_LINK_NOT_FOLLOWED, "a soft link was skipped rather than followed");
                        observer.entryWalked(progress());
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (isAFolderNotWalked(dir)) {
                        // An entry the walk saw and does not enter: nothing below it is met, so nothing below it
                        // is an occurrence, an anomaly or an entry counted (ADR-340 section 8.2).
                        foldersNotWalked++;
                        observer.folderNotEntered(root.relativize(dir).toString().replace(java.io.File.separatorChar, '/'));
                        observer.entryWalked(progress());
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    directoriesEntered++;
                    descend(entry);
                    observer.entryWalked(progress());
                    return FileVisitResult.CONTINUE;
                }
            }
            throw new IllegalStateException("unreachable: every checkpoint position is handled");
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            List<Integer> entry = takeOrdinal();
            if (positionOf(entry) != Checkpoint.Position.PENDING) {
                // Recorded by an earlier session. Re-recording it would violate the ledger's
                // uniqueness constraint, and counting it again would break the reconciliation.
                return FileVisitResult.CONTINUE;
            }
            entriesSeen++;

            if (isSoftLink(attrs)) {
                report(file, WalkAnomalyKind.SOFT_LINK_NOT_FOLLOWED, "a soft link was skipped rather than followed");
                observer.entryWalked(progress());
                return FileVisitResult.CONTINUE;
            }
            if (!attrs.isRegularFile()) {
                report(file, WalkAnomalyKind.UNPROCESSABLE, "not a regular file");
                observer.entryWalked(progress());
                return FileVisitResult.CONTINUE;
            }

            switch (folder == null
                    ? OccurrencePath.relativize(root, file)
                    : OccurrencePath.relativize(root, folder.spelling(), file)) {
                case OccurrencePath.Stored(OccurrencePath path) -> {
                    occurrences++;
                    observer.fileOccurrence(
                            path, attrs.size(), attrs.lastModifiedTime().toInstant(), attrs.creationTime().toInstant());
                }
                case OccurrencePath.Unstorable(String lossyRendering, String reason) -> {
                    anomalies++;
                    observer.anomaly(lossyRendering, WalkAnomalyKind.UNENCODABLE_PATH, reason);
                }
            }
            observer.entryWalked(progress());
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            String detail = exc.getClass().getSimpleName() + ": " + exc.getMessage();
            if (file.equals(root)) {
                // The root is not an entry beneath itself, so it is not counted as one — and having
                // failed, it was not entered either. The identity holds with anomalies alone.
                if (folder != null) {
                    entriesSeen++;
                }
                report(file, WalkAnomalyKind.UNPROCESSABLE, detail);
                return FileVisitResult.CONTINUE;
            }

            List<Integer> entry = takeOrdinal();
            if (positionOf(entry) != Checkpoint.Position.PENDING) {
                return FileVisitResult.CONTINUE;
            }
            entriesSeen++;
            report(file, WalkAnomalyKind.UNPROCESSABLE, detail);
            observer.entryWalked(progress());
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
            if (exc != null) {
                // The directory was entered but its listing did not complete, so entries below it
                // may be missing without any of them having been seen. It still counts as complete
                // for checkpoint purposes: a resumed walk that retried it would re-record whatever
                // the failed listing did reach, and the anomaly is the durable record either way.
                report(dir, WalkAnomalyKind.UNPROCESSABLE, "listing this directory did not complete: " + exc);
            }

            List<Integer> completed = List.copyOf(position);
            ascend();
            if (!completed.isEmpty()) {
                observer.checkpoint(
                        new Checkpoint(completed, render(root, dir), folder == null ? 0 : folder.ordinal()), progress());
            }
            return FileVisitResult.CONTINUE;
        }

        /**
         * Whether the directory's path from the root is one the profile lists, element by element: names compared
         * without regard to case on Windows, exactly elsewhere (ADR-340 section 8.2).
         */
        private boolean isAFolderNotWalked(Path dir) {
            if (notWalked.isEmpty() || folder != null) {
                return false;
            }
            Path below = root.relativize(dir);
            int count = below.getNameCount();
            boolean withoutRegardToCase = java.io.File.separatorChar != '/';
            for (List<String> listed : notWalked) {
                if (listed.size() != count) {
                    continue;
                }
                boolean same = true;
                for (int i = 0; i < count && same; i++) {
                    String name = below.getName(i).toString();
                    same = withoutRegardToCase ? name.equalsIgnoreCase(listed.get(i)) : name.equals(listed.get(i));
                }
                if (same) {
                    return true;
                }
            }
            return false;
        }

        /** The ordinal path of the next entry of the directory being listed. */
        private List<Integer> takeOrdinal() {
            int level = nextChild.size() - 1;
            int ordinal = nextChild.get(level);
            nextChild.set(level, ordinal + 1);

            List<Integer> entry = new ArrayList<>(position);
            entry.add(ordinal);
            return entry;
        }

        private void descend(List<Integer> entry) {
            if (entry != null) {
                position.add(entry.getLast());
            }
            nextChild.add(0);
        }

        private void ascend() {
            nextChild.removeLast();
            if (!position.isEmpty()) {
                position.removeLast();
            }
        }

        private Checkpoint.Position positionOf(List<Integer> entry) {
            return resumeFrom == null ? Checkpoint.Position.PENDING : resumeFrom.positionOf(entry);
        }

        /**
         * The checkpoint's one guard: the ordinals are only meaningful if they still point at the
         * directory they pointed at when they were written (ADR-016, ADR-055).
         */
        private void verifyTheTreeStillAgrees(Path dir, List<Integer> entry) {
            String rendering = render(root, dir);
            if (!rendering.equals(resumeFrom.pathRendering())) {
                throw new CheckpointMismatchException(
                        "this walk was checkpointed at %s, but that position now holds %s: the tree changed under a walk"
                                .formatted(resumeFrom, rendering));
            }
            foundTheCheckpoint = true;
        }

        /**
         * A junction and a volume mount point share a reparse tag and read as {@code isOther()},
         * not {@code isSymbolicLink()} — so both must be tested, or a junction is silently treated
         * as an ordinary entry.
         */
        private static boolean isSoftLink(BasicFileAttributes attrs) {
            return attrs.isSymbolicLink() || attrs.isOther();
        }

        /** An anomaly below a folder outside the corpus root is spelt as its occurrences are (section 5.4). */
        private String spelt(String rendering, Path entry) {
            String below = entry.equals(root) ? "" : rendering.replace('\\', '/');
            String leading = folder.spelling();
            return below.isEmpty() ? leading : leading + (leading.endsWith("/") ? "" : "/") + below;
        }

        private void report(Path entry, WalkAnomalyKind kind, String detail) {
            anomalies++;
            String rendering = render(root, entry);
            observer.anomaly(folder == null ? rendering : spelt(rendering, entry), kind, detail);
        }
    }
}
