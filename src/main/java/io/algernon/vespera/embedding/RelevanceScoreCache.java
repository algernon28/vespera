package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.KeysetPages;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.Placeholders;
import io.algernon.vespera.ledger.RunId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
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

    /**
     * How many occurrences {@code runId} scored strictly below {@code floor}.
     *
     * <p>Strictly below: a document scoring exactly the floor survives it. The profile calls the key
     * a floor and describes the value as the score "below which" a survivor is removed, so the
     * boundary belongs to the documents that stay -- which is the reading that removes less.
     */
    long countScoredBelow(RunId runId, double floor) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relevance_score WHERE run_id = ? AND score < ?",
                Long.class,
                runId.value(),
                floor);
        return count == null ? 0 : count;
    }

    /**
     * Hands the occurrences {@code runId} scored strictly below {@code floor} to {@code page}, a page of up
     * to {@value KeysetPages#ROWS_IN_A_PAGE} at a time in the order the scores were written (ADR-220 section
     * 5), each page once its statement has finished and before the next is asked for. Strictly below, as
     * {@link #countScoredBelow}. Each page is planned by {@code relevance_score_by_run_id (run_id=? AND
     * rowid>?)}, sorting nothing.
     */
    void eachPageScoredBelow(RunId runId, double floor, Consumer<List<OccurrenceId>> page) {
        new KeysetPages<ScoreRow>(
                        jdbcTemplate,
                        "SELECT rowid, occurrence_id FROM relevance_score WHERE run_id = ? AND score < ?"
                                + " AND rowid > ? ORDER BY rowid LIMIT " + KeysetPages.ROWS_IN_A_PAGE,
                        List.of(runId.value(), floor),
                        List.of(Long.MIN_VALUE),
                        row -> List.of(row.rowid()),
                        (resultSet, rowNumber) -> new ScoreRow(
                                resultSet.getLong(1), new OccurrenceId(resultSet.getLong("occurrence_id"))))
                .eachPage(rows -> page.accept(rows.stream().map(ScoreRow::occurrence).toList()));
    }

    /** One score row as read back: the number it was written under, which the read pages by, and its occurrence. */
    private record ScoreRow(long rowid, OccurrenceId occurrence) {}

    /**
     * Deletes every relevance-score row recorded under {@code runId} — the discard half of
     * ADR-115/ADR-116, for a step whose completion under this run is not recorded: this table is
     * keyed {@code (occurrence_id, run_id)}, so a second write over a stopped invocation's rows would
     * otherwise collide on the first survivor it re-scored.
     */
    void discardForRun(RunId runId) {
        jdbcTemplate.update("DELETE FROM relevance_score WHERE run_id = ?", runId.value());
    }

    /**
     * The scores {@code runId} recorded for {@code occurrences}, holding only those that have one: asked of
     * the table's primary key {@value KeysetPages#ROWS_IN_A_PAGE} occurrences at most to a statement, and
     * none for an empty collection (ADR-240).
     */
    Map<OccurrenceId, RelevanceScore> forOccurrences(Collection<OccurrenceId> occurrences, RunId runId) {
        Map<OccurrenceId, RelevanceScore> scores = new HashMap<>();
        List<OccurrenceId> all = List.copyOf(occurrences);
        for (int from = 0; from < all.size(); from += KeysetPages.ROWS_IN_A_PAGE) {
            List<OccurrenceId> batch = all.subList(from, Math.min(all.size(), from + KeysetPages.ROWS_IN_A_PAGE));
            List<Object> arguments = new ArrayList<>();
            arguments.add(runId.value());
            batch.forEach(occurrence -> arguments.add(occurrence.value()));
            jdbcTemplate.query(
                    "SELECT occurrence_id, score, winning_seed_occurrence_id FROM relevance_score"
                            + " WHERE run_id = ? AND occurrence_id IN (" + Placeholders.of(batch.size()) + ")",
                    resultSet -> {
                        scores.put(
                                new OccurrenceId(resultSet.getLong("occurrence_id")),
                                new RelevanceScore(
                                        resultSet.getDouble("score"),
                                        new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id"))));
                    },
                    arguments.toArray());
        }
        return scores;
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
