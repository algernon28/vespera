package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.ledger.OccurrenceId;

/**
 * What one seed occurrence's extraction produced (ADR-083).
 *
 * <p>Note what this record does <em>not</em> carry: a {@code VerdictKind}. Stage 2's own
 * {@link ExtractionOutcome} carries one, because every corpus document it judges is either removed or
 * left standing. A seed is never published, so no kind in the closed vocabulary applies to it, and
 * the type that travels through the seed pass says so by having nowhere to put one.
 *
 * <p>{@code measurement} travels with every outcome, usable or not, because {@link
 * SeedExtractionItemWriter} writes the row once the measurement run exists (ADR-092): the pass holding
 * the converted document open is the only one that can measure it without converting a second time,
 * and that pass is the processor, not the writer. What travels is the measured row and not the
 * response it came from, so the writer holds a seed folder's worth of numbers rather than of text.
 *
 * @param occurrenceId the seed occurrence
 * @param measurement the row measured off what the extractor answered with, for every seed it
 *     answered for at all
 * @param unusableReason why it produced no text, or {@code null} for a seed that did
 */
record SeedExtractionOutcome(
        OccurrenceId occurrenceId, ExtractionMetrics.Measurement measurement, String unusableReason) {

    /** A seed that produced text, and is therefore something ADR-020's maximum can be taken over. */
    static SeedExtractionOutcome usable(OccurrenceId occurrenceId, ExtractionMetrics.Measurement measurement) {
        return new SeedExtractionOutcome(occurrenceId, measurement, null);
    }

    /** A seed that produced no text, recorded as data rather than judged. */
    static SeedExtractionOutcome unusable(
            OccurrenceId occurrenceId, ExtractionMetrics.Measurement measurement, String reason) {
        return new SeedExtractionOutcome(occurrenceId, measurement, reason);
    }

    boolean usable() {
        return unusableReason == null;
    }
}
