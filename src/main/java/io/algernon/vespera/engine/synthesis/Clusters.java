package io.algernon.vespera.engine.synthesis;

import io.algernon.vespera.engine.ledger.KeysetPages;
import io.algernon.vespera.engine.ledger.ListedPages;
import io.algernon.vespera.engine.ledger.Placeholders;
import io.algernon.vespera.engine.ledger.RunId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code synthesis}'s record of the arrangement (ADR-105, ADR-110, ADR-409 section 3), behind this class and nothing
 * else querying {@code arranged_cluster} (ADR-041).
 *
 * <p><b>One row per cluster, and membership is left alone.</b> Which documents are in a cluster is
 * stage 5's, recorded as one row per document carrying the cluster's identity; restating it here
 * would duplicate tens of thousands of rows and recreate exactly the drift that shape was chosen to
 * make impossible. What this adds is the level that did not exist — a cluster as something with a
 * name, a size and a place.
 *
 * <p>Rows are keyed by run, so a re-arrangement writes its own row set rather than overwriting an
 * earlier one (ADR-077). That is what makes the gate's approval of a named run mean something: the
 * arrangement approved is still there to be read after a later one is written beside it.
 *
 * <p><b>Nothing here writes a verdict.</b> Stage 6a removes nothing, so no blocking kind applies —
 * and it does not write a passing one either, which is a gap between ADR-049 and this code that is
 * real and deliberately not closed over the one stage in the cascade that judges nothing.
 */
@Component
public class Clusters {

    private final JdbcTemplate jdbcTemplate;

    public Clusters(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Records one arranged cluster under {@code runId}, which is the 6a run. */
    public void record(RunId runId, ArrangedCluster cluster, ClusterLabel label) {
        jdbcTemplate.update(
                "INSERT INTO arranged_cluster (run_id, topic_seed_path, topic_chunk_index, subtopic_ordinal,"
                        + " cluster_ordinal, label, document_count, topic_order, subtopic_order, cluster_order)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                runId.value(),
                cluster.topic().seedPath(),
                cluster.topic().chunkIndex(),
                Subtopic.inRow(cluster.subtopic()),
                cluster.ordinal(),
                label.value(),
                cluster.documentCount(),
                cluster.topicOrder(),
                cluster.subtopicOrder(),
                cluster.clusterOrder());
    }

    /**
     * Deletes every cluster row recorded under {@code runId} — the discard half of ADR-115/ADR-116,
     * for a step whose completion under this run is not recorded: this table is keyed by the run, the topic, the
     * subtopic and the ordinal, so a second write over a stopped invocation's rows would otherwise collide on the
     * first cluster it re-arranged.
     */
    public void discardForRun(RunId runId) {
        jdbcTemplate.update("DELETE FROM arranged_cluster WHERE run_id = ?", runId.value());
    }

    /**
     * The topics recorded under {@code arrangement}, one row each with how many clusters sit in it and how many
     * documents they hold (ADR-223 section 4), in the order the arrangement places them.
     *
     * <p>Read grouped by the topic, planned by the table's primary key with nothing sorted, and put in the stored
     * order on the heap: the arrangement the operator approved and the arrangement a reader receives have to be the
     * same one (ADR-112).
     */
    public List<ArrangedTopic> topicsOf(RunId arrangement) {
        List<ArrangedTopic> topics = new ArrayList<>(jdbcTemplate.query(
                "SELECT topic_seed_path, topic_chunk_index, MIN(topic_order) AS topic_order,"
                        + " COUNT(*) AS clusters, SUM(document_count) AS members FROM arranged_cluster"
                        + " WHERE run_id = ? GROUP BY topic_seed_path, topic_chunk_index",
                (resultSet, rowNumber) -> new ArrangedTopic(
                        new TopicKey(resultSet.getString("topic_seed_path"), resultSet.getInt("topic_chunk_index")),
                        resultSet.getInt("topic_order"),
                        resultSet.getInt("clusters"),
                        resultSet.getInt("members")),
                arrangement.value()));
        topics.sort(Comparator.comparingInt(ArrangedTopic::topicOrder));
        return topics;
    }

    /** The topics of {@link #topicsOf}, in the same order. */
    public List<TopicKey> keysOf(RunId arrangement) {
        return topicsOf(arrangement).stream().map(ArrangedTopic::topic).toList();
    }

    /**
     * The clusters recorded under {@code arrangement} in {@code topic}, in the order the arrangement gives them
     * (ADR-223 section 3): subtopic by subtopic in {@code subtopic_order}, each subtopic's in {@code cluster_order}. Read
     * unordered and put in the stored order on the heap, so that nothing is sorted by the database and no rule of the
     * order is applied a second time.
     */
    public List<RecordedCluster> ofTopic(RunId arrangement, TopicKey topic) {
        List<RecordedCluster> recorded = new ArrayList<>(jdbcTemplate.query(
                "SELECT subtopic_ordinal, cluster_ordinal, label, document_count, topic_order, subtopic_order,"
                        + " cluster_order FROM arranged_cluster"
                        + " WHERE run_id = ? AND topic_seed_path = ? AND topic_chunk_index = ?",
                (resultSet, rowNumber) -> new RecordedCluster(
                        new ArrangedCluster(
                                topic,
                                Subtopic.ofRow(resultSet.getInt("subtopic_ordinal")),
                                resultSet.getInt("cluster_ordinal"),
                                resultSet.getInt("document_count"),
                                resultSet.getInt("topic_order"),
                                resultSet.getInt("subtopic_order"),
                                resultSet.getInt("cluster_order")),
                        new ClusterLabel(resultSet.getString("label"))),
                arrangement.value(),
                topic.seedPath(),
                topic.chunkIndex()));
        recorded.sort(Comparator.comparingInt((RecordedCluster cluster) -> cluster.cluster().subtopicOrder())
                .thenComparingInt(cluster -> cluster.cluster().clusterOrder()));
        return recorded;
    }

    /**
     * The places {@code arrangement} gives each of {@code clusters}, holding only those it records: the manifest's
     * question for a page of survivors (ADR-241). One statement for each topic and subtopic among them and each
     * {@value KeysetPages#ROWS_IN_A_PAGE} of its ordinals, planned as a lookup by the table's primary key; none for an
     * empty collection.
     */
    public Map<ClusterSlot, ArrangedCluster> placesOf(RunId arrangement, Collection<ClusterSlot> clusters) {
        Map<TopicKey, Map<OptionalInt, List<Integer>>> ordinals = new LinkedHashMap<>();
        for (ClusterSlot cluster : clusters) {
            ordinals.computeIfAbsent(cluster.topic(), topic -> new LinkedHashMap<>())
                    .computeIfAbsent(cluster.subtopic(), subtopic -> new ArrayList<>())
                    .add(cluster.clusterOrdinal());
        }
        Map<ClusterSlot, ArrangedCluster> places = new HashMap<>();
        ordinals.forEach((topic, bySubtopic) -> bySubtopic.forEach((subtopic, inSubtopic) -> {
            ListedPages.each(inSubtopic, batch -> {
                List<Object> arguments = new ArrayList<>();
                arguments.add(arrangement.value());
                arguments.add(topic.seedPath());
                arguments.add(topic.chunkIndex());
                arguments.add(Subtopic.inRow(subtopic));
                arguments.addAll(batch);
                jdbcTemplate.query(
                        "SELECT cluster_ordinal, document_count, topic_order, subtopic_order, cluster_order"
                                + " FROM arranged_cluster WHERE run_id = ? AND topic_seed_path = ?"
                                + " AND topic_chunk_index = ? AND subtopic_ordinal = ? AND cluster_ordinal IN ("
                                + Placeholders.of(batch.size()) + ")",
                        resultSet -> {
                            int ordinal = resultSet.getInt("cluster_ordinal");
                            places.put(
                                    new ClusterSlot(topic, subtopic, ordinal),
                                    new ArrangedCluster(
                                            topic,
                                            subtopic,
                                            ordinal,
                                            resultSet.getInt("document_count"),
                                            resultSet.getInt("topic_order"),
                                            resultSet.getInt("subtopic_order"),
                                            resultSet.getInt("cluster_order")));
                        },
                        arguments.toArray());
            });
        }));
        return places;
    }

    /** How many clusters are recorded under {@code runId}. A count the database makes, reading no column of any row (ADR-220 section 7). */
    public int countForRun(RunId runId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM arranged_cluster WHERE run_id = ?", Integer.class, runId.value());
        return count == null ? 0 : count;
    }
}
