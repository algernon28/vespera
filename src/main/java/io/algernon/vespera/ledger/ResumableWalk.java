package io.algernon.vespera.ledger;

/**
 * An unfinished walk over a root, and everything needed to continue it under its own id (ADR-055).
 *
 * <p>The checkpoint crosses this seam as two opaque strings. What they encode is {@code corpus}'s
 * business — the ledger stores a walk's progress without having an opinion about how a walk makes
 * progress — and keeping them opaque is what lets {@code ledger} depend on no other module.
 *
 * @param counts cumulative across every session of this walk, not just the last one, and counting
 *     the root exactly once
 */
public record ResumableWalk(
        WalkId walkId, String checkpointOrdinals, String checkpointPath, WalkCounts counts) {}
