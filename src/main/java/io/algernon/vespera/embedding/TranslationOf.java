package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code pipeline}'s only way into {@code embedding} for ADR-271's translation step: the likenesses among a
 * run's survivors, found from their stored chunk vectors, and the {@code translation_of} rows recorded for the
 * copies removed. Behind this class and nothing else the table is queried (ADR-041).
 *
 * <p>The mean vector of a candidate is computed from its chunk vectors as clustering computes it (ADR-087),
 * read in blocks and discarded; no table stores one.
 */
@Component
public class TranslationOf {

    private final Clustering clustering;
    private final JdbcTemplate jdbcTemplate;

    TranslationOf(Clustering clustering, JdbcTemplate jdbcTemplate) {
        this.clustering = clustering;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * A survivor entering the step: its occurrence, the content hash its vectors are stored under, and the
     * language stage 2 stored for it (Lingua's name), or {@code null} where it stored none.
     */
    public record Candidate(OccurrenceId occurrence, String contentHash, String language) {}

    /**
     * The likenesses among {@code candidates} (ADR-271 section 3), the occurrences named in the order given.
     * A candidate with no stored chunk vector under the embedder identity is no candidate and is paired with
     * nothing.
     */
    public List<Translations.Likeness> likenessesAmong(
            List<Candidate> candidates,
            String chunkerIdentity,
            String chunkingRuleIdentity,
            String embedderIdentity,
            ClusteringProgress progress) {
        StoredMeanVectors vectors =
                new StoredMeanVectors(candidates, chunkerIdentity, chunkingRuleIdentity, embedderIdentity);
        int dimension = vectors.dimension();
        if (dimension == 0) {
            return List.of();
        }
        List<Translations.Likeness> likenesses = new ArrayList<>();
        for (TranslationCandidates.Likeness found :
                TranslationCandidates.mutualNearest(vectors, Clustering.blockSizeFor(dimension), progress)) {
            likenesses.add(new Translations.Likeness(
                    candidates.get(found.first()).occurrence(),
                    candidates.get(found.second()).occurrence(),
                    found.similarity()));
        }
        return likenesses;
    }

    /** Records that {@code removal.removed()} is a translation of {@code removal.kept()} under {@code runId}. */
    public void record(RunId runId, Translations.Removal removal) {
        jdbcTemplate.update(
                "INSERT INTO translation_of (occurrence_id, run_id, translation_of_occurrence_id, language,"
                        + " kept_language, evidence) VALUES (?, ?, ?, ?, ?, ?)",
                removal.removed().value(),
                runId.value(),
                removal.kept().value(),
                removal.language(),
                removal.keptLanguage(),
                removal.evidence().stored());
    }

    /** Deletes every row recorded under {@code runId}: the step decides again (ADR-271 section 5). */
    public void discardForRun(RunId runId) {
        jdbcTemplate.update("DELETE FROM translation_of WHERE run_id = ?", runId.value());
    }

    /** The candidates' mean vectors, read from the vector cache a block at a time. */
    private final class StoredMeanVectors implements TranslationCandidates.Vectors {

        private final List<Candidate> candidates;
        private final String chunkerIdentity;
        private final String chunkingRuleIdentity;
        private final String embedderIdentity;

        StoredMeanVectors(
                List<Candidate> candidates,
                String chunkerIdentity,
                String chunkingRuleIdentity,
                String embedderIdentity) {
            this.candidates = candidates;
            this.chunkerIdentity = chunkerIdentity;
            this.chunkingRuleIdentity = chunkingRuleIdentity;
            this.embedderIdentity = embedderIdentity;
        }

        @Override
        public int count() {
            return candidates.size();
        }

        @Override
        public List<TranslationCandidates.Vector> block(int from, int size) {
            List<TranslationCandidates.Vector> block = new ArrayList<>();
            for (int i = from; i < Math.min(from + size, candidates.size()); i++) {
                float[] mean = meanOf(candidates.get(i));
                String language = candidates.get(i).language();
                block.add(new TranslationCandidates.Vector(mean.length == 0 ? null : language, mean));
            }
            return block;
        }

        /** How wide a vector is, taken from the first candidate that has one; zero where none has. */
        int dimension() {
            for (Candidate candidate : candidates) {
                int width = meanOf(candidate).length;
                if (width > 0) {
                    return width;
                }
            }
            return 0;
        }

        /** The mean of the candidate's chunk vectors, empty where none is stored. */
        private float[] meanOf(Candidate candidate) {
            return clustering.meanVectorOf(
                    candidate.contentHash(), chunkerIdentity, chunkingRuleIdentity, embedderIdentity);
        }
    }
}
