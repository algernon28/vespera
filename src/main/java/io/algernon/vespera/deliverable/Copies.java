package io.algernon.vespera.deliverable;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.synthesis.ClusterSlot;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.Set;

/**
 * The verified copy of each survivor the tree lists (ADR-274 §2): a file in its cluster's folder, named by {@link CopyNames}, made from the original opened for reading and nothing else, and put in place only when its bytes hash
 * to the key stage 2 recorded.
 *
 * <p><b>No copy fails the step, throws, or writes a row</b> (§2.3). What did not stand is held here, by occurrence,
 * for the pages and the manifest of the same drawing; the name each copy stands under is held, as it follows from
 * the survivor and its cluster's members (ADR-280 §2). The next invocation tries every copy that does not stand again.
 *
 * <p><b>A file at a copy's name is trusted</b> (§2.4): only the verified move puts one there, so neither it nor the
 * original is read again. A {@code .part} a stopped invocation left is written over.
 *
 * <p>This class writes no line and names no path outside the tree; it reports numbers through {@link
 * DeliverableProgress} (§8).
 */
final class Copies {

    /** Where an earlier layout's copies lie, beneath the tree (ADR-274 §1); no longer written (ADR-280 §0). */
    static final String DIRECTORY_NAME = "documents";

    /** The longest absolute path some Windows programs open (ADR-280 §3). */
    static final int LONGEST_WINDOWS_PATH = 259;

    private static final int BUFFER_BYTES = 1 << 16;

    private final Path tree;
    private final String corpusRoot;
    private final ReplacingMove moves;
    private final LongSupplier usableSpace;
    private final DeliverableProgress progress;
    private final Map<Long, CopyOutcome> notStanding = new HashMap<>();
    /** A cluster's folder as a path from the tree's root, by the cluster; one short string for each cluster drawn. */
    private final Map<ClusterSlot, String> foldersFromTheRoot = new HashMap<>();
    /**
     * The names that differ from {@link CopyNames#plainName}, by occurrence: only those a collision changed. Every
     * other copy's name follows from its survivor alone, so no name is held for it.
     */
    private final Map<Long, String> suffixedNames = new HashMap<>();
    private long atALongPath;

    Copies(Path tree, String corpusRoot, ReplacingMove moves, LongSupplier usableSpace, DeliverableProgress progress) {
        this.tree = tree;
        this.corpusRoot = corpusRoot;
        this.moves = moves;
        this.usableSpace = usableSpace;
        this.progress = progress;
    }

    /**
     * The copy's name in its cluster's folder: the one worked out when that cluster's copies were made where a
     * collision changed it, and otherwise the survivor's own plain name (ADR-280 §1, §2).
     */
    String nameOf(ListedSurvivor survivor) {
        String suffixed = suffixedNames.get(survivor.occurrence().value());
        return suffixed != null ? suffixed : CopyNames.plainName(survivor);
    }

    /** The copy's path from the tree's root, {@code /} between its parts (ADR-280 §0). */
    String pathOf(ListedSurvivor survivor) {
        String folder = foldersFromTheRoot.get(survivor.slot());
        return folder == null || folder.isEmpty() ? nameOf(survivor) : folder + "/" + nameOf(survivor);
    }

    /** What became of the survivor's copy; {@link CopyOutcome#COPIED} for any that was never recorded as failing. */
    CopyOutcome outcomeOf(ListedSurvivor survivor) {
        return notStanding.getOrDefault(survivor.occurrence().value(), CopyOutcome.COPIED);
    }

    /** How many copies stand, made or kept, at an absolute path longer than {@value #LONGEST_WINDOWS_PATH} characters. */
    long copiesAtALongPath() {
        return atALongPath;
    }

    /**
     * Works out every name of {@code members} before the first copy, from what is recorded alone (ADR-280 §2), then
     * makes the copy of each that does not stand in {@code clusterFolder}, reporting each outcome as it comes. A file
     * at the name worked out is trusted and neither it nor its original is read.
     *
     * <p>The names this cluster's members share are worked out here and let go when the method returns: what is kept
     * is the cluster's folder, as a path from the tree's root, and the name of each copy a collision changed. Every
     * other name follows from its survivor alone ({@link CopyNames#plainName}), so the page and the manifest of the
     * same drawing come by it again without holding it.
     */
    void copyAll(Path clusterFolder, List<ListedSurvivor> members, Set<String> pictureNames) {
        Map<OccurrenceId, String> names = CopyNames.of(members, pictureNames);
        Path folder = clusterFolder.toAbsolutePath().normalize();
        List<String> parts = new ArrayList<>();
        tree.toAbsolutePath().normalize().relativize(folder).forEach(part -> parts.add(part.toString()));
        String fromTheRoot = String.join("/", parts).replaceAll("^/+", "");
        for (ListedSurvivor survivor : members) {
            foldersFromTheRoot.put(survivor.slot(), fromTheRoot);
            String name = names.get(survivor.occurrence());
            if (name.equals(CopyNames.plainName(survivor))) {
                suffixedNames.remove(survivor.occurrence().value());
            } else {
                suffixedNames.put(survivor.occurrence().value(), name);
            }
        }
        try {
            Files.createDirectories(folder);
        } catch (IOException | RuntimeException refused) {
            // Each copy below then fails as that survivor's own (ADR-274 §2.3).
        }
        for (ListedSurvivor survivor : members) {
            Path copy = null;
            try {
                copy = folder.resolve(names.get(survivor.occurrence()));
                if (Files.isRegularFile(copy)) {
                    progress.survivorAlreadyCopied();
                } else {
                    CopyOutcome outcome = copy(survivor, copy);
                    if (outcome != CopyOutcome.COPIED) {
                        notStanding.put(survivor.occurrence().value(), outcome);
                        continue;
                    }
                }
                if (copy.toString().length() > LONGEST_WINDOWS_PATH) {
                    atALongPath++;
                }
            } catch (RuntimeException refused) {
                // A name the platform's path parser refuses, an InvalidPathException among them, is that survivor's
                // own failure and not the run's: nothing leaves the tree to roll back its rows (ADR-274 §2.3).
                notStanding.put(survivor.occurrence().value(), CopyOutcome.NOT_COPIED);
                progress.survivorNotCopied();
            }
        }
    }

    private CopyOutcome copy(ListedSurvivor survivor, Path copy) {
        Path part = copy.resolveSibling(copy.getFileName() + Deliverable.PART_SUFFIX);
        try {
            Path original = Path.of(corpusRoot).resolve(survivor.path().value());
            long size = Files.size(original);
            if (usableSpace.getAsLong() - size < Deliverable.COPY_HEADROOM_BYTES) {
                progress.survivorWithNoRoom();
                return CopyOutcome.NO_ROOM;
            }
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            long bytes = 0;
            try (InputStream in = Files.newInputStream(original, StandardOpenOption.READ);
                    OutputStream out = Files.newOutputStream(
                            part,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[BUFFER_BYTES];
                for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                    sha256.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                    bytes += read;
                }
            }
            if (!HexFormat.of().formatHex(sha256.digest()).equalsIgnoreCase(survivor.contentHash())) {
                Files.deleteIfExists(part);
                progress.survivorChanged();
                return CopyOutcome.CHANGED;
            }
            moves.intoPlace(part, copy);
            progress.survivorCopied(bytes);
            return CopyOutcome.COPIED;
        } catch (NoSuchFileException | NotDirectoryException gone) {
            leaveNoPart(part);
            progress.survivorGone();
            return CopyOutcome.GONE;
        } catch (IOException | RuntimeException | NoSuchAlgorithmException other) {
            // Deliberately every failure, a path the parser refuses among them: a bad copy is that survivor's,
            // not the run's, and an exception out of the tree would roll back every fault row (ADR-274 §2.3).
            leaveNoPart(part);
            progress.survivorNotCopied();
            return CopyOutcome.NOT_COPIED;
        }
    }

    private static void leaveNoPart(Path part) {
        try {
            Files.deleteIfExists(part);
        } catch (IOException | RuntimeException stays) {
            // A part that cannot be removed is written over by the next attempt (ADR-274 §2.4).
        }
    }

    /** How many bytes the original of {@code survivor} holds, or none where its size cannot be read. */
    static long sizeOf(String corpusRoot, ListedSurvivor survivor) {
        try {
            return Files.size(Path.of(corpusRoot).resolve(survivor.path().value()));
        } catch (IOException | RuntimeException unreadable) {
            return 0;
        }
    }
}
