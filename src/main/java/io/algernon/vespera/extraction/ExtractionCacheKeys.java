package io.algernon.vespera.extraction;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code extraction}'s {@code extraction_cache_key} table (ADR-206): the key -- the SHA-256 of a file's
 * bytes, as 64 lowercase hexadecimal characters -- under which stage 2, or seed extraction under the
 * measurement run, looked the extraction cache up for one file occurrence. Every step after them reads it
 * here and opens no archive file to find it again.
 *
 * <p>A row exists exactly where an {@code extraction_metric} row does, under the same run (ADR-206
 * section 2), so whoever writes one writes the other, on the thread the step runs on and in the same
 * transaction (ADR-127, ADR-140). Nothing decides anything from a row: it is an address, not a second
 * byte identity beside {@code corpus}'s.
 *
 * <p>Not a bean: it is built over a {@link JdbcTemplate}, and reached from {@link ExtractionMetrics} by
 * the steps that write it.
 */
public class ExtractionCacheKeys {

    private final JdbcTemplate jdbcTemplate;

    public ExtractionCacheKeys(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Records {@code contentHash} as the key {@code occurrenceId} was looked up under during {@code runId}.
     * The table refuses a value that is not a key, and a second row for the same pair, which is what a
     * step that resumes must not write (ADR-181).
     */
    public void record(OccurrenceId occurrenceId, RunId runId, String contentHash) {
        jdbcTemplate.update(
                "INSERT INTO extraction_cache_key (occurrence_id, run_id, content_hash) VALUES (?, ?, ?)",
                occurrenceId.value(),
                runId.value(),
                contentHash);
    }

    /** The key recorded for {@code occurrenceId} under {@code runId}, empty where none was. */
    public Optional<String> forOccurrence(OccurrenceId occurrenceId, RunId runId) {
        List<String> found = jdbcTemplate.queryForList(
                "SELECT content_hash FROM extraction_cache_key WHERE occurrence_id = ? AND run_id = ?",
                String.class,
                occurrenceId.value(),
                runId.value());
        return found.stream().findFirst();
    }

    /**
     * The key recorded for {@code occurrenceId} under {@code runId}, and an error where none was: the step
     * that measured an occurrence, stage 2 or seed extraction, records one for every occurrence it
     * measured, so one with none is the ledger disagreeing with itself (ADR-206 section 4), which no step
     * turns into a row or a fallback.
     */
    public String requireForOccurrence(OccurrenceId occurrenceId, RunId runId) {
        return forOccurrence(occurrenceId, runId)
                .orElseThrow(() -> new IllegalStateException("occurrence " + occurrenceId.value()
                        + " has no extraction cache key on record under run " + runId.value()
                        + ", although the step that measured it records one for every document it measured"));
    }

    /**
     * Deletes every key recorded under {@code runId}, beside the deletion of that run's {@code
     * extraction_metric} rows (ADR-206 section 2): the table is keyed {@code (occurrence_id, run_id)}, so a
     * second write over a stopped invocation's rows would collide with the first.
     */
    public void discardForRun(RunId runId) {
        jdbcTemplate.update("DELETE FROM extraction_cache_key WHERE run_id = ?", runId.value());
    }
}
