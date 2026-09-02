package io.algernon.vespera.ledger;

import java.time.Instant;

/** A file occurrence as the ledger read it back, scoped to the walk it was queried against. */
public record RecordedOccurrence(OccurrencePath path, long sizeInBytes, Instant lastModified, Instant creationTime) {}
