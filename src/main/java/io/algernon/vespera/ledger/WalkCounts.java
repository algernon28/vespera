package io.algernon.vespera.ledger;

/**
 * What a walk row says it met, read back rather than remembered.
 *
 * <p>Read back on purpose: the excludes-nothing reconciliation (ADR-056) compares these against an
 * independent count of the rows actually written, and comparing an in-memory counter against the
 * database would only prove the walk agreed with itself.
 */
public record WalkCounts(long entriesSeen, long directoriesEntered) {}
