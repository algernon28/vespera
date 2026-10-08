package io.algernon.vespera.similarity;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.Collection;
import java.util.Map;

/**
 * What redundancy resolution asks of the module that measured the documents: how many alphanumeric
 * characters each of a set of occurrences held, as stage 2 recorded under a run (ADR-209 section 3.2).
 *
 * <p>Declared here and implemented by {@code extraction}, which owns the table the counts are in;
 * {@code pipeline} hands the one to the other, because {@code similarity} may not name {@code
 * extraction} (ADR-040, ADR-110).
 */
@FunctionalInterface
public interface AlphanumericCounts {

    /**
     * The count recorded under {@code run} for each of {@code occurrences}, holding only the occurrences
     * that have a row: one with none is absent from the answer.
     */
    Map<OccurrenceId, Long> recordedUnder(RunId run, Collection<OccurrenceId> occurrences);
}
