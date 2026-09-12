package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code embedding}'s record of the seeds that produced no text (ADR-083), behind this class and
 * nothing else querying the table (ADR-041).
 *
 * <p><b>There is no verdict method here, deliberately.</b> This module cannot write one — the
 * vocabulary is closed and lives in {@code ledger} (ADR-042) — but the point is stronger than
 * mechanical: a seed is not a candidate, so no kind in that vocabulary applies to it. An unusable
 * seed does not stop the run either; it is recorded, reported, and scoring proceeds against the
 * seeds that survived extraction.
 *
 * <p>Rows are per run rather than per occurrence, so a corrected seed folder — which is a different
 * run, because the seed folder is part of what the measurement run's identity is derived from
 * (ADR-083, and #94 §9 for the run's own shape) — records its own row set beside the earlier one
 * rather than overwriting it. That is what keeps scores taken against a partial seed set from being
 * read as scores against a complete one, by identity rather than by a check anyone has to remember.
 */
@Component
public class UnusableSeeds {

    private final JdbcTemplate jdbcTemplate;

    public UnusableSeeds(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Records that {@code occurrenceId} produced no text under {@code runId}, and why. */
    public void record(OccurrenceId occurrenceId, RunId runId, String reason) {
        jdbcTemplate.update(
                "INSERT INTO unusable_seed (occurrence_id, run_id, reason) VALUES (?, ?, ?)",
                occurrenceId.value(),
                runId.value(),
                reason);
    }

    /** The seeds {@code runId} found unusable, for the report that tells an operator what to fix. */
    public List<UnusableSeed> forRun(RunId runId) {
        return jdbcTemplate.query(
                "SELECT occurrence_id, reason FROM unusable_seed WHERE run_id = ? ORDER BY occurrence_id",
                (resultSet, rowNumber) ->
                        new UnusableSeed(new OccurrenceId(resultSet.getLong("occurrence_id")), resultSet.getString("reason")),
                runId.value());
    }
}
