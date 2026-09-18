package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Stage 6b's record of a group whose answer it turned down (ADR-108, ADR-109, ADR-111), behind this
 * class and nothing else querying the table (ADR-041).
 *
 * <p><b>The precedent is {@code walk_anomaly} and {@code unusable_seed}, not the ledger.</b> A
 * verdict removes a file occurrence from what gets published; a cluster fault removes nothing — only
 * what was said about a group's documents was rejected, not the documents. So this is a fact about
 * content, never a verdict (ADR-111).
 *
 * <p><b>A group never carries a row here and in {@link SynthesisDocs} under the same run</b>, in any
 * state anything can read. What keeps that true across invocations is {@link #delete} landing in the
 * same step transaction as the write it follows (ADR-111, #185): a group turned down once is asked
 * again under the same run id, and the write and the delete commit together or not at all.
 *
 * <p>Rows are keyed by the 6b run, so a second generation writes beside the first rather than over
 * it (ADR-077), the rule {@link SynthesisDocs} already follows.
 *
 * <p><b>Not {@code @Component}.</b> {@code GenerationTasklet} constructs its own instance from an
 * ambient {@code JdbcTemplate} rather than have Spring inject one (ADR-041 holds either way: only
 * this class touches {@code cluster_fault}, and only through here) — a bean nothing in {@code
 * src/main} would inject would just sit in the context unused. A test that needs one imports this
 * class and gets it the ordinary way {@code @Import} already provides for a plain class.
 */
public class ClusterFaults {

    private final JdbcTemplate jdbcTemplate;

    public ClusterFaults(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Records why one group's answer was turned down, under {@code runId}, which is the 6b run.
     *
     * <p><b>Replaces rather than inserts, which is what makes a second invocation possible.</b> A run
     * that turned an answer down records no completion, so the next invocation re-derives the same
     * run id and asks again — a plain insert would meet its own row and end the run on a key
     * collision.
     *
     * <p><b>Replaces rather than ignores</b>, because the second attempt may fail a different check,
     * and what is kept must be true of the attempt that is standing — unlike the neighbouring
     * recording of a finished step, which ignores a repeat because one row already says everything a
     * second would.
     */
    public void record(RunId runId, OccurrenceId winningSeed, int clusterOrdinal, ClusterFault fault) {
        jdbcTemplate.update(
                "INSERT OR REPLACE INTO cluster_fault (run_id, winning_seed_occurrence_id,"
                        + " cluster_ordinal, kind, detail) VALUES (?, ?, ?, ?, ?)",
                runId.value(),
                winningSeed.value(),
                clusterOrdinal,
                fault.kind().name(),
                fault.detail());
    }

    /**
     * Deletes the fault standing against one cluster, because a re-attempt under the same run
     * succeeded (ADR-111, #185).
     *
     * <p><b>The one row in this schema removed on success.</b> Every other row this module writes — a
     * {@code synthesis_doc}, a fault recorded above — stands until the table itself is gone. A fault
     * is rewritten in place when a later attempt fails a different check, which is what {@link
     * #record}'s replace is for; what happens nowhere else here is a row being taken away because
     * something went right. This delete exists for exactly one reason and does not generalise: the
     * ledger must never say a cluster both failed and succeeded under one run, so once a {@code
     * synthesis_doc} row is written for a cluster that once faulted, the fault row saying otherwise has
     * to go with it. Read this as the narrow carve-out ADR-111 names, not as licence to delete anything
     * else in this module.
     *
     * <p>A no-op when no fault stands against the cluster — the ordinary case, since most clusters
     * never fault at all.
     */
    public void delete(RunId runId, OccurrenceId winningSeed, int clusterOrdinal) {
        jdbcTemplate.update(
                "DELETE FROM cluster_fault WHERE run_id = ? AND winning_seed_occurrence_id = ? AND"
                        + " cluster_ordinal = ?",
                runId.value(),
                winningSeed.value(),
                clusterOrdinal);
    }

    /** Every cluster fault recorded under {@code runId}, in the order the groups were attempted. */
    public List<RecordedClusterFault> forRun(RunId runId) {
        return jdbcTemplate.query(
                "SELECT winning_seed_occurrence_id, cluster_ordinal, kind, detail FROM cluster_fault"
                        + " WHERE run_id = ? ORDER BY rowid",
                (resultSet, rowNumber) -> new RecordedClusterFault(
                        new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id")),
                        resultSet.getInt("cluster_ordinal"),
                        new ClusterFault(
                                ClusterFaultKind.valueOf(resultSet.getString("kind")),
                                resultSet.getString("detail"))),
                runId.value());
    }
}
