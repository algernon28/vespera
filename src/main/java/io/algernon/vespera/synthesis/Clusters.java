package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code synthesis}'s record of the arrangement (ADR-105, ADR-110), behind this class and nothing
 * else querying the table (ADR-041).
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
                "INSERT INTO cluster (run_id, winning_seed_occurrence_id, cluster_ordinal, label,"
                        + " document_count, partition_order, cluster_order) VALUES (?, ?, ?, ?, ?, ?, ?)",
                runId.value(),
                cluster.winningSeed().value(),
                cluster.ordinal(),
                label.value(),
                cluster.documentCount(),
                cluster.partitionOrder(),
                cluster.clusterOrder());
    }

    /**
     * Deletes every cluster row recorded under {@code runId} — the discard half of ADR-115/ADR-116,
     * for a step whose completion under this run is not recorded: this table is keyed {@code (run_id,
     * winning_seed_occurrence_id, cluster_ordinal)}, so a second write over a stopped invocation's rows
     * would otherwise collide on the first cluster it re-arranged.
     */
    public void discardForRun(RunId runId) {
        jdbcTemplate.update("DELETE FROM cluster WHERE run_id = ?", runId.value());
    }

    /**
     * The seed partitions recorded under {@code arrangement}, one row each with how many clusters sit in it and
     * how many documents they hold (ADR-223 section 4), in the order the arrangement places them.
     *
     * <p>Read grouped by the seed, planned by the table's primary key with nothing sorted, and put in the
     * stored order on the heap: the arrangement the operator approved and the arrangement a reader receives
     * have to be the same one (ADR-112).
     */
    public List<ArrangedPartition> partitionsOf(RunId arrangement) {
        List<ArrangedPartition> partitions = new ArrayList<>(jdbcTemplate.query(
                "SELECT winning_seed_occurrence_id, MIN(partition_order) AS partition_order,"
                        + " COUNT(*) AS clusters, SUM(document_count) AS members FROM cluster"
                        + " WHERE run_id = ? GROUP BY winning_seed_occurrence_id",
                (resultSet, rowNumber) -> new ArrangedPartition(
                        new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id")),
                        resultSet.getInt("partition_order"),
                        resultSet.getInt("clusters"),
                        resultSet.getInt("members")),
                arrangement.value()));
        partitions.sort(Comparator.comparingInt(ArrangedPartition::partitionOrder));
        return partitions;
    }

    /** The winning seeds of {@link #partitionsOf}, in the same order. */
    public List<OccurrenceId> seedsOf(RunId arrangement) {
        return partitionsOf(arrangement).stream().map(ArrangedPartition::winningSeed).toList();
    }

    /**
     * The clusters recorded under {@code arrangement} in {@code winningSeed}'s partition, in the order the
     * arrangement gives them (ADR-223 section 3): read unordered and put in the stored {@code cluster_order}
     * on the heap, so that nothing is sorted by the database and no rule of the order is applied a second time.
     */
    public List<RecordedCluster> ofPartition(RunId arrangement, OccurrenceId winningSeed) {
        List<RecordedCluster> recorded = new ArrayList<>(jdbcTemplate.query(
                "SELECT cluster_ordinal, label, document_count, partition_order, cluster_order FROM cluster"
                        + " WHERE run_id = ? AND winning_seed_occurrence_id = ?",
                (resultSet, rowNumber) -> new RecordedCluster(
                        new ArrangedCluster(
                                winningSeed,
                                resultSet.getInt("cluster_ordinal"),
                                resultSet.getInt("document_count"),
                                resultSet.getInt("partition_order"),
                                resultSet.getInt("cluster_order")),
                        new ClusterLabel(resultSet.getString("label"))),
                arrangement.value(),
                winningSeed.value()));
        recorded.sort(Comparator.comparingInt((RecordedCluster cluster) -> cluster.cluster().clusterOrder()));
        return recorded;
    }

    /** The two places {@code arrangement} gives one cluster, read by its key, or empty where it records none. */
    public Optional<ArrangedCluster> placeOf(RunId arrangement, OccurrenceId winningSeed, int clusterOrdinal) {
        return jdbcTemplate
                .query(
                        "SELECT document_count, partition_order, cluster_order FROM cluster WHERE run_id = ?"
                                + " AND winning_seed_occurrence_id = ? AND cluster_ordinal = ?",
                        (resultSet, rowNumber) -> new ArrangedCluster(
                                winningSeed,
                                clusterOrdinal,
                                resultSet.getInt("document_count"),
                                resultSet.getInt("partition_order"),
                                resultSet.getInt("cluster_order")),
                        arrangement.value(),
                        winningSeed.value(),
                        clusterOrdinal)
                .stream()
                .findFirst();
    }

    /** How many clusters are recorded under {@code runId}. A count the database makes, reading no column of any row (ADR-220 section 7). */
    public int countForRun(RunId runId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cluster WHERE run_id = ?", Integer.class, runId.value());
        return count == null ? 0 : count;
    }
}
