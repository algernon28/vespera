package io.algernon.vespera.ledger;

/**
 * The identity a walk is minted under, distinct from a run: a walk owns file occurrences and is
 * scoped to one filesystem root, where a run owns verdicts and is scoped to one stage under one
 * configuration (ADR-048). A surrogate key, for the same reason a file occurrence's identity is one
 * (ADR-015): nothing about a walk's own data is stable enough to serve as its key.
 */
public record WalkId(long value) {}
