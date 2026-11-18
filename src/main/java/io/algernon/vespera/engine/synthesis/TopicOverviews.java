package io.algernon.vespera.engine.synthesis;

import io.algernon.vespera.engine.ledger.RunId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stage 6b's record of each topic's overview (ADR-402, ADR-409 sections 4 and 5), behind this class and nothing else
 * querying {@code topic_overview}, {@code topic_overview_source} or {@code topic_overview_fault} (ADR-041).
 *
 * <p><b>An overview's rows are written in one transaction</b> (the step's), its sources with its writing, or its fault
 * alone, so that no row of a topic is left without the others and none has to be cleared before a topic is asked:
 * nothing deletes from the three tables. A topic has a row in {@code topic_overview} or in {@code
 * topic_overview_fault}, never both, and is asked once under its run, or twice, and never again.
 */
@Component
public class TopicOverviews {

    private final JdbcTemplate jdbcTemplate;

    public TopicOverviews(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Whether {@code topic} has an overview or a turned-down overview recorded under {@code generation}. */
    public boolean isSettled(RunId generation, TopicKey topic) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT (SELECT COUNT(*) FROM topic_overview WHERE run_id = ? AND topic_seed_path = ?"
                        + " AND topic_chunk_index = ?) + (SELECT COUNT(*) FROM topic_overview_fault WHERE run_id = ?"
                        + " AND topic_seed_path = ? AND topic_chunk_index = ?)",
                Integer.class,
                generation.value(),
                topic.seedPath(),
                topic.chunkIndex(),
                generation.value(),
                topic.seedPath(),
                topic.chunkIndex());
        return count != null && count > 0;
    }

    /**
     * Records the overview of {@code topic} that was believed: its writing, the asking it was believed at, and one row for
     * each number the call sent, naming the cluster at that number ({@code clustersByNumber}, the cluster at number
     * {@code n} being the {@code n}th, from 1).
     */
    public void recordWritten(
            RunId generation, TopicKey topic, TopicOverviewOutcome.Written written, List<ClusterSlot> clustersByNumber) {
        jdbcTemplate.update(
                "INSERT INTO topic_overview (run_id, topic_seed_path, topic_chunk_index, writing, asked)"
                        + " VALUES (?, ?, ?, ?, ?)",
                generation.value(),
                topic.seedPath(),
                topic.chunkIndex(),
                written.writing(),
                written.asked());
        for (int number : written.numbersSent()) {
            ClusterSlot cluster = clustersByNumber.get(number - 1);
            jdbcTemplate.update(
                    "INSERT INTO topic_overview_source (run_id, topic_seed_path, topic_chunk_index, number,"
                            + " subtopic_ordinal, cluster_ordinal) VALUES (?, ?, ?, ?, ?, ?)",
                    generation.value(),
                    topic.seedPath(),
                    topic.chunkIndex(),
                    number,
                    Subtopic.inRow(cluster.subtopic()),
                    cluster.clusterOrdinal());
        }
    }

    /** Records that the overview of {@code topic} was turned down, with how many answers were asked for. */
    public void recordTurnedDown(RunId generation, TopicKey topic, TopicOverviewOutcome.TurnedDown turnedDown) {
        jdbcTemplate.update(
                "INSERT INTO topic_overview_fault (run_id, topic_seed_path, topic_chunk_index, kind, detail, asked)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                generation.value(),
                topic.seedPath(),
                topic.chunkIndex(),
                turnedDown.fault().kind().name(),
                turnedDown.fault().detail(),
                turnedDown.asked());
    }
}
