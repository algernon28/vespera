package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.OptionalLong;
import java.util.function.LongConsumer;

/**
 * What the seed/corpus comparison asks of the module that measured the documents: the form each was
 * measured to have, one row per occurrence of a run (ADR-209 section 3.2).
 *
 * <p>Declared here and implemented by {@code extraction}, which owns the table the rows are in; {@code
 * pipeline} hands the one to the other, because {@code embedding} may not name {@code extraction}
 * (ADR-040, ADR-110).
 */
public interface MeasuredForms {

    /** The span of {@code runId}'s rows, empty where it holds none: the total a counted read is started with. */
    OptionalLong rowsUpTo(RunId runId);

    /**
     * Gives each row of {@code runId} to {@code row} as it is read, and the steps SQLite has taken to {@code
     * stepsTaken} (ADR-193).
     */
    void each(RunId runId, LongConsumer stepsTaken, Row row);

    /**
     * The seven values the comparison reads from a row, in this order, a missing mean score told by the
     * flag and a missing page count by {@code null}.
     */
    @FunctionalInterface
    interface Row {

        void read(
                OccurrenceId occurrence,
                String language,
                boolean meanScoreIsNull,
                int words,
                Integer pages,
                int vowelless,
                int singleCharacter);
    }
}
