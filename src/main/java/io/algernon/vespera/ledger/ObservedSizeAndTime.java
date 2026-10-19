package io.algernon.vespera.ledger;

import java.time.Instant;

/** The size and last-modified time the census observed for one file occurrence, read back by its id. */
public record ObservedSizeAndTime(long sizeBytes, Instant lastModified) {}
