package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.List;
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
     * Every cluster recorded under {@code runId}, in the order the arrangement gives them.
     *
     * <p>Ordered by the stored columns rather than re-sorted, because the arrangement the operator
     * approved and the arrangement a reader receives have to be the same one (ADR-112).
     */
    public List<RecordedCluster> forRun(RunId runId) {
        return jdbcTemplate.query(
                "SELECT winning_seed_occurrence_id, cluster_ordinal, label, document_count,"
                        + " partition_order, cluster_order FROM cluster WHERE run_id = ?"
                        + " ORDER BY partition_order, cluster_order",
                (resultSet, rowNumber) -> new RecordedCluster(
                        new ArrangedCluster(
                                new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id")),
                                resultSet.getInt("cluster_ordinal"),
                                resultSet.getInt("document_count"),
                                resultSet.getInt("partition_order"),
                                resultSet.getInt("cluster_order")),
                        new ClusterLabel(resultSet.getString("label"))),
                runId.value());
    }
}
