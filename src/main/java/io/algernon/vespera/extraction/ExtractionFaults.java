package io.algernon.vespera.extraction;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code extraction}'s record of a refused conversion (ADR-139), behind this class and nothing else
 * querying the table (ADR-041).
 *
 * <p><b>The precedent is {@code unusable_seed} and {@code cluster_fault}, not the ledger.</b> A
 * verdict removes an occurrence from the survivor set; a fault row alone removes nothing -- it is
 * {@code pipeline}'s own fault-resolving listener (ADR-139 section 3) that turns one into {@code
 * extraction-failed}, and since ADR-220 section 14 it does so from the rows this class reads back a page
 * at a time: each fault row is written where the skip is heard, in the chunk's own transaction, and
 * nothing is held to the step's end. A resumed step reads the rows its stopped run left the same way
 * ({@link #eachPageOfFaulted}). This class writes, discards, counts and pages the fault rows; the category
 * column is otherwise there for a human's query.
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

    /** The most fault rows one statement of a read brings back. */
    private static final int ROWS_IN_A_PAGE = 1_000;

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
     * Hands the occurrences carrying a fault row under {@code runId} to {@code page}, a page of up to
     * {@value #ROWS_IN_A_PAGE} at a time in the order the rows were written, and answers how many it handed
     * over (ADR-181 section 1, ADR-220 section 2): the ones a resumed step reads again, and the ones whose
     * resolving verdicts it deletes first.
     *
     * <p>Each page is one statement, {@code extraction_fault_by_run_id (run_id=? AND rowid>?)}, sorting
     * nothing, and it is handed over once that statement has finished and before the next is issued, so
     * the caller may ask the ledger something between pages (ADR-193 section 2) and no more than one page is
     * ever held. {@code progress} is told its total, the span of the run's rows, empty where it holds none,
     * before the first page; the rows read so far after each page that held a row; and that it ended after
     * the last. It is never told SQLite's steps: no statement here is counted by them.
     */
    public long eachPageOfFaulted(
            RunId runId, ExtractionStatementProgress progress, Consumer<List<OccurrenceId>> page) {
        ExtractionStatement statement = ExtractionStatement.FAULTED_OCCURRENCES;
        progress.statementStarting(statement, faultRowsUpTo(runId));
        long[] handedOver = {0};
        eachPageOfRows(runId, rows -> {
            page.accept(rows.stream().map(FaultRow::occurrence).toList());
            handedOver[0] += rows.size();
            progress.rowsRead(statement, handedOver[0]);
        });
        progress.statementEnded(statement);
        return handedOver[0];
    }

    /**
     * Hands the run's fault rows, with their category and detail, to {@code page} a page of up to
     * {@value #ROWS_IN_A_PAGE} at a time in the order they were written, the same read {@link
     * #eachPageOfFaulted} makes with the two more columns: what resolving the faults of a completed step
     * reads (ADR-220 section 14). A page is handed over after its statement has finished.
     */
    void eachPageOfRows(RunId runId, Consumer<List<FaultRow>> page) {
        long after = Long.MIN_VALUE;
        while (true) {
            List<FaultRow> rows = jdbcTemplate.query(
                    "SELECT rowid, occurrence_id, category, detail FROM extraction_fault"
                            + " WHERE run_id = ? AND rowid > ? ORDER BY rowid LIMIT " + ROWS_IN_A_PAGE,
                    (resultSet, rowNumber) -> new FaultRow(
                            resultSet.getLong("rowid"),
                            new OccurrenceId(resultSet.getLong("occurrence_id")),
                            resultSet.getString("category"),
                            resultSet.getString("detail")),
                    runId.value(),
                    after);
            if (!rows.isEmpty()) {
                page.accept(rows);
                after = rows.getLast().rowid();
            }
            if (rows.size() < ROWS_IN_A_PAGE) {
                return;
            }
        }
    }

    /** One fault row as read back: the number it was written under, which the read pages by, and what it says. */
    record FaultRow(long rowid, OccurrenceId occurrence, String category, String detail) {}

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
