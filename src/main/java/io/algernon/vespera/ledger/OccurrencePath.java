package io.algernon.vespera.ledger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * How a file occurrence's path identifies it: the path beneath the corpus root, spelled exactly as
 * directory traversal gave it, with separators rewritten as {@code /}. Recorded as ADR-051.
 *
 * <p>Two things this deliberately does not do, both because they would merge distinct files into
 * one occurrence. It does not fold case — the JDK's folding table disagrees with NTFS, where
 * {@code ı} and {@code I} are different files — and it does not normalise Unicode, because NTFS
 * stores opaque UTF-16 and normalises nothing. Comparison is therefore exact string equality.
 *
 * <p>It also does not canonicalise. For an entry found by traversal the directory entry already is
 * the authoritative on-disk spelling, so {@code toRealPath()} would cost a filesystem call per file
 * and buy nothing. The corpus root is canonicalised once per walk, by the walk, because the root is
 * the one component a person typed.
 */
public record OccurrencePath(String value) {

    public OccurrencePath {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("an occurrence path is never empty");
        }
    }

    /** The outcome of deriving a stored path: either one exists, or the name cannot be stored. */
    public sealed interface Result {}

    /** The entry has a stored form. */
    public record Stored(OccurrencePath path) implements Result {}

    /**
     * The entry has no stored form, and therefore yields no file occurrence. It is still an entry
     * the walk is accountable for, so it is recorded as a walk anomaly carrying these two fields:
     * a rendering the operator can act on, and why it was refused.
     */
    public record Unstorable(String lossyRendering, String reason) implements Result {}

    /**
     * Derives the stored path for an entry found beneath {@code corpusRoot}.
     *
     * @throws IllegalArgumentException if the entry is not beneath the root, or is the root itself.
     *     Both are programming errors rather than properties of the corpus: a walk only ever offers
     *     entries it found underneath the root it was given.
     */
    public static Result relativize(Path corpusRoot, Path entry) {
        Path relative = corpusRoot.relativize(entry);
        if (relative.getNameCount() == 0 || relative.toString().isEmpty()) {
            throw new IllegalArgumentException("entry is the corpus root itself: " + entry);
        }
        if (relative.startsWith("..")) {
            throw new IllegalArgumentException("entry is not beneath the corpus root: " + entry);
        }

        StringBuilder joined = new StringBuilder();
        for (Path name : relative) {
            if (joined.length() > 0) {
                joined.append('/');
            }
            joined.append(name);
        }
        String stored = joined.toString();

        // A filename may be valid on NTFS and have no UTF-8 encoding at all: an unpaired surrogate
        // is the case that occurs in practice. SQLite TEXT is UTF-8, so such a path could only be
        // stored corrupted — and the corrupted form is not even a legal Windows path, since the
        // replacement character is forbidden in one. The entry becomes an anomaly instead.
        String roundTripped = new String(stored.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        if (!roundTripped.equals(stored)) {
            return new Unstorable(
                    roundTripped,
                    "the name does not survive a UTF-8 round trip, so it cannot be stored as text");
        }

        return new Stored(new OccurrencePath(stored));
    }

    @Override
    public String toString() {
        return value;
    }
}
