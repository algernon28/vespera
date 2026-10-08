package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.OccurrenceId;

/** What stage 1's first pass tells its caller as it goes (ADR-188). */
public interface CheckingProgress {

    /** After each survivor is checked and its verdict, if any, written. */
    void checked(OccurrenceId occurrence, BrokenOrOutOfScope.Outcome outcome);

    /** A text file whose lines could not be read to see whether it is a log, so it is not one. */
    void timestampsUnreadable(OccurrenceId occurrence, Exception cause);

    /**
     * Called before the {@code broken} verdict is written for a survivor whose size, leading bytes,
     * zip container or PDF trailer the file system would not hand over, with the reason the verdict
     * will carry. The caller asks whether the archive has gone, and stops the step if it has, so
     * nothing is marked for it (ADR-210 section 2). The file is still marked {@code broken} when this
     * returns: stage 1 is the only place a format is detected, so a file with no verdict and no format
     * would never reach stage 2's {@code could not be read}.
     */
    default void couldNotRead(OccurrenceId occurrence, String reason) {}
}
