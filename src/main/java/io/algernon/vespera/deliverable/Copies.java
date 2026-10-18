package io.algernon.vespera.deliverable;

import io.algernon.vespera.synthesis.FilenameStem;
import io.algernon.vespera.ledger.ReplacingMove;
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
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * The verified copy of each survivor the tree lists (ADR-274 §2): a file under {@code documents/}, named by its
 * occurrence, made from the original opened for reading and nothing else, and put in place only when its bytes hash
 * to the key stage 2 recorded.
 *
 * <p><b>No copy fails the step, throws, or writes a row</b> (§2.3). What did not stand is held here, by occurrence,
 * for the pages and the manifest of the same drawing; a copy that stands is not held, since the name it stands under
 * follows from the survivor alone. The next invocation tries every copy that does not stand again.
 *
 * <p><b>A file at a copy's name is trusted</b> (§2.4): only the verified move puts one there, so neither it nor the
 * original is read again. A {@code .part} a stopped invocation left is written over.
 *
 * <p>This class writes no line and names no path outside the tree; it reports numbers through {@link
 * DeliverableProgress} (§8).
 */
final class Copies {

    /** Where the copies lie, beneath the tree (ADR-274 §1). */
    static final String DIRECTORY_NAME = "documents";

    private static final Pattern AN_EXTENSION = Pattern.compile("[a-z0-9]{1,8}");
    private static final int BUFFER_BYTES = 1 << 16;

    private final Path tree;
    private final String corpusRoot;
    private final ReplacingMove moves;
    private final LongSupplier usableSpace;
    private final DeliverableProgress progress;
    private final Map<Long, CopyOutcome> notStanding = new HashMap<>();

    Copies(Path tree, String corpusRoot, ReplacingMove moves, LongSupplier usableSpace, DeliverableProgress progress) {
        this.tree = tree;
        this.corpusRoot = corpusRoot;
        this.moves = moves;
        this.usableSpace = usableSpace;
        this.progress = progress;
    }

    /**
     * {@code <occurrence id>-<slug>.<extension>}: the slug is the stem's slug cut to its limit, the extension the last
     * of the file name lower-cased where it is one to eight of {@code a} to {@code z} and digits; with no slug the
     * hyphen goes, and with no such extension the dot (ADR-274 §2.1).
     */
    static String nameOf(ListedSurvivor survivor) {
        String path = survivor.path().value();
        String file = path.substring(path.lastIndexOf('/') + 1);
        int dot = file.lastIndexOf('.');
        String extension = dot < 0 ? "" : file.substring(dot + 1).toLowerCase(Locale.ROOT);
        String slug = IndexPage.slug(FilenameStem.of(path));
        return survivor.occurrence().value()
                + (slug.isEmpty() ? "" : "-" + slug)
                + (AN_EXTENSION.matcher(extension).matches() ? "." + extension : "");
    }

    /** The copy's path from the tree's root, {@code /} between its parts. */
    static String pathOf(ListedSurvivor survivor) {
        return DIRECTORY_NAME + "/" + nameOf(survivor);
    }

    /** Whether a copy stands at the survivor's name. */
    boolean stands(ListedSurvivor survivor) {
        return Files.isRegularFile(tree.resolve(DIRECTORY_NAME).resolve(nameOf(survivor)));
    }

    /** What became of the survivor's copy; {@link CopyOutcome#COPIED} for any that was never recorded as failing. */
    CopyOutcome outcomeOf(ListedSurvivor survivor) {
        return notStanding.getOrDefault(survivor.occurrence().value(), CopyOutcome.COPIED);
    }

    /** Makes the copy of each of {@code survivors} that does not stand, reporting each outcome as it comes. */
    void copyAll(List<ListedSurvivor> survivors) {
        for (ListedSurvivor survivor : survivors) {
            if (stands(survivor)) {
                progress.survivorAlreadyCopied();
                continue;
            }
            CopyOutcome outcome = copy(survivor);
            if (outcome != CopyOutcome.COPIED) {
                notStanding.put(survivor.occurrence().value(), outcome);
            }
        }
    }

    private CopyOutcome copy(ListedSurvivor survivor) {
        Path copy = tree.resolve(DIRECTORY_NAME).resolve(nameOf(survivor));
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
