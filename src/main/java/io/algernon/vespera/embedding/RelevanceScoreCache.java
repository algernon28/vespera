package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code embedding}'s own record of a corpus survivor's relevance score (ADR-020, #108): the maximum
 * over resident seed documents and which one produced it, one row per occurrence per run.
 *
 * <p><b>Run-scoped, unlike {@link VectorCache}</b> — the one place this ticket's own table departs
 * the content-addressed shape {@code vector} takes. A score is a judgement about an occurrence under
 * a run, not a derivation of content under an instrument (ADR-041), so a re-run under a corrected
 * seed folder or a different model records its own row set beside the earlier one rather than
 * overwriting it.
 */
@Component
class RelevanceScoreCache {

    private final JdbcTemplate jdbcTemplate;

    RelevanceScoreCache(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Records {@code score} for {@code occurrenceId} under {@code runId}. */
    void record(OccurrenceId occurrenceId, RunId runId, RelevanceScore score) {
        jdbcTemplate.update(
                "INSERT INTO relevance_score (occurrence_id, run_id, score, winning_seed_occurrence_id)"
                        + " VALUES (?, ?, ?, ?)",
                occurrenceId.value(),
                runId.value(),
                score.score(),
                score.winningSeedOccurrenceId().value());
    }

    /** The score {@code runId} recorded for {@code occurrenceId}, if any — a test's own way to read one back. */
    Optional<RelevanceScore> forOccurrence(OccurrenceId occurrenceId, RunId runId) {
        return jdbcTemplate
                .query(
                        "SELECT score, winning_seed_occurrence_id FROM relevance_score"
                                + " WHERE occurrence_id = ? AND run_id = ?",
                        (resultSet, rowNumber) -> new RelevanceScore(
                                resultSet.getDouble("score"),
                                new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id"))),
                        occurrenceId.value(),
                        runId.value())
                .stream()
                .findFirst();
    }
}
