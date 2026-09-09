package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@code pipeline}'s only way into {@code embedding} for ADR-020's scoring (#108), the same shape
 * {@link ChunkEmbedder} already gives it for gate 3's own step.
 *
 * <p><b>The seed side is resident, the corpus side streams (ADR-085).</b> {@link #residentSeedVectors}
 * is called once per invocation and its whole return value is held for the rest of the pass — 16 KiB
 * per seed chunk, the bound ADR-085 measured. {@link #scoreAndRecord} is called once per survivor and
 * reads only that survivor's own chunk vectors before scoring and discarding them, so nothing here
 * ever holds two survivors' vectors at once, regardless of corpus size.
 */
@Component
public class RelevanceScoring {

    private final VectorCache vectorCache;
    private final RelevanceScorer scorer;
    private final RelevanceScoreCache scoreCache;

    RelevanceScoring(VectorCache vectorCache, RelevanceScorer scorer, RelevanceScoreCache scoreCache) {
        this.vectorCache = vectorCache;
        this.scorer = scorer;
        this.scoreCache = scoreCache;
    }

    /**
     * Loads every usable seed document's chunk vectors, keyed by seed occurrence — the resident side
     * of ADR-085's shape, built once and held for the whole scoring pass. A seed occurrence whose
     * content hash matches no stored vector is left out rather than recorded with an empty list, so a
     * caller never has to tell "seed with no chunks" apart from "seed with none scored yet" by size.
     */
    public Map<OccurrenceId, List<float[]>> residentSeedVectors(
            Map<OccurrenceId, String> seedContentHashesByOccurrence,
            String chunkerIdentity,
            String chunkingRuleIdentity,
            String modelName) {
        Map<OccurrenceId, List<float[]>> resident = new LinkedHashMap<>();
        for (Map.Entry<OccurrenceId, String> seed : seedContentHashesByOccurrence.entrySet()) {
            List<float[]> vectors =
                    vectorCache.vectorsFor(seed.getValue(), chunkerIdentity, chunkingRuleIdentity, modelName);
            if (!vectors.isEmpty()) {
                resident.put(seed.getKey(), vectors);
            }
        }
        return resident;
    }

    /**
     * The survivors {@code runId} scored below {@code floor} — the documents a set, applicable
     * relevance threshold removes (ADR-088, #112).
     *
     * <p>Hands back occurrences and writes nothing. A verdict belongs to {@code ledger} and is
     * written by the step that composes this one, so the module that holds the scores never also
     * holds the power to remove a document by them (ADR-041, ADR-060).
     */
    public List<OccurrenceId> scoredBelow(RunId runId, double floor) {
        return scoreCache.scoredBelow(runId, floor);
    }

    /**
     * Scores one corpus survivor against {@code residentSeedVectors} and stores the result under
     * {@code runId} — never materialising more than this one survivor's own chunk vectors alongside
     * the seed side already held resident (ADR-085).
     *
     * <p>Throws rather than scoring zero if {@code occurrenceId} has no stored vectors: a corpus
     * survivor reaching stage 5 with no chunks is a case #108 confirmed cannot happen by construction
     * (stage 2 removes documents with no text before a survivor is ever chunked), so a row here would
     * misreport an assumption as a measurement (ADR-020, "Confirm, do not assume").
     */
    public void scoreAndRecord(
            OccurrenceId occurrenceId,
            RunId runId,
            String contentHash,
            String chunkerIdentity,
            String chunkingRuleIdentity,
            String modelName,
            Map<OccurrenceId, List<float[]>> residentSeedVectors) {
        List<float[]> survivorChunkVectors =
                vectorCache.vectorsFor(contentHash, chunkerIdentity, chunkingRuleIdentity, modelName);
        if (survivorChunkVectors.isEmpty()) {
            throw new IllegalStateException(
                    "occurrence " + occurrenceId.value() + " has no stored chunk vectors to score; a"
                            + " corpus survivor with no chunks was confirmed impossible by construction"
                            + " (#108) -- if this is reached, stage 2's no-text floor let one through");
        }
        RelevanceScore score = scorer.score(survivorChunkVectors, residentSeedVectors);
        scoreCache.record(occurrenceId, runId, score);
    }
}
