package io.algernon.vespera;

import io.algernon.vespera.embedding.DocumentCluster;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.synthesis.ArrangedCluster;
import io.algernon.vespera.synthesis.ClusterFault;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import io.algernon.vespera.synthesis.ClusterLabel;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.ListedFault;
import io.algernon.vespera.synthesis.ListedDoc;
import io.algernon.vespera.synthesis.SynthesisDoc;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Every row one run left in a table of stages 5f, 6a and 6b, read whole so that a test can make a claim
 * about all of them.
 *
 * <p>No class of {@code src/main} reads these tables a run at a time since ADR-223: stage 6a and stage 6b go
 * through one seed partition at a time and ask for a cluster's rows by its key. A test's database holds a
 * handful of rows, so a test may read them all, and it reads them here, in the order the reads {@code
 * src/main} had gave them, without leaving a read in {@code src/main} for tests alone to call (ADR-216).
 */
public final class WholeRun {

    private WholeRun() {}

    /** Every membership row recorded under a scoring run, in occurrence order. */
    public static List<DocumentCluster> membership(JdbcTemplate jdbcTemplate, RunId scoring) {
        return jdbcTemplate.query(
                "SELECT occurrence_id, winning_seed_occurrence_id, cluster_ordinal FROM document_cluster"
                        + " WHERE run_id = ? ORDER BY occurrence_id",
                (resultSet, rowNumber) -> new DocumentCluster(
                        new OccurrenceId(resultSet.getLong("occurrence_id")),
                        new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id")),
                        resultSet.getInt("cluster_ordinal")),
                scoring.value());
    }

    /** Every cluster recorded under an arrangement, in the order the arrangement gives them. */
    public static List<RecordedCluster> clusters(JdbcTemplate jdbcTemplate, RunId arrangement) {
        return jdbcTemplate.query(
                "SELECT winning_seed_occurrence_id, cluster_ordinal, label, document_count, partition_order,"
                        + " cluster_order FROM cluster WHERE run_id = ? ORDER BY partition_order, cluster_order",
                (resultSet, rowNumber) -> new RecordedCluster(
                        new ArrangedCluster(
                                new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id")),
                                resultSet.getInt("cluster_ordinal"),
                                resultSet.getInt("document_count"),
                                resultSet.getInt("partition_order"),
                                resultSet.getInt("cluster_order")),
                        new ClusterLabel(resultSet.getString("label"))),
                arrangement.value());
    }

    /**
     * Every synthesis doc recorded under a generation run, in the order the clusters were written, each with
     * the documents its call carried in citation order.
     */
    public static List<ListedDoc> synthesisDocs(JdbcTemplate jdbcTemplate, RunId generation) {
        // The docs first and what each call sent afterwards, one statement open at a time: the test profile's
        // pool holds one connection, and a read begun inside another's rows would wait for it for ever.
        List<ListedDoc> withoutWhatWasSent = jdbcTemplate.query(
                "SELECT winning_seed_occurrence_id, cluster_ordinal, title, prose FROM synthesis_doc"
                        + " WHERE run_id = ? ORDER BY rowid",
                (resultSet, rowNumber) -> new ListedDoc(
                        new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id")),
                        resultSet.getInt("cluster_ordinal"),
                        new SynthesisDoc(resultSet.getString("title"), resultSet.getString("prose"), List.of())),
                generation.value());
        List<ListedDoc> docs = new ArrayList<>();
        for (ListedDoc doc : withoutWhatWasSent) {
            List<OccurrenceId> sent = jdbcTemplate.query(
                    "SELECT occurrence_id FROM call_exemplar WHERE run_id = ? AND winning_seed_occurrence_id = ?"
                            + " AND cluster_ordinal = ? ORDER BY citation_ordinal",
                    (exemplar, rowNumber) -> new OccurrenceId(exemplar.getLong("occurrence_id")),
                    generation.value(),
                    doc.winningSeed().value(),
                    doc.clusterOrdinal());
            docs.add(new ListedDoc(
                    doc.winningSeed(), doc.clusterOrdinal(), new SynthesisDoc(doc.doc().title(), doc.doc().prose(), sent)));
        }
        return docs;
    }

    /** Every cluster fault recorded under a generation run, in the order the clusters were attempted. */
    public static List<ListedFault> clusterFaults(JdbcTemplate jdbcTemplate, RunId generation) {
        return jdbcTemplate.query(
                "SELECT winning_seed_occurrence_id, cluster_ordinal, kind, detail FROM cluster_fault"
                        + " WHERE run_id = ? ORDER BY rowid",
                (resultSet, rowNumber) -> new ListedFault(
                        new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id")),
                        resultSet.getInt("cluster_ordinal"),
                        new ClusterFault(
                                ClusterFaultKind.valueOf(resultSet.getString("kind")), resultSet.getString("detail"))),
                generation.value());
    }
}
