package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
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

    /** The most memberships one page of {@link #eachPage} holds. */
    private static final int MEMBERS_IN_A_PAGE = 1_000;

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

    /**
     * Deletes every membership row recorded under {@code runId} — the discard half of
     * ADR-115/ADR-116, for a step whose completion under this run is not recorded.
     */
    public void discardForRun(RunId runId) {
        jdbcTemplate.update("DELETE FROM document_cluster WHERE run_id = ?", runId.value());
    }

    /**
     * How many survivors {@code winningSeed}'s partition holds under {@code runId}: a count the database makes,
     * reading no column of any row (ADR-223 section 3). A seed with none has no partition.
     */
    public int sizeOf(RunId runId, OccurrenceId winningSeed) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_cluster WHERE run_id = ? AND winning_seed_occurrence_id = ?",
                Integer.class,
                runId.value(),
                winningSeed.value());
        return count == null ? 0 : count;
    }

    /**
     * The membership of {@code winningSeed}'s partition under {@code runId}, in occurrence order: read unordered
     * and put in order on the heap, so that nothing is sorted by the database (ADR-223 section 3). The order
     * matters to a caller: a cluster's average closeness is a sum taken in it, and the document a cluster is
     * named after is the first of equal scores.
     */
    public List<DocumentCluster> membersOf(RunId runId, OccurrenceId winningSeed) {
        List<DocumentCluster> members = new ArrayList<>(jdbcTemplate.query(
                "SELECT occurrence_id, cluster_ordinal FROM document_cluster"
                        + " WHERE run_id = ? AND winning_seed_occurrence_id = ?",
                (resultSet, rowNumber) -> new DocumentCluster(
                        new OccurrenceId(resultSet.getLong("occurrence_id")),
                        winningSeed,
                        resultSet.getInt("cluster_ordinal")),
                runId.value(),
                winningSeed.value()));
        members.sort(Comparator.comparingLong((DocumentCluster member) -> member.occurrenceId().value()));
        return members;
    }

    /**
     * Hands every membership recorded under {@code runId} to {@code page}, a page of up to {@value
     * #MEMBERS_IN_A_PAGE} at a time in occurrence order, each once its statement has finished (ADR-223
     * section 7). Each page begins after the last occurrence of the one before, and is planned by the primary
     * key with the run compared as a value, so that the index on the run is not chosen and nothing is sorted.
     */
    public void eachPage(RunId runId, Consumer<List<DocumentCluster>> page) {
        long after = Long.MIN_VALUE;
        while (true) {
            List<DocumentCluster> members = jdbcTemplate.query(
                    "SELECT occurrence_id, winning_seed_occurrence_id, cluster_ordinal FROM document_cluster"
                            + " WHERE occurrence_id > ? AND +run_id = ? ORDER BY occurrence_id LIMIT "
                            + MEMBERS_IN_A_PAGE,
                    (resultSet, rowNumber) -> new DocumentCluster(
                            new OccurrenceId(resultSet.getLong("occurrence_id")),
                            new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id")),
                            resultSet.getInt("cluster_ordinal")),
                    after,
                    runId.value());
            if (!members.isEmpty()) {
                page.accept(members);
            }
            if (members.size() < MEMBERS_IN_A_PAGE) {
                return;
            }
            after = members.getLast().occurrenceId().value();
        }
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
}
