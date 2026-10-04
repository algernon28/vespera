package io.algernon.vespera.extraction;

import java.util.Optional;

/**
 * What one file occurrence's call earns (ADR-189 section 2). {@code extraction} returns decisions and
 * {@code pipeline} acts on them, throwing what a Spring Batch step has to be told.
 */
public sealed interface OccurrenceDecision {

    /**
     * A conversion: the caller shingles its text. A verdict of degenerate-output where a reason is
     * present, else none, so the occurrence is a survivor. Its metric row is already written.
     */
    record Converted(Optional<String> degenerateReason) implements OccurrenceDecision {}

    /** extraction-failed, with this reason. */
    record Failed(String reason) implements OccurrenceDecision {}

    /** Set aside unjudged: a fault of this category and detail, resolved at the end of the step. */
    record SetAside(String category, String detail) implements OccurrenceDecision {}

    /**
     * Dropped the connection twice: extraction-failed with this reason, unless {@code row} is
     * {@link InARow.ControlNotConverted}, which stops.
     */
    record DroppedTwice(String reason, InARow row) implements OccurrenceDecision {}
}
