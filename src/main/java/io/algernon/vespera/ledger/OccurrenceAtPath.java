package io.algernon.vespera.ledger;

/** An occurrence with the path the walk recorded for it: what {@link Verdicts#eachPageOfSurvivorsByPath} hands out (ADR-249 section 3). */
public record OccurrenceAtPath(OccurrenceId occurrenceId, OccurrencePath path) {}
