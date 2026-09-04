package io.algernon.vespera.corpus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How far a walk has got, in a form a later walk can skip past (ADR-055).
 *
 * <p>The encoding is a path of per-level ordinal positions through the directory tree — the third
 * child of the first child of the root is {@code 0/2} — naming the last directory whose whole
 * subtree was recorded. That form is what makes a resumed walk cheap rather than merely correct: it
 * lets the walk answer "is this entry already done?" from the position it is standing in, and return
 * {@code SKIP_SUBTREE} on a completed directory instead of descending and re-stat'ing every file
 * inside it, which is the dominant cost of a walk over hundreds of gigabytes.
 *
 * <p>It works only because traversal order over an unchanged tree is stable, which is measured
 * rather than assumed, and because the corpus is treated as static (ADR-016). {@code pathRendering}
 * is the guard on that assumption: the relative path the ordinals pointed at when the checkpoint was
 * written, checked against what they point at on resume, so a tree that changed underneath fails
 * loudly instead of silently skipping the wrong subtree.
 *
 * <p>What that guard covers is worth being exact about, because it is a check and not a proof. A
 * change at or above the checkpoint's own position moves what those ordinals name, so the rendering
 * no longer matches and the walk stops; a position that now holds a file rather than a directory is
 * never matched at all, and the walk stops at the end for the same reason. What it does not see is a
 * reordering entirely inside the region already recorded, which leaves the checkpoint's own ordinals
 * pointing where they did. Catching that would mean re-reading every entry the checkpoint exists to
 * let the walk skip. ADR-050's exclusive access and ADR-016's static corpus are what make that
 * residue acceptable rather than merely unmeasured.
 *
 * @param ordinals the position of the completed directory, one ordinal per level below the root
 * @param pathRendering that directory's root-relative path when the checkpoint was written
 */
record Checkpoint(List<Integer> ordinals, String pathRendering) {

    private static final String SEPARATOR = "/";

    Checkpoint {
        if (ordinals == null || ordinals.isEmpty()) {
            throw new IllegalArgumentException("a checkpoint names a directory below the root, so it has ordinals");
        }
        ordinals = List.copyOf(ordinals);
    }

    /**
     * Reads back a checkpoint stored as two columns, or nothing where a walk has yet to reach one.
     *
     * <p>Both halves have to be present: ordinals with no path cannot be verified against the tree,
     * and a path with no ordinals cannot be skipped past.
     */
    static Optional<Checkpoint> of(String ordinals, String pathRendering) {
        if (ordinals == null || ordinals.isBlank() || pathRendering == null) {
            return Optional.empty();
        }
        List<Integer> parsed = new ArrayList<>();
        for (String ordinal : ordinals.split(SEPARATOR)) {
            parsed.add(Integer.valueOf(ordinal.trim()));
        }
        return Optional.of(new Checkpoint(parsed, pathRendering));
    }

    /** The ordinals as one string, for storage. */
    String encodedOrdinals() {
        return ordinals.stream().map(String::valueOf).reduce((left, right) -> left + SEPARATOR + right)
                .orElseThrow();
    }

    /**
     * Where {@code entry} sits relative to this checkpoint, given the ordinal path of that entry.
     *
     * <p>Lexicographic on the ordinals, with one case that is not an ordering question: an entry
     * whose ordinals are a strict prefix of the checkpoint's is an ancestor of the completed
     * directory, so it is neither done nor pending — it has to be entered again, and not counted
     * again.
     */
    Position positionOf(List<Integer> entry) {
        int shared = Math.min(entry.size(), ordinals.size());
        for (int level = 0; level < shared; level++) {
            int difference = Integer.compare(entry.get(level), ordinals.get(level));
            if (difference != 0) {
                return difference < 0 ? Position.DONE : Position.PENDING;
            }
        }
        return entry.size() < ordinals.size() ? Position.ANCESTOR : Position.DONE;
    }

    /** Whether {@code entry} is the checkpointed directory itself, the one place the path is checked. */
    boolean isTheCheckpointedDirectory(List<Integer> entry) {
        return ordinals.equals(entry);
    }

    /** Where an entry sits relative to a checkpoint. */
    enum Position {

        /** Recorded by an earlier session: skip it, and do not count it again. */
        DONE,

        /** On the path down to the checkpointed directory: enter it, but do not count it again. */
        ANCESTOR,

        /** Not yet reached by any session: walk it as a fresh entry. */
        PENDING
    }

    @Override
    public String toString() {
        return encodedOrdinals() + " (" + pathRendering + ")";
    }
}
