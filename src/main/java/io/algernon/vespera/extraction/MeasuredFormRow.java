package io.algernon.vespera.extraction;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * What the seed/corpus comparison reads from one {@code extraction_metric} row, handed to a callback as
 * the row is read (ADR-209 section 3.2): the seven values, in this order, a missing mean score told by
 * the flag and a missing page count by {@code null}.
 *
 * <p>{@code embedding} declares the same shape as {@code MeasuredForms.Row}; {@code pipeline} hands one
 * to the other as {@code row::read}, because {@code embedding} may not name {@code extraction}.
 */
@FunctionalInterface
public interface MeasuredFormRow {

    void read(
            OccurrenceId occurrence,
            String language,
            boolean meanScoreIsNull,
            int words,
            Integer pages,
            int vowelless,
            int singleCharacter);
}
