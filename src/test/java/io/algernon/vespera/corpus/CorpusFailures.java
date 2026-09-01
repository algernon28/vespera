package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.WalkId;

/**
 * Mints the two walk failures a test outside {@code corpus} cannot construct for itself.
 *
 * <p>Both constructors are package-private on purpose: only the walk gets to decide that a corpus
 * lost rows, and a test in another module has no business claiming it did. But what those failures
 * cost the invocation is decided in {@code pipeline}, and pinning that decision means handing a test
 * there one of these. This is the narrow way to do it — a factory in the package that owns them,
 * exposing nothing but the fact that they can be raised.
 */
public final class CorpusFailures {

    /** A finished walk whose counts leave entries unaccounted for (ADR-056). */
    public static ExcludesNothingViolation excludesNothing() {
        return new ExcludesNothingViolation(new WalkId(1), new Walk.Progress(99, 0, 0, 0));
    }

    /** A resumed walk that found something other than what its checkpoint pointed at (ADR-055). */
    public static CheckpointMismatch checkpointMismatch() {
        return new CheckpointMismatch("walk 1 resumed at a position holding something else");
    }

    private CorpusFailures() {}
}
