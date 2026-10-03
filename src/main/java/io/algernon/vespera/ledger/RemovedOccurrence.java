package io.algernon.vespera.ledger;

/**
 * One occurrence a run removed, with the path census recorded it under and the reason the verdict
 * carries: what a page listing a run's removals for review needs (ADR-175). It names no verdict kind,
 * because the reason is what says why.
 */
public record RemovedOccurrence(OccurrenceId occurrenceId, String path, String reason) {}
