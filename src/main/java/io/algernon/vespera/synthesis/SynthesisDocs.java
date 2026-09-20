package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stage 6b's record of what each call produced (ADR-108, ADR-110, ADR-133), behind this class and
 * nothing else querying either of its two tables (ADR-041).
 *
 * <p><b>Two tables, written and read as one fact.</b> {@code synthesis_doc} carries what came back
 * and {@code call_exemplar} carries which documents went out under which number, because the
 * correspondence between a citation and a document cannot be re-derived anywhere downstream
 * (ADR-133). Nothing outside this class ever sees them apart: a {@link SynthesisDoc} carries both.
 *
 * <p><b>The answer is kept rather than written straight out.</b> Generating is the most expensive
 * call this system makes, and what a reader opens is a rendering of this row rather than a copy of it:
 * each {@code [n]} becomes a link and the list of documents is composed at write time (ADR-109). A
 * stored answer is also what lets an unchanged re-run rebuild the same tree without asking the model
 * again — whether it should is deliberately still open (ADR-108).
 *
 * <p><b>A cluster with a row here succeeded, and a cluster without one is the hole.</b> That is the
 * whole shape of a cluster fault: nothing is written to say a cluster failed, because the absence
 * already says it, and the deliverable heads the hole with the label 6a derived (ADR-106, ADR-111).
 *
 * <p>Rows are keyed by the 6b run, so a second generation writes its own row set beside the first
 * rather than over it (ADR-077) — the rule {@link Clusters} already follows one stage back.
 *
 * <p><b>Nothing here writes a verdict.</b> Writing connecting text over survivors removes no document
 * from anything, so no blocking kind applies, and generation's one way of failing is a fault against a
 * cluster rather than a judgement against a document (ADR-111).
 */
@Component
public class SynthesisDocs {

    private final JdbcTemplate jdbcTemplate;

    public SynthesisDocs(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Records what one call produced for one cluster, under {@code runId}, which is the 6b run.
     *
     * <p><b>The documents it was written from land first, and anything standing under that key is
     * cleared before they do</b> (ADR-133). The {@code synthesis_doc} row is what tells a later
     * invocation the cluster is already written (ADR-111, ADR-115), so it has to be the last thing
     * to appear: an invocation that died between the two statements would otherwise leave exemplar
     * rows nothing points at, and the re-attempt that follows would collide with them. In this order
     * a half-written cluster is one nothing was written over, which is the state the next invocation
     * already knows how to finish.
     */
    public void record(RunId runId, OccurrenceId winningSeed, int clusterOrdinal, SynthesisDoc doc) {
        jdbcTemplate.update(
                "DELETE FROM call_exemplar WHERE run_id = ? AND winning_seed_occurrence_id = ? AND"
                        + " cluster_ordinal = ?",
                runId.value(),
                winningSeed.value(),
                clusterOrdinal);
        int citationOrdinal = 0;
        for (OccurrenceId sent : doc.sent()) {
            citationOrdinal++;
            jdbcTemplate.update(
                    "INSERT INTO call_exemplar (run_id, winning_seed_occurrence_id, cluster_ordinal,"
                            + " citation_ordinal, occurrence_id) VALUES (?, ?, ?, ?, ?)",
                    runId.value(),
                    winningSeed.value(),
                    clusterOrdinal,
                    citationOrdinal,
                    sent.value());
        }
        jdbcTemplate.update(
                "INSERT INTO synthesis_doc (run_id, winning_seed_occurrence_id, cluster_ordinal, title,"
                        + " prose) VALUES (?, ?, ?, ?, ?)",
                runId.value(),
                winningSeed.value(),
                clusterOrdinal,
                doc.title(),
                doc.prose());
    }

    /** Every synthesis doc recorded under {@code runId}, in the order the clusters were written. */
    public List<RecordedSynthesisDoc> forRun(RunId runId) {
        Map<ClusterKey, List<OccurrenceId>> sent = whatEachCallSent(runId);
        return jdbcTemplate.query(
                "SELECT winning_seed_occurrence_id, cluster_ordinal, title, prose"
                        + " FROM synthesis_doc WHERE run_id = ? ORDER BY rowid",
                (resultSet, rowNumber) -> {
                    ClusterKey key = new ClusterKey(
                            new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id")),
                            resultSet.getInt("cluster_ordinal"));
                    return new RecordedSynthesisDoc(
                            key.winningSeed(),
                            key.clusterOrdinal(),
                            new SynthesisDoc(
                                    resultSet.getString("title"),
                                    resultSet.getString("prose"),
                                    sent.getOrDefault(key, List.of())));
                },
                runId.value());
    }

    /**
     * Which documents each call under {@code runId} carried, each in the order its own call was given
     * them (ADR-133).
     *
     * <p>Read in one query rather than one per cluster: a run holds a call per cluster, and this is
     * read where the terminal stage writes the whole tree in one pass.
     */
    private Map<ClusterKey, List<OccurrenceId>> whatEachCallSent(RunId runId) {
        Map<ClusterKey, List<OccurrenceId>> sent = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT winning_seed_occurrence_id, cluster_ordinal, occurrence_id FROM call_exemplar"
                        + " WHERE run_id = ? ORDER BY winning_seed_occurrence_id, cluster_ordinal,"
                        + " citation_ordinal",
                resultSet -> {
                    ClusterKey key = new ClusterKey(
                            new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id")),
                            resultSet.getInt("cluster_ordinal"));
                    sent.computeIfAbsent(key, cluster -> new ArrayList<>())
                            .add(new OccurrenceId(resultSet.getLong("occurrence_id")));
                },
                runId.value());
        return sent;
    }

    /** One cluster of one run, as both tables here key it (ADR-110). */
    private record ClusterKey(OccurrenceId winningSeed, int clusterOrdinal) {}
}
