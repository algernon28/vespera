package io.algernon.vespera.corpus;

import io.algernon.vespera.ledger.WalkId;

/**
 * A finished walk's counts do not account for every entry it met (ADR-056).
 *
 * <p>The claim census rests on is that it excludes nothing, and this is that claim checked rather
 * than asserted: the walk's own cumulative counts against an independent {@code COUNT(*)} over what
 * was actually written. A mismatch means occurrences went missing between the walk and the ledger,
 * which every later stage would inherit as a corpus quietly smaller than the archive.
 *
 * <p>So it aborts the invocation. There is no degraded mode where a census that lost rows is still
 * worth judging against.
 */
public class ExcludesNothingViolation extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    ExcludesNothingViolation(WalkId walkId, Walk.Progress asWritten) {
        super(("walk %d met %d entries and entered %d directories, but the ledger holds %d occurrences and %d"
                        + " anomalies: %d entries are unaccounted for, so this walk does not exclude nothing")
                .formatted(
                        walkId.value(),
                        asWritten.entriesSeen(),
                        asWritten.directoriesEntered(),
                        asWritten.occurrences(),
                        asWritten.anomalies(),
                        asWritten.unaccountedFor()));
    }
}
