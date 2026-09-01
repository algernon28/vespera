package io.algernon.vespera.ledger;

/**
 * The identity of one file occurrence: a surrogate key per file occurrence (ADR-015), because
 * nothing about the file itself is stable enough to key on and a path is only unique within a walk.
 *
 * <p>It is what a verdict attaches to, and what the survivors query yields.
 */
public record OccurrenceId(long value) {}
