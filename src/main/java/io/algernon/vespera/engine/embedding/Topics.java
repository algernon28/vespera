package io.algernon.vespera.engine.embedding;

import io.algernon.vespera.engine.ledger.RunId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code embedding}'s record of the topics that stand, their passages and the seeds feeding none (ADR-409 section 2),
 * behind this class and nothing else querying {@code topic}, {@code topic_passage} or {@code seed_feeding_no_topic}
 * (ADR-041).
 *
 * <p><b>Written once, by the scoring step, under the scoring run</b>, once the breadth floor is applied and before
 * the first survivor is scored (ADR-409 section 5), and read by the arrangement step, which finds none again: a
 * topic is built from its rows, its passages in visiting order. A topic is written as its key, the seed path and the
 * chunk index of its first passage (ADR-409 section 1).
 *
 * <p>Nothing here sorts: a run's topics are read in key order by the table's primary key, and a topic's passages in
 * visiting order by the index on the run and the topic (ADR-409 section 12).
 */
@Component
public class Topics {

    private final JdbcTemplate jdbcTemplate;

    public Topics(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Records the topics that stand under {@code runId}, each with its passages, and the seeds feeding none.
     */
    public void record(RunId runId, StandingTopics standing) {
        for (Topic topic : standing.topics()) {
            SeedPassage first = topic.key().firstPassage();
            jdbcTemplate.update(
                    "INSERT INTO topic (run_id, seed_path, chunk_index, passage_count) VALUES (?, ?, ?, ?)",
                    runId.value(),
                    first.seedPath(),
                    first.chunkIndex(),
                    topic.passages().size());
            for (SeedPassage passage : topic.passages()) {
                jdbcTemplate.update(
                        "INSERT INTO topic_passage (run_id, seed_path, chunk_index, topic_seed_path,"
                                + " topic_chunk_index) VALUES (?, ?, ?, ?, ?)",
                        runId.value(),
                        passage.seedPath(),
                        passage.chunkIndex(),
                        first.seedPath(),
                        first.chunkIndex());
            }
        }
        for (String seedPath : standing.seedsFeedingNoTopic()) {
            jdbcTemplate.update(
                    "INSERT INTO seed_feeding_no_topic (run_id, seed_path) VALUES (?, ?)", runId.value(), seedPath);
        }
    }

    /**
     * The topics {@code runId} kept, in key order, each with its passages in visiting order (ADR-409 section 5): what
     * the arrangement step builds its {@link Topic}s from.
     */
    public List<Topic> standingUnder(RunId runId) {
        List<TopicKey> keys = jdbcTemplate.query(
                "SELECT seed_path, chunk_index FROM topic WHERE run_id = ? ORDER BY seed_path, chunk_index",
                (resultSet, rowNumber) -> new TopicKey(
                        new SeedPassage(resultSet.getString("seed_path"), resultSet.getInt("chunk_index"))),
                runId.value());
        List<Topic> topics = new ArrayList<>();
        for (TopicKey key : keys) {
            List<SeedPassage> passages = jdbcTemplate.query(
                    "SELECT seed_path, chunk_index FROM topic_passage WHERE run_id = ? AND topic_seed_path = ?"
                            + " AND topic_chunk_index = ? ORDER BY seed_path, chunk_index",
                    (resultSet, rowNumber) ->
                            new SeedPassage(resultSet.getString("seed_path"), resultSet.getInt("chunk_index")),
                    runId.value(),
                    key.firstPassage().seedPath(),
                    key.firstPassage().chunkIndex());
            topics.add(new Topic(key, passages));
        }
        return List.copyOf(topics);
    }

    /**
     * Deletes every row recorded under {@code runId}, the discard half of ADR-115 and ADR-116, with the step's other
     * rows (ADR-409 section 5).
     */
    public void discardForRun(RunId runId) {
        jdbcTemplate.update("DELETE FROM topic_passage WHERE run_id = ?", runId.value());
        jdbcTemplate.update("DELETE FROM topic WHERE run_id = ?", runId.value());
        jdbcTemplate.update("DELETE FROM seed_feeding_no_topic WHERE run_id = ?", runId.value());
    }
}
