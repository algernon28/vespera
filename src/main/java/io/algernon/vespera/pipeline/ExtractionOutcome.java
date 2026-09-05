package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.VerdictKind;

/**
 * A verdict {@link ExtractionItemProcessor} decided to write for one occurrence — always
 * {@link VerdictKind#EXTRACTION_FAILED} in this ticket's slice, since neither {@code degenerate-output}
 * nor {@code passed} is decided here (that is #48's metrics/degeneracy pass, which continues the same
 * per-occurrence work this processor starts).
 *
 * <p>A success or {@code partial_success} response is not represented by an instance of this record at
 * all: {@link ExtractionItemProcessor} returns {@code null} for those, which Spring Batch reads as "filter
 * this item" rather than "write nothing for it" — an occurrence carries no row until a stage actually
 * judges it.
 */
record ExtractionOutcome(OccurrenceId occurrenceId, VerdictKind kind, String reason) {}
