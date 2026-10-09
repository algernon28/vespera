package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.VerdictKind;

/**
 * A verdict {@link ExtractionItemProcessor} decided to write for one occurrence:
 * {@link VerdictKind#EXTRACTION_FAILED} where the conversion failed or the file could not be read
 * (ADR-070, ADR-210), or {@link VerdictKind#DEGENERATE_OUTPUT} where a converted occurrence fell below
 * the degeneracy floor (ADR-070).
 *
 * <p>A survivor is not represented by an instance of this record at all: {@link ExtractionItemProcessor}
 * returns {@code null} for it, which Spring Batch reads as "filter this item" rather than "write nothing
 * for it" — an occurrence carries no row until a stage actually judges it.
 */
record ExtractionOutcome(OccurrenceId occurrenceId, VerdictKind kind, String reason) {}
