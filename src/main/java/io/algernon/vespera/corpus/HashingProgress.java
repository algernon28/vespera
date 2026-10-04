package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.OccurrenceId;

/** What stage 1's second pass tells its caller as it goes (ADR-188). */
public interface HashingProgress {

    /** Called exactly once, before the first hash, zero included: how many file occurrences will be hashed. */
    void toHash(long occurrences);

    /** After each file occurrence is hashed and its hash recorded. */
    void hashed(OccurrenceId occurrence, String sha256);
}
