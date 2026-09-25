package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.ledger.OccurrenceId;

/**
 * What one seed occurrence's extraction produced (ADR-083, ADR-155).
 *
 * <p>Note what this record does <em>not</em> carry: a {@code VerdictKind}. Stage 2's own
 * {@link ExtractionOutcome} carries one, because every corpus document it judges is either removed or
 * left standing. A seed is not a candidate, so no kind in the closed vocabulary applies to it, and
 * the type that travels through the seed pass says so by having nowhere to put one.
 *
 * <p>{@code measurement} travels with every outcome that was converted, usable or not, because {@link
 * SeedExtractionItemWriter} writes the row once the measurement run exists (ADR-092): the pass holding
 * the converted document open is the only one that can measure it without converting a second time,
 * and that pass is the processor, not the writer. What travels is the measured row and not the
 * response it came from, so the writer holds a seed folder's worth of numbers rather than of text.
 *
 * <p>{@code measurement} is {@code null} for a seed whose file would not open (ADR-155): without the
 * file's bytes there is no content hash to key a cache lookup with and nothing to measure, so this
 * outcome carries no {@code extraction_metric} row at all, only an {@code unusable_seed} row under
 * {@link #FILE_COULD_NOT_BE_OPENED}.
 *
 * @param occurrenceId the seed occurrence
 * @param measurement the row measured off what the extractor answered with, for every seed it
 *     converted, or {@code null} for a seed whose file would not open
 * @param unusableReason why it produced no text, or {@code null} for a seed that did
 */
record SeedExtractionOutcome(
        OccurrenceId occurrenceId, ExtractionMetrics.Measurement measurement, String unusableReason) {

    /**
     * The reason recorded for a seed whose file would not open when seed extraction read it (ADR-155
     * section 1). Distinct from {@link io.algernon.vespera.extraction.UsableText#NO_ALPHANUMERIC_CONTENT}:
     * that reason is a fact about a document a conversion carried no text from or was refused for; this
     * one is a fact about the archive at the moment it was read, and says nothing about the document.
     */
    static final String FILE_COULD_NOT_BE_OPENED = "the file could not be opened when seed extraction read it";

    /** A seed that produced text, and is therefore something ADR-020's maximum can be taken over. */
    static SeedExtractionOutcome usable(OccurrenceId occurrenceId, ExtractionMetrics.Measurement measurement) {
        return new SeedExtractionOutcome(occurrenceId, measurement, null);
    }

    /** A seed that produced no text, recorded as data rather than judged. */
    static SeedExtractionOutcome unusable(
            OccurrenceId occurrenceId, ExtractionMetrics.Measurement measurement, String reason) {
        return new SeedExtractionOutcome(occurrenceId, measurement, reason);
    }

    /**
     * A seed whose file would not open when this pass reached it (ADR-155 section 1): no conversion was
     * attempted and no measurement was taken, so this outcome carries no row for {@link #measurement()}.
     */
    static SeedExtractionOutcome couldNotOpen(OccurrenceId occurrenceId) {
        return new SeedExtractionOutcome(occurrenceId, null, FILE_COULD_NOT_BE_OPENED);
    }

    boolean usable() {
        return unusableReason == null;
    }

    /** Whether this outcome was converted at all, and therefore carries a row for {@link #measurement()}. */
    boolean hasMeasurement() {
        return measurement != null;
    }
}
