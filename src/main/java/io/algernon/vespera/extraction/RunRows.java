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
 * The two reads {@code extraction} makes over every row one run holds in one table, counted by SQLite's
 * progress handler (ADR-193 sections 1 and 2, ADR-199 section 2): the span of the run's rowids, which is
 * the total, and the occurrences the rows name.
 *
 * <p>The table is a constant of the caller, never an operator's text; each table named has an index on
 * {@code run_id} alone (ADR-173), which is what makes the span two descents and the steps a row the same
 * for every row.
 */
final class RunRows {

    private RunRows() {}

    /**
     * The span of {@code run}'s rowids in {@code table}, greatest less least plus one, asked as two statements
     * and never one (ADR-191 section 2). Empty for a run that holds no row, which has nothing to wait for.
     */
    static OptionalLong spanOf(JdbcTemplate jdbcTemplate, String table, RunId run) {
        Long least =
                jdbcTemplate.queryForObject("SELECT MIN(rowid) FROM " + table + " WHERE run_id = ?", Long.class, run.value());
        Long greatest =
                jdbcTemplate.queryForObject("SELECT MAX(rowid) FROM " + table + " WHERE run_id = ?", Long.class, run.value());
        if (least == null || greatest == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(greatest - least + 1);
    }

    /**
     * The occurrences {@code run}'s rows in {@code table} name, announced to {@code progress} as {@code
     * statement}: started with the span, given its steps, ended. The read runs on the connection the template
     * hands over and is never handed back to it, so the handler counts the read and nothing else.
     */
    static Set<OccurrenceId> occurrencesOf(
            JdbcTemplate jdbcTemplate,
            String table,
            RunId run,
            ExtractionStatement statement,
            ExtractionStatementProgress progress) {
        progress.statementStarting(statement, spanOf(jdbcTemplate, table, run));
        Set<OccurrenceId> occurrences = new HashSet<>();
        StatementSteps.counted(jdbcTemplate, steps -> progress.stepsTaken(statement, steps), connection -> {
            try (PreparedStatement select =
                    connection.prepareStatement("SELECT occurrence_id FROM " + table + " WHERE run_id = ?")) {
                select.setString(1, run.value());
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        occurrences.add(new OccurrenceId(rows.getLong("occurrence_id")));
                    }
                }
            }
            return null;
        });
        progress.statementEnded(statement);
        return occurrences;
    }
}
