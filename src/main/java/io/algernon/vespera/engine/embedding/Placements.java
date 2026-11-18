package io.algernon.vespera.engine.embedding;

import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.RunId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code embedding}'s record of where each scored document is placed (ADR-400, ADR-409 section 2), behind this class
 * and nothing else querying {@code winning_topic} or {@code see_also} (ADR-041).
 *
 * <p><b>Every scored document has one winning-topic row</b>, whether the relevance floor passes it or not (ADR-400
 * section 5), written beside its score and under the same key; no score at a topic is kept. A topic other than its
 * winning one that the document fits is a {@code see_also} row. Both are the scoring step's, under the scoring run,
 * and are discarded with the step's other rows (ADR-409 section 5).
 *
 * <p>A topic's members are read, and counted, in occurrence order by the index on the run, the topic and the
 * occurrence, which sorts nothing (ADR-409 section 12).
 */
@Component
public class Placements {

    private final JdbcTemplate jdbcTemplate;

    public Placements(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Records where {@code occurrenceId} is placed under {@code runId}: its winning topic, and the topics it fits. */
    public void record(OccurrenceId occurrenceId, RunId runId, Placement placement) {
        SeedPassage winning = placement.winningTopic().firstPassage();
        jdbcTemplate.update(
                "INSERT INTO winning_topic (occurrence_id, run_id, topic_seed_path, topic_chunk_index)"
                        + " VALUES (?, ?, ?, ?)",
                occurrenceId.value(),
                runId.value(),
                winning.seedPath(),
                winning.chunkIndex());
        for (TopicKey fitted : placement.seeAlso()) {
            jdbcTemplate.update(
                    "INSERT INTO see_also (occurrence_id, run_id, topic_seed_path, topic_chunk_index)"
                            + " VALUES (?, ?, ?, ?)",
                    occurrenceId.value(),
                    runId.value(),
                    fitted.firstPassage().seedPath(),
                    fitted.firstPassage().chunkIndex());
        }
    }

    /** Deletes every placement recorded under {@code runId}, with the scores of the same step. */
    public void discardForRun(RunId runId) {
        jdbcTemplate.update("DELETE FROM see_also WHERE run_id = ?", runId.value());
        jdbcTemplate.update("DELETE FROM winning_topic WHERE run_id = ?", runId.value());
    }

    /**
     * The topics {@code runId} placed at least one document under, in key order: one row a topic, grouped on the
     * index of the run and the topic and put in key order on the heap (ADR-409 section 12).
     */
    public List<TopicKey> topicsPlacedUnder(RunId runId) {
        List<TopicKey> topics = new ArrayList<>(jdbcTemplate.query(
                "SELECT topic_seed_path, topic_chunk_index FROM winning_topic WHERE run_id = ?"
                        + " GROUP BY topic_seed_path, topic_chunk_index",
                (resultSet, rowNumber) -> new TopicKey(new SeedPassage(
                        resultSet.getString("topic_seed_path"), resultSet.getInt("topic_chunk_index"))),
                runId.value()));
        topics.sort(Comparator.comparing(TopicKey::firstPassage));
        return topics;
    }

    /** The documents {@code runId} placed under {@code topic}, in occurrence order, the order clustering visits them in (ADR-087). */
    public List<OccurrenceId> membersOf(RunId runId, TopicKey topic) {
        return jdbcTemplate.query(
                "SELECT occurrence_id FROM winning_topic WHERE run_id = ? AND topic_seed_path = ?"
                        + " AND topic_chunk_index = ? ORDER BY occurrence_id",
                (resultSet, rowNumber) -> new OccurrenceId(resultSet.getLong("occurrence_id")),
                runId.value(),
                topic.firstPassage().seedPath(),
                topic.firstPassage().chunkIndex());
    }
}
