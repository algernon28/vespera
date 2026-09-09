package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.List;
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

    /**
     * The seeds that won at least one survivor under {@code runId}, in occurrence order — one seed
     * partition each (ADR-045).
     *
     * <p>A seed nothing chose has no row and therefore no partition, rather than an empty one: a
     * partition is the set of documents a seed won, so a seed that won nothing is absent from this
     * list for the same reason a cluster with no members has no rows.
     */
    List<OccurrenceId> winningSeeds(RunId runId) {
        return jdbcTemplate.query(
                "SELECT DISTINCT winning_seed_occurrence_id FROM relevance_score WHERE run_id = ?"
                        + " ORDER BY winning_seed_occurrence_id",
                (resultSet, rowNumber) -> new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id")),
                runId.value());
    }

    /**
     * The survivors {@code winningSeed} won under {@code runId}, in occurrence order.
     *
     * <p>The order is the whole point of naming it here: ADR-087 fixes occurrence-id order as the one
     * clustering visits documents in, and an order the database chose freely would make the ordinals
     * an artefact of the query plan rather than a property of the corpus.
     */
    List<OccurrenceId> partitionMembers(RunId runId, OccurrenceId winningSeed) {
        return jdbcTemplate.query(
                "SELECT occurrence_id FROM relevance_score WHERE run_id = ?"
                        + " AND winning_seed_occurrence_id = ? ORDER BY occurrence_id",
                (resultSet, rowNumber) -> new OccurrenceId(resultSet.getLong("occurrence_id")),
                runId.value(),
                winningSeed.value());
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
