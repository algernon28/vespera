package io.algernon.vespera.engine.embedding;

import io.algernon.vespera.engine.ledger.KeysetPages;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.RunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code embedding}'s record of which cluster each survivor landed in (ADR-087), behind this class
 * and nothing else querying the table (ADR-041).
 *
 * <p><b>One row per survivor, and a cluster is the set of rows carrying its identity</b> — the run, the topic, the
 * subtopic and the ordinal (ADR-409 section 3). A cluster row of its own would be a second place the truth
 * lived, which membership would then have to be kept in step with. A topic with no subtopics is written with a
 * subtopic of {@value #NO_SUBTOPIC}, and read back as an empty {@link OptionalInt}.
 *
 * <p>Rows are keyed by occurrence and run: clustering is derived under a configuration, so a re-run
 * writes its own row set (ADR-077) rather than overwriting an earlier one. That is the ordinary rule
 * here, and it is the opposite of {@link RelevanceLabels}, whose rows record a person's answer and
 * are never rewritten.
 *
 * <p><b>Nothing here writes a verdict.</b> Clustering removes nothing; it arranges what survived.
 */
@Component
public class DocumentClusters {

    /** How a row writes that its topic has no subtopics (ADR-409 section 3). */
    static final int NO_SUBTOPIC = -1;

    private final JdbcTemplate jdbcTemplate;

    public DocumentClusters(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Records that {@code occurrenceId} landed in cluster {@code ordinal} of {@code subtopic} of {@code topic}. */
    public void record(RunId runId, OccurrenceId occurrenceId, TopicKey topic, OptionalInt subtopic, int ordinal) {
        jdbcTemplate.update(
                "INSERT INTO cluster_member (occurrence_id, run_id, topic_seed_path, topic_chunk_index,"
                        + " subtopic_ordinal, cluster_ordinal) VALUES (?, ?, ?, ?, ?, ?)",
                occurrenceId.value(),
                runId.value(),
                topic.firstPassage().seedPath(),
                topic.firstPassage().chunkIndex(),
                subtopic.orElse(NO_SUBTOPIC),
                ordinal);
    }

    /**
     * Deletes every membership row recorded under {@code runId} — the discard half of
     * ADR-115/ADR-116, for a step whose completion under this run is not recorded, and, since ADR-230, for
     * clustering done again under a run whose completion is recorded.
     */
    public void discardForRun(RunId runId) {
        jdbcTemplate.update("DELETE FROM cluster_member WHERE run_id = ?", runId.value());
    }

    /**
     * How many survivors {@code topic} holds under {@code runId}: a count the database makes, reading no column of
     * any row (ADR-223 section 3). A topic with none has no row.
     */
    public int sizeOf(RunId runId, TopicKey topic) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cluster_member WHERE run_id = ? AND topic_seed_path = ? AND topic_chunk_index = ?",
                Integer.class,
                runId.value(),
                topic.firstPassage().seedPath(),
                topic.firstPassage().chunkIndex());
        return count == null ? 0 : count;
    }

    /**
     * The topics that hold a clustered survivor under {@code runId}, each with how many it holds, in key order (ADR-409
     * sections 12, 13): one row a topic, grouped on the index of the run and the topic, which is read in the order of
     * the key and sorts nothing.
     */
    public Map<TopicKey, Integer> sizesOfTopics(RunId runId) {
        Map<TopicKey, Integer> counted = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT topic_seed_path, topic_chunk_index, COUNT(*) AS members FROM cluster_member"
                        + " WHERE run_id = ? GROUP BY topic_seed_path, topic_chunk_index",
                resultSet -> {
                    counted.put(topicOf(resultSet), resultSet.getInt("members"));
                },
                runId.value());
        List<TopicKey> inKeyOrder = new ArrayList<>(counted.keySet());
        inKeyOrder.sort(Comparator.comparing(TopicKey::firstPassage));
        Map<TopicKey, Integer> sizes = new LinkedHashMap<>();
        inKeyOrder.forEach(topic -> sizes.put(topic, counted.get(topic)));
        return sizes;
    }

    /**
     * The membership of {@code topic} under {@code runId}, in occurrence order: read unordered and put in order on
     * the heap, so that nothing is sorted by the database (ADR-223 section 3). The order matters to a caller: a
     * cluster's average closeness is a sum taken in it, and the document a cluster is named after is the first of
     * equal scores.
     */
    public List<DocumentCluster> membersOf(RunId runId, TopicKey topic) {
        List<DocumentCluster> members = new ArrayList<>(jdbcTemplate.query(
                "SELECT occurrence_id, subtopic_ordinal, cluster_ordinal FROM cluster_member"
                        + " WHERE run_id = ? AND topic_seed_path = ? AND topic_chunk_index = ?",
                (resultSet, rowNumber) -> new DocumentCluster(
                        new OccurrenceId(resultSet.getLong("occurrence_id")),
                        topic,
                        subtopicOf(resultSet),
                        resultSet.getInt("cluster_ordinal")),
                runId.value(),
                topic.firstPassage().seedPath(),
                topic.firstPassage().chunkIndex()));
        members.sort(Comparator.comparingLong((DocumentCluster member) -> member.occurrenceId().value()));
        return members;
    }

    /**
     * Hands every membership recorded under {@code runId} to {@code page}, a page of up to {@value
     * KeysetPages#ROWS_IN_A_PAGE} at a time in occurrence order, each once its statement has finished (ADR-223
     * section 7). Each page begins after the last occurrence of the one before, and is planned by the primary
     * key with the run compared as a value, so that the index on the run is not chosen and nothing is sorted.
     */
    public void eachPage(RunId runId, Consumer<List<DocumentCluster>> page) {
        new KeysetPages<DocumentCluster>(
                        jdbcTemplate,
                        "SELECT occurrence_id, topic_seed_path, topic_chunk_index, subtopic_ordinal, cluster_ordinal"
                                + " FROM cluster_member WHERE occurrence_id > ? AND +run_id = ? ORDER BY occurrence_id",
                        List.of(Long.MIN_VALUE, runId.value()),
                        last -> List.of(last.occurrenceId().value(), runId.value()),
                        (resultSet, rowNumber) -> new DocumentCluster(
                                new OccurrenceId(resultSet.getLong("occurrence_id")),
                                topicOf(resultSet),
                                subtopicOf(resultSet),
                                resultSet.getInt("cluster_ordinal")))
                .eachPage(page);
    }

    /**
     * How large each cluster in {@code topic} is, in subtopic and then cluster order — the spread a reader needs to
     * see whether one cluster swallowed the topic or it broke into singletons.
     */
    public List<Integer> sizesFor(RunId runId, TopicKey topic) {
        return jdbcTemplate.query(
                "SELECT COUNT(*) AS members FROM cluster_member WHERE run_id = ? AND topic_seed_path = ?"
                        + " AND topic_chunk_index = ? GROUP BY subtopic_ordinal, cluster_ordinal"
                        + " ORDER BY subtopic_ordinal, cluster_ordinal",
                (resultSet, rowNumber) -> resultSet.getInt("members"),
                runId.value(),
                topic.firstPassage().seedPath(),
                topic.firstPassage().chunkIndex());
    }

    /**
     * How many survivors each subtopic of {@code topic} holds under {@code runId}, by the subtopic's number (ADR-416
     * section 8): grouped on the index of the run, the topic and the subtopic, which sorts nothing. Empty where the topic
     * is not divided.
     */
    public Map<Integer, Integer> survivorsOfSubtopics(RunId runId, TopicKey topic) {
        Map<Integer, Integer> counted = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT subtopic_ordinal, COUNT(*) AS members FROM cluster_member WHERE run_id = ?"
                        + " AND topic_seed_path = ? AND topic_chunk_index = ? AND subtopic_ordinal >= 0"
                        + " GROUP BY subtopic_ordinal",
                resultSet -> {
                    counted.put(resultSet.getInt("subtopic_ordinal"), resultSet.getInt("members"));
                },
                runId.value(),
                topic.firstPassage().seedPath(),
                topic.firstPassage().chunkIndex());
        return counted;
    }

    /**
     * How large each cluster of one subtopic of {@code topic} is, in cluster order (ADR-416 section 8), read from the
     * index of the run, the topic, the subtopic and the cluster.
     */
    public List<Integer> sizesFor(RunId runId, TopicKey topic, int subtopic) {
        return jdbcTemplate.query(
                "SELECT COUNT(*) AS members FROM cluster_member WHERE run_id = ? AND topic_seed_path = ?"
                        + " AND topic_chunk_index = ? AND subtopic_ordinal = ? GROUP BY cluster_ordinal"
                        + " ORDER BY cluster_ordinal",
                (resultSet, rowNumber) -> resultSet.getInt("members"),
                runId.value(),
                topic.firstPassage().seedPath(),
                topic.firstPassage().chunkIndex(),
                subtopic);
    }

    private static TopicKey topicOf(ResultSet resultSet) throws SQLException {
        return new TopicKey(
                new SeedPassage(resultSet.getString("topic_seed_path"), resultSet.getInt("topic_chunk_index")));
    }

    private static OptionalInt subtopicOf(ResultSet resultSet) throws SQLException {
        int subtopic = resultSet.getInt("subtopic_ordinal");
        return subtopic == NO_SUBTOPIC ? OptionalInt.empty() : OptionalInt.of(subtopic);
    }
}
