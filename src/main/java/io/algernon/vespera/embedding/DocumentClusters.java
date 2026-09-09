package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code embedding}'s record of which cluster each survivor landed in (ADR-087), behind this class
 * and nothing else querying the table (ADR-041).
 *
 * <p><b>One row per survivor, and a cluster is the set of rows carrying its identity</b> — the run,
 * the winning seed and the ordinal. A cluster row of its own would be a second place the truth
 * lived, which membership would then have to be kept in step with.
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

    private final JdbcTemplate jdbcTemplate;

    public DocumentClusters(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Records that {@code occurrenceId} landed in cluster {@code ordinal} of its seed's partition. */
    public void record(RunId runId, OccurrenceId occurrenceId, OccurrenceId winningSeed, int ordinal) {
        jdbcTemplate.update(
                "INSERT INTO document_cluster (occurrence_id, run_id, winning_seed_occurrence_id,"
                        + " cluster_ordinal) VALUES (?, ?, ?, ?)",
                occurrenceId.value(),
                runId.value(),
                winningSeed.value(),
                ordinal);
    }

    /** Every membership recorded under {@code runId}, in occurrence order. */
    public List<DocumentCluster> forRun(RunId runId) {
        return jdbcTemplate.query(
                "SELECT occurrence_id, winning_seed_occurrence_id, cluster_ordinal FROM document_cluster"
                        + " WHERE run_id = ? ORDER BY occurrence_id",
                (resultSet, rowNumber) -> new DocumentCluster(
                        new OccurrenceId(resultSet.getLong("occurrence_id")),
                        new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id")),
                        resultSet.getInt("cluster_ordinal")),
                runId.value());
    }

    /**
     * How large each cluster in one seed's partition is, in ordinal order — the spread a reader needs
     * to see whether one cluster swallowed the partition or it broke into singletons.
     */
    public List<Integer> sizesFor(RunId runId, OccurrenceId winningSeed) {
        return jdbcTemplate.query(
                "SELECT COUNT(*) AS members FROM document_cluster WHERE run_id = ?"
                        + " AND winning_seed_occurrence_id = ? GROUP BY cluster_ordinal ORDER BY cluster_ordinal",
                (resultSet, rowNumber) -> resultSet.getInt("members"),
                runId.value(),
                winningSeed.value());
    }

    /** The seeds whose partitions this run clustered, in occurrence order. */
    public List<OccurrenceId> partitionsFor(RunId runId) {
        return jdbcTemplate.query(
                "SELECT DISTINCT winning_seed_occurrence_id FROM document_cluster WHERE run_id = ?"
                        + " ORDER BY winning_seed_occurrence_id",
                (resultSet, rowNumber) -> new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id")),
                runId.value());
    }
}
