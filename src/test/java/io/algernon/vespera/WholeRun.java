package io.algernon.vespera;

import io.algernon.vespera.engine.embedding.DocumentCluster;
import io.algernon.vespera.engine.embedding.SeedPassage;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.synthesis.ArrangedCluster;
import io.algernon.vespera.engine.synthesis.ClusterFault;
import io.algernon.vespera.engine.synthesis.ClusterFaultKind;
import io.algernon.vespera.engine.synthesis.ClusterLabel;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.ListedDoc;
import io.algernon.vespera.engine.synthesis.ListedFault;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import io.algernon.vespera.engine.synthesis.TopicKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Every row one run left in a table of stages 5f, 6a and 6b, read whole so that a test can make a claim
 * about all of them.
 *
 * <p>No class of {@code src/main} reads these tables a run at a time since ADR-223: stage 6a and stage 6b go
 * through one topic at a time and ask for a cluster's rows by its key (ADR-409 section 13). A test's database holds
 * a handful of rows, so a test may read them all, and it reads them here, in the order the reads {@code src/main}
 * had gave them, without leaving a read in {@code src/main} for tests alone to call (ADR-216).
 *
 * <p>A row's subtopic of {@code -1} is a topic with no subtopics, and is read as an empty {@link OptionalInt}
 * (ADR-409 section 3).
 */
public final class WholeRun {

    /** How a row writes that its topic has no subtopics (ADR-409 section 3). */
    private static final int NO_SUBTOPIC = -1;

    private WholeRun() {}

    /** Every membership row recorded under a scoring run, in occurrence order. */
    public static List<DocumentCluster> membership(JdbcTemplate jdbcTemplate, RunId scoring) {
        return jdbcTemplate.query(
                "SELECT occurrence_id, topic_seed_path, topic_chunk_index, subtopic_ordinal, cluster_ordinal"
                        + " FROM cluster_member WHERE run_id = ? ORDER BY occurrence_id",
                (resultSet, rowNumber) -> new DocumentCluster(
                        new OccurrenceId(resultSet.getLong("occurrence_id")),
                        new io.algernon.vespera.engine.embedding.TopicKey(new SeedPassage(
                                resultSet.getString("topic_seed_path"), resultSet.getInt("topic_chunk_index"))),
                        subtopicOf(resultSet),
                        resultSet.getInt("cluster_ordinal")),
                scoring.value());
    }

    /** Every cluster recorded under an arrangement, in the order the arrangement gives them. */
    public static List<RecordedCluster> clusters(JdbcTemplate jdbcTemplate, RunId arrangement) {
        return jdbcTemplate.query(
                "SELECT topic_seed_path, topic_chunk_index, subtopic_ordinal, cluster_ordinal, label, document_count,"
                        + " topic_order, subtopic_order, cluster_order FROM arranged_cluster WHERE run_id = ?"
                        + " ORDER BY topic_order, subtopic_order, cluster_order",
                (resultSet, rowNumber) -> new RecordedCluster(
                        new ArrangedCluster(
                                topicOf(resultSet),
                                subtopicOf(resultSet),
                                resultSet.getInt("cluster_ordinal"),
                                resultSet.getInt("document_count"),
                                resultSet.getInt("topic_order"),
                                resultSet.getInt("subtopic_order"),
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
                "SELECT topic_seed_path, topic_chunk_index, subtopic_ordinal, cluster_ordinal, title, prose"
                        + " FROM cluster_synthesis_doc WHERE run_id = ? ORDER BY rowid",
                (resultSet, rowNumber) -> new ListedDoc(
                        slotOf(resultSet),
                        new SynthesisDoc(resultSet.getString("title"), resultSet.getString("prose"), List.of())),
                generation.value());
        List<ListedDoc> docs = new ArrayList<>();
        for (ListedDoc doc : withoutWhatWasSent) {
            ClusterSlot cluster = doc.cluster();
            List<OccurrenceId> sent = jdbcTemplate.query(
                    "SELECT occurrence_id FROM cluster_call_exemplar WHERE run_id = ? AND topic_seed_path = ?"
                            + " AND topic_chunk_index = ? AND subtopic_ordinal = ? AND cluster_ordinal = ?"
                            + " ORDER BY citation_ordinal",
                    (exemplar, rowNumber) -> new OccurrenceId(exemplar.getLong("occurrence_id")),
                    generation.value(),
                    cluster.topic().seedPath(),
                    cluster.topic().chunkIndex(),
                    cluster.subtopic().orElse(NO_SUBTOPIC),
                    cluster.clusterOrdinal());
            docs.add(new ListedDoc(cluster, new SynthesisDoc(doc.doc().title(), doc.doc().prose(), sent)));
        }
        return docs;
    }

    /** Every cluster fault recorded under a generation run, in the order the clusters were attempted. */
    public static List<ListedFault> clusterFaults(JdbcTemplate jdbcTemplate, RunId generation) {
        return jdbcTemplate.query(
                "SELECT topic_seed_path, topic_chunk_index, subtopic_ordinal, cluster_ordinal, kind, detail"
                        + " FROM standing_cluster_fault WHERE run_id = ? ORDER BY rowid",
                (resultSet, rowNumber) -> new ListedFault(
                        slotOf(resultSet),
                        new ClusterFault(
                                ClusterFaultKind.valueOf(resultSet.getString("kind")), resultSet.getString("detail"))),
                generation.value());
    }

    private static ClusterSlot slotOf(ResultSet resultSet) throws SQLException {
        return new ClusterSlot(topicOf(resultSet), subtopicOf(resultSet), resultSet.getInt("cluster_ordinal"));
    }

    private static TopicKey topicOf(ResultSet resultSet) throws SQLException {
        return new TopicKey(resultSet.getString("topic_seed_path"), resultSet.getInt("topic_chunk_index"));
    }

    private static OptionalInt subtopicOf(ResultSet resultSet) throws SQLException {
        int subtopic = resultSet.getInt("subtopic_ordinal");
        return subtopic == NO_SUBTOPIC ? OptionalInt.empty() : OptionalInt.of(subtopic);
    }
}
