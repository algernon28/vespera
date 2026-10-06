package io.algernon.vespera.extraction;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.StatementSteps;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.OptionalLong;
import java.util.Set;
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

    /**
     * The occurrences carrying a fault row under {@code runId} (ADR-181 section 1): the ones a resumed
     * step reads again, and the ones whose resolving verdicts it deletes first.
     */
    public Set<OccurrenceId> occurrencesForRun(RunId runId) {
        return occurrencesForRun(runId, ExtractionStatementProgress.NONE);
    }

    /**
     * The same, counted (ADR-199 section 1): {@code progress} is told its total before the read (empty
     * where the run holds no row), the steps SQLite has taken at each callback, and that it ended. The read
     * goes through one run's rows by an index on {@code run_id} alone, so the total is cheap; the read is
     * always issued, and where the run holds no row it finds none.
     */
    public Set<OccurrenceId> occurrencesForRun(RunId runId, ExtractionStatementProgress progress) {
        OptionalLong rowsUpTo = faultRowsUpTo(runId);
        ExtractionStatement statement = ExtractionStatement.FAULTED_OCCURRENCES;
        progress.statementStarting(statement, rowsUpTo);
        Set<OccurrenceId> faulted = StatementSteps.counted(
                jdbcTemplate, steps -> progress.stepsTaken(statement, steps), connection -> {
                    Set<OccurrenceId> found = new HashSet<>();
                    try (PreparedStatement read = connection.prepareStatement(
                            "SELECT occurrence_id FROM extraction_fault WHERE run_id = ?")) {
                        read.setString(1, runId.value());
                        try (ResultSet rows = read.executeQuery()) {
                            while (rows.next()) {
                                found.add(new OccurrenceId(rows.getLong("occurrence_id")));
                            }
                        }
                    }
                    return found;
                });
        progress.statementEnded(statement);
        return faulted;
    }

    /**
     * The span of {@code runId}'s fault rows, the most the read goes through, empty where it holds none. Two
     * statements on purpose, as {@code DocumentFrequency.shingleRowsUpTo} explains: each is one descent of
     * {@code extraction_fault_by_run_id}, and one asking for both, or for a count, would read the run's part
     * of the index.
     */
    OptionalLong faultRowsUpTo(RunId runId) {
        Long least = jdbcTemplate.queryForObject(
                "SELECT MIN(rowid) FROM extraction_fault WHERE run_id = ?", Long.class, runId.value());
        Long greatest = jdbcTemplate.queryForObject(
                "SELECT MAX(rowid) FROM extraction_fault WHERE run_id = ?", Long.class, runId.value());
        if (least == null || greatest == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(greatest - least + 1);
    }

    /** How many occurrences {@code runId}'s step refused to open -- the count section 7 puts on a page. */
    public long countForRun(RunId runId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM extraction_fault WHERE run_id = ?", Long.class, runId.value());
        return count == null ? 0 : count;
    }
}
