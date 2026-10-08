package io.algernon.vespera.ledger;

/** A survivor and the size the walk recorded for it, as {@link Verdicts#survivorsBySize} hands it out. */
public record SizedOccurrence(OccurrenceId occurrenceId, long sizeBytes) {}
