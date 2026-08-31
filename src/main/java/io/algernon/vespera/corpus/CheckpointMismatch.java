package io.algernon.vespera.corpus;

/**
 * A resumed walk found something other than what its checkpoint pointed at (ADR-055).
 *
 * <p>Loud on purpose. Resuming rests on the corpus being static (ADR-016) and on traversal order
 * being stable; where that no longer holds, skipping past the checkpoint would silently omit
 * whatever moved into the skipped positions, and a corpus quietly missing a subtree is the one
 * failure census exists to make impossible.
 *
 * <p>The way out is an operator's call, not the walk's: delete the unfinished walk and start again.
 */
public class CheckpointMismatch extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    CheckpointMismatch(String message) {
        super(message);
    }
}
