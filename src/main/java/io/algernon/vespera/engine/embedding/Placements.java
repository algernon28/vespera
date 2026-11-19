package io.algernon.vespera.engine.embedding;

import io.algernon.vespera.engine.ledger.KeysetPages;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.RunId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
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

    /**
     * Hands the documents with a link to {@code topic} under {@code runId} to {@code page}, a page of up to {@value
     * KeysetPages#ROWS_IN_A_PAGE} at a time in occurrence order, by the index on the run, the topic and the occurrence,
     * sorting nothing (ADR-416 section 8). Each page is handed over once its statement has finished.
     */
    public void eachPageLinkedTo(RunId runId, TopicKey topic, Consumer<List<OccurrenceId>> page) {
        new KeysetPages<OccurrenceId>(
                        jdbcTemplate,
                        "SELECT occurrence_id FROM see_also WHERE run_id = ? AND topic_seed_path = ?"
                                + " AND topic_chunk_index = ? AND occurrence_id > ? ORDER BY occurrence_id",
                        List.of(runId.value(), topic.firstPassage().seedPath(), topic.firstPassage().chunkIndex()),
                        List.of(Long.MIN_VALUE),
                        last -> List.of(last.value()),
                        (resultSet, rowNumber) -> new OccurrenceId(resultSet.getLong("occurrence_id")))
                .eachPage(page);
    }

    /**
     * One survivor that fits a topic other than the one it is placed under: its occurrence, the topic it is placed under,
     * and the cluster it is a member of there (ADR-415 section 5).
     */
    public record SeeAlsoRow(OccurrenceId occurrence, TopicKey placedUnder, OptionalInt subtopic, int clusterOrdinal) {}

    /**
     * Hands {@code page} the documents that fit {@code fitted}, other than the topic they are placed under, and are
     * placed under {@code placedUnder}, each with the cluster it is a member of there, a page of up to {@value
     * KeysetPages#ROWS_IN_A_PAGE} at a time in occurrence order, each once its statement has finished (ADR-415 section 5,
     * ADR-409 section 12). Each page begins after the last occurrence of the one before; it is read by the index on the
     * run, the topic and the occurrence, which gives the rows in occurrence order and sorts nothing, and the cluster is
     * found by the primary key of the memberships. A document no clustering placed has no cluster and is not handed on.
     */
    public void eachPageOfSeeAlso(
            RunId runId, TopicKey fitted, TopicKey placedUnder, Consumer<List<SeeAlsoRow>> page) {
        SeedPassage under = placedUnder.firstPassage();
        new KeysetPages<SeeAlsoRow>(
                        jdbcTemplate,
                        "SELECT s.occurrence_id, c.subtopic_ordinal, c.cluster_ordinal FROM see_also s"
                                + " JOIN cluster_member c ON c.occurrence_id = s.occurrence_id AND c.run_id = s.run_id"
                                + " WHERE s.run_id = ? AND s.topic_seed_path = ? AND s.topic_chunk_index = ?"
                                + " AND +c.topic_seed_path = ? AND +c.topic_chunk_index = ? AND s.occurrence_id > ?"
                                + " ORDER BY s.occurrence_id",
                        List.of(
                                runId.value(),
                                fitted.firstPassage().seedPath(),
                                fitted.firstPassage().chunkIndex(),
                                under.seedPath(),
                                under.chunkIndex()),
                        List.of(Long.MIN_VALUE),
                        last -> List.of(last.occurrence().value()),
                        (resultSet, rowNumber) -> {
                            int subtopic = resultSet.getInt("subtopic_ordinal");
                            return new SeeAlsoRow(
                                    new OccurrenceId(resultSet.getLong("occurrence_id")),
                                    placedUnder,
                                    subtopic == DocumentClusters.NO_SUBTOPIC ? OptionalInt.empty() : OptionalInt.of(subtopic),
                                    resultSet.getInt("cluster_ordinal"));
                        })
                .eachPage(page);
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
