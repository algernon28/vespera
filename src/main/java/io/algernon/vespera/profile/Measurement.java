package io.algernon.vespera.profile;

import java.time.Instant;

/**
 * Where the data informing a profile key lives, and when census last looked (ADR-061).
 *
 * <p>A pointer rather than a number. The measurement itself belongs in the ledger, which is where a
 * walk's counts and a stage's verdicts already are; copying it into the profile would create a
 * second copy nobody updates. What the profile carries instead is enough for a person to go and read
 * the real thing.
 *
 * @param source what to go and look at — a walk, a table, a report
 * @param refreshedAt when census last wrote this pointer
 */
public record Measurement(String source, Instant refreshedAt) {}
