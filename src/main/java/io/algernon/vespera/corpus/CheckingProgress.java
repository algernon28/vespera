package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.OccurrenceId;

/** What stage 1's first pass tells its caller as it goes (ADR-188). */
public interface CheckingProgress {

    /** After each survivor is checked and its verdict, if any, written. */
    void checked(OccurrenceId occurrence, BrokenOrOutOfScope.Outcome outcome);

    /** A text file whose lines could not be read to see whether it is a log, so it is not one. */
    void timestampsUnreadable(OccurrenceId occurrence, Exception cause);
}
