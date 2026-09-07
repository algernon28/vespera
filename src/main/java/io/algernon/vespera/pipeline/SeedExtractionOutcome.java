package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * What one seed occurrence's extraction produced (ADR-083).
 *
 * <p>Note what this record does <em>not</em> carry: a {@code VerdictKind}. Stage 2's own
 * {@link ExtractionOutcome} carries one, because every corpus document it judges is either removed or
 * left standing. A seed is never published, so no kind in the closed vocabulary applies to it, and
 * the type that travels through the seed pass says so by having nowhere to put one.
 *
 * @param occurrenceId the seed occurrence
 * @param unusableReason why it produced no text, or {@code null} for a seed that did
 */
record SeedExtractionOutcome(OccurrenceId occurrenceId, String unusableReason) {

    /** A seed that produced text, and is therefore something ADR-020's maximum can be taken over. */
    static SeedExtractionOutcome usable(OccurrenceId occurrenceId) {
        return new SeedExtractionOutcome(occurrenceId, null);
    }

    /** A seed that produced no text, recorded as data rather than judged. */
    static SeedExtractionOutcome unusable(OccurrenceId occurrenceId, String reason) {
        return new SeedExtractionOutcome(occurrenceId, reason);
    }

    boolean usable() {
        return unusableReason == null;
    }
}
