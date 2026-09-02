package io.algernon.vespera.ledger;

import java.time.Instant;

/** What census observed about one file occurrence, read back by its id. */
public record OccurrenceFacts(OccurrencePath path, long sizeBytes, Instant creationTime) {}
