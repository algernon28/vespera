package io.algernon.vespera.extraction;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code extraction}'s record of a refused conversion (ADR-139), behind this class and nothing else
 * querying the table (ADR-041).
 *
 * <p><b>The precedent is {@code unusable_seed} and {@code cluster_fault}, not the ledger.</b> A
 * verdict removes an occurrence from the survivor set; a fault row alone removes nothing -- it is
 * {@code pipeline}'s own fault-resolving listener (ADR-139 section 3) that turns one into {@code
 * extraction-failed}, from the occurrence, category and detail it already holds in memory from the
 * skip itself -- it never queries this table back. This class only ever writes, discards and counts
 * the fault rows; nothing in {@code src/main} enumerates them, since the category column is there for
 * a human's query, not a caller's.
 *
 * <p><b>Not {@code @Component}.</b> On {@code ClusterFaults}' own precedent: every caller that needs
 * one already holds a {@link JdbcTemplate} and constructs its own instance from it, so a bean nothing
 * in {@code src/main} would inject would just sit in the context unused. A test that needs one imports
 * this class and gets it the ordinary way {@code new} already provides for a plain class.
 *
 * <p>Rows are per run (ADR-077): {@link #discardForRun} is the discard half of ADR-115/ADR-116, for a
 * step whose completion under its own run is not recorded, and it exists for the same reason {@code
 * extraction_metric}'s own discard does -- the primary key is {@code (occurrence_id, run_id)}, so a
 * second write over a stopped invocation's rows would collide on the first rather than silently
 * double it.
 */
public class ExtractionFaults {

    private final JdbcTemplate jdbcTemplate;

    public ExtractionFaults(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Records that the converter refused {@code occurrenceId} under {@code runId}, and how. */
    public void write(OccurrenceId occurrenceId, RunId runId, String category, String detail) {
        jdbcTemplate.update(
                "INSERT INTO extraction_fault (occurrence_id, run_id, category, detail) VALUES (?, ?, ?, ?)",
                occurrenceId.value(),
                runId.value(),
                category,
                detail);
    }

    /**
     * Deletes every fault row recorded under {@code runId} -- the discard half of ADR-115/ADR-116,
     * for a step whose completion under this run is not recorded.
     */
    public void discardForRun(RunId runId) {
        jdbcTemplate.update("DELETE FROM extraction_fault WHERE run_id = ?", runId.value());
    }

    /** How many occurrences {@code runId}'s step refused to open -- the count section 7 puts on a page. */
    public long countForRun(RunId runId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM extraction_fault WHERE run_id = ?", Long.class, runId.value());
        return count == null ? 0 : count;
    }
}
