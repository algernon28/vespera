package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stage 6b's record of what each call produced (ADR-108, ADR-110), behind this class and nothing else
 * querying the table (ADR-041).
 *
 * <p><b>The answer is kept rather than written straight out.</b> Generating is the most expensive
 * call this system makes, and what a reader opens is a rendering of this row rather than a copy of it:
 * each {@code [n]} becomes a link and the list of documents is composed at write time (ADR-109). A
 * stored answer is also what lets an unchanged re-run rebuild the same tree without asking the model
 * again — whether it should is deliberately still open (ADR-108).
 *
 * <p><b>A group with a row here succeeded, and a group without one is the hole.</b> That is the whole
 * shape of a cluster fault: nothing is written to say a group failed, because the absence already says
 * it, and the deliverable heads the hole with the label 6a derived (ADR-106, ADR-111).
 *
 * <p>Rows are keyed by the 6b run, so a second generation writes its own row set beside the first
 * rather than over it (ADR-077) — the rule {@link Clusters} already follows one stage back.
 *
 * <p><b>Nothing here writes a verdict.</b> Writing connecting text over survivors removes no document
 * from anything, so no blocking kind applies, and generation's one way of failing is a fault against a
 * group rather than a judgement against a document (ADR-111).
 */
@Component
public class SynthesisDocs {

    private final JdbcTemplate jdbcTemplate;

    public SynthesisDocs(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Records what one call produced for one group, under {@code runId}, which is the 6b run. */
    public void record(RunId runId, OccurrenceId winningSeed, int clusterOrdinal, SynthesisDoc doc) {
        jdbcTemplate.update(
                "INSERT INTO synthesis_doc (run_id, winning_seed_occurrence_id, cluster_ordinal, title,"
                        + " prose, documents_sent) VALUES (?, ?, ?, ?, ?, ?)",
                runId.value(),
                winningSeed.value(),
                clusterOrdinal,
                doc.title(),
                doc.prose(),
                doc.documentsSent());
    }

    /** Every synthesis doc recorded under {@code runId}, in the order the groups were written. */
    public List<RecordedSynthesisDoc> forRun(RunId runId) {
        return jdbcTemplate.query(
                "SELECT winning_seed_occurrence_id, cluster_ordinal, title, prose, documents_sent"
                        + " FROM synthesis_doc WHERE run_id = ? ORDER BY rowid",
                (resultSet, rowNumber) -> new RecordedSynthesisDoc(
                        new OccurrenceId(resultSet.getLong("winning_seed_occurrence_id")),
                        resultSet.getInt("cluster_ordinal"),
                        new SynthesisDoc(
                                resultSet.getString("title"),
                                resultSet.getString("prose"),
                                resultSet.getInt("documents_sent"))),
                runId.value());
    }
}
