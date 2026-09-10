package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code embedding}'s record of what a person answered about each document (ADR-088), behind this
 * class and nothing else querying the table (ADR-041).
 *
 * <p><b>Keyed by the document's path and the seed set, never by the run or the occurrence.</b> A
 * label answers "is this document relevant to this seed set", which is true or false regardless of
 * which model scored it or when. The run, the score on screen and the embedder identity are recorded
 * beside the answer as the context it was given in.
 *
 * <p><b>The path, because an occurrence id is per-walk (ADR-097).</b> ADR-055 resumes only an
 * unfinished walk, so every ordinary invocation mints a new one and a label keyed by the occurrence
 * would join to nothing the next run scores — the answers would sit here and stop being findable,
 * which is worse than losing them because nothing reports their absence. Whoever joins these answers
 * to scores resolves the path into the walk they are reading; nothing in this class needs a walk.
 *
 * <p><b>ADR-077's fresh-row-set rule deliberately does not apply here, and this is the one table in
 * the system where that is so.</b> Everywhere else a re-run writes a new row set under a new run id,
 * because a second computation is a second observation. A second copy of a person's answer is not an
 * observation at all — it is a duplicate — so ingesting the same answer twice leaves one row, and a
 * re-score under a new model re-reads the answers already given rather than asking for them again.
 * That is the strongest argument for keeping labels out of the run scope, and the reason to resist
 * any later change that attaches a run id to their key.
 */
@Component
public class RelevanceLabels {

    private final JdbcTemplate jdbcTemplate;

    public RelevanceLabels(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Records what a person answered about the document at {@code path} against {@code seedSet}.
     *
     * <p>An answer already recorded for that pair keeps its row and takes the newer context, so
     * ingesting the same file twice is harmless and a re-score updates what the answer was seen
     * against without ever asking for the answer again.
     */
    public void record(
            OccurrencePath path,
            String seedSet,
            boolean relevant,
            RunId runId,
            double scoreShown,
            String embedderIdentity) {
        jdbcTemplate.update(
                "INSERT INTO relevance_label (path, seed_set, relevant, run_id, score_shown,"
                        + " embedder_identity) VALUES (?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT (path, seed_set) DO UPDATE SET"
                        + " relevant = excluded.relevant, run_id = excluded.run_id,"
                        + " score_shown = excluded.score_shown, embedder_identity = excluded.embedder_identity",
                path.value(),
                seedSet,
                relevant ? 1 : 0,
                runId.value(),
                scoreShown,
                embedderIdentity);
    }

    /** What a person answered about the document at {@code path} against {@code seedSet}. */
    public Optional<Boolean> answerFor(OccurrencePath path, String seedSet) {
        return jdbcTemplate
                .query(
                        "SELECT relevant FROM relevance_label WHERE path = ? AND seed_set = ?",
                        (resultSet, rowNumber) -> resultSet.getBoolean("relevant"),
                        path.value(),
                        seedSet)
                .stream()
                .findFirst();
    }

    /** Every answer given against {@code seedSet}, for the reader working out where the cut goes. */
    public List<RelevanceLabel> forSeedSet(String seedSet) {
        return jdbcTemplate.query(
                "SELECT path, seed_set, relevant, run_id, score_shown, embedder_identity"
                        + " FROM relevance_label WHERE seed_set = ? ORDER BY path",
                (resultSet, rowNumber) -> new RelevanceLabel(
                        new OccurrencePath(resultSet.getString("path")),
                        resultSet.getString("seed_set"),
                        resultSet.getBoolean("relevant"),
                        resultSet.getString("run_id"),
                        resultSet.getDouble("score_shown"),
                        resultSet.getString("embedder_identity")),
                seedSet);
    }

    /** How many answers stand against {@code seedSet} — the size of the pass, partial or complete. */
    public int countFor(String seedSet) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relevance_label WHERE seed_set = ?", Integer.class, seedSet);
        return count == null ? 0 : count;
    }
}
