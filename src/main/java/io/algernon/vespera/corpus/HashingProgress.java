package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * What stage 1's second pass tells its caller as it goes (ADR-188): the hashing loop, and, from ADR-192, the
 * loop that reads each survivor's size to group it and the loop that records each duplicate. The three
 * methods ADR-192 adds have default bodies that do nothing, so an implementer that counts only the hashing
 * compiles unchanged.
 */
public interface HashingProgress {

    /**
     * Called exactly once, before the first size is read, zero included: how many survivors the pass
     * reads, each of which has its recorded size read to group it (ADR-200).
     */
    default void toSize(long survivors) {}

    /** After each survivor's size is read, the one whose size no other survivor shares included. */
    default void sized() {}

    /**
     * After each duplicate is recorded as superseded and verdicted. There is no total: how many files are
     * copies of another is known only when every size group has been hashed, so the caller opens a running
     * count when {@link #toHash} is called.
     */
    default void supersededRecorded() {}

    /** Called exactly once, before the first hash, zero included: how many file occurrences will be hashed. */
    void toHash(long occurrences);

    /** After each file occurrence is hashed and its hash recorded. */
    void hashed(OccurrenceId occurrence, String sha256);

    /**
     * Called once, in place of {@link #hashed}, for a file occurrence the file system would not hand over
     * when it was hashed. No hash and no verdict is recorded for it: stage 2 reads every survivor stage 1
     * recorded no hash for and marks the file itself if it still cannot be read (ADR-210 section 4). The
     * caller asks whether the archive has gone, and stops the step if it has; otherwise it counts the file
     * as gone through, so the hashing total is still reached.
     */
    default void notHashed(OccurrenceId occurrence, java.io.IOException cause) {}
}
