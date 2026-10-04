package io.algernon.vespera.pipeline;

/**
 * What {@link ExtractionItemProcessor} tells {@link ExtractionCircuitBreaker} about the occurrence it
 * has just returned, so that the breaker can tell an answer about a file from an occurrence that says
 * nothing about the converter now (ADR-184 section 4).
 *
 * <p>The breaker is told only that an item completed. An answer read from the extraction cache, an
 * occurrence with no detected format, a timeout with no response and an occurrence that dropped the
 * connection twice all complete too, and none of them leaves the row of service-scope failures any
 * shorter. The processor marks those; an occurrence it does not mark is evidence, and ends the row.
 * Step-scoped and read on the step thread only, so a mark is made and consumed within one occurrence.
 */
class ExtractionRowEvidence {

    private boolean none;

    /** The occurrence being processed brought no evidence that the converter answers about files now. */
    void noneFromThisOccurrence() {
        none = true;
    }

    /** Whether the occurrence just processed brought none; forgets the mark either way. */
    boolean consumeNone() {
        boolean marked = none;
        none = false;
        return marked;
    }

    /** Forgets a mark an occurrence that did not complete may have left. */
    void forget() {
        none = false;
    }
}
