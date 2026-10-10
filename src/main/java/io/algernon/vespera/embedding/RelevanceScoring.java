package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
     * of ADR-085's shape, built once and held for the whole scoring pass. A seed with no chunk is left out
     * (ADR-231 section 2a); a seed with a chunk and no stored vector under the embedder identity is refused
     * (section 2): scoring without it would measure every survivor against fewer seeds.
     *
     * @throws IllegalStateException on the first seed with a chunk and no stored vector under {@code embedderIdentity}
     */
    public Map<OccurrenceId, List<float[]>> residentSeedVectors(
            Map<OccurrenceId, SeedChunks> usableSeedsByOccurrence,
            String chunkerIdentity,
            String chunkingRuleIdentity,
            String embedderIdentity) {
        return residentSeedVectors(
                usableSeedsByOccurrence,
                chunkerIdentity,
                chunkingRuleIdentity,
                embedderIdentity,
                ScoringProgress.NONE);
    }

    /**
     * As {@link #residentSeedVectors(Map, String, String, String)}, telling {@code progress} how many seeds
     * will be read, once, before the first, and each seed as it is gone through (ADR-192 section 5). A seed with no chunk
     * is told to {@code progress} as left out and counted; a seed with a chunk and no vector stops the read
     * before it is counted (ADR-231 section 2).
     */
    public Map<OccurrenceId, List<float[]>> residentSeedVectors(
            Map<OccurrenceId, SeedChunks> usableSeedsByOccurrence,
            String chunkerIdentity,
            String chunkingRuleIdentity,
            String embedderIdentity,
            ScoringProgress progress) {
        Map<OccurrenceId, List<float[]>> resident = new LinkedHashMap<>();
        progress.toReadSeedVectors(usableSeedsByOccurrence.size());
        for (Map.Entry<OccurrenceId, SeedChunks> seed : usableSeedsByOccurrence.entrySet()) {
            if (seed.getValue().chunkCount() == 0) {
                progress.seedLeftOutWithNoChunk(seed.getKey());
                progress.seedVectorsRead();
                continue;
            }
            List<float[]> vectors = vectorCache.vectorsFor(
                    seed.getValue().contentHash(), chunkerIdentity, chunkingRuleIdentity, embedderIdentity);
            if (vectors.isEmpty()) {
                throw new IllegalStateException("seed occurrence " + seed.getKey().value()
                        + " has no stored chunk vectors under embedder identity " + embedderIdentity
                        + "; a usable seed is embedded by the step before this one, so either that step did not"
                        + " embed it or another pull of the embedding model wrote its vectors -- scoring without"
                        + " it would measure every survivor against fewer seeds than the seed set holds");
            }
            resident.put(seed.getKey(), vectors);
            progress.seedVectorsRead();
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
    /**
     * The relevance score each of {@code occurrences} carries under {@code runId}, for a caller that
     * needs the numbers themselves rather than a cut through them.
     *
     * <p>Handed out rather than read where it is used, because the terminal stages may not name this
     * module (ADR-110): the score decides which document leads a cluster (ADR-106), how clusters
     * within a partition compare (ADR-112) and the order exemplars are sent in (ADR-108), and all
     * three happen where the passes are assembled.
     *
     * <p>An occurrence with no score row is absent from the result rather than present with a zero. A
     * zero would be a score, and "not measured" is not one — the same distinction ADR-070 draws for a
     * null metric.
     */
    public Map<OccurrenceId, Double> scoresFor(RunId runId, Collection<OccurrenceId> occurrences) {
        return scoresFor(runId, occurrences, ScoringProgress.NONE);
    }

    /**
     * As {@link #scoresFor(RunId, Collection)}, telling {@code progress} how many occurrences will be read,
     * once, before the first, and each occurrence as it is read, scored or not (ADR-192 section 5).
     */
    public Map<OccurrenceId, Double> scoresFor(
            RunId runId, Collection<OccurrenceId> occurrences, ScoringProgress progress) {
        Map<OccurrenceId, Double> scores = new LinkedHashMap<>();
        progress.toReadScores(occurrences.size());
        for (OccurrenceId occurrence : occurrences) {
            scoreCache.forOccurrence(occurrence, runId).ifPresent(score -> scores.put(occurrence, score.score()));
            progress.scoreRead();
        }
        return scores;
    }

    /**
     * The seeds that won at least one survivor under {@code runId}, in occurrence order: one seed partition
     * each (ADR-045), the read stage 5f and stage 6a both start from (ADR-223 section 3).
     */
    public List<OccurrenceId> winningSeeds(RunId runId) {
        return scoreCache.winningSeeds(runId);
    }

    /** How many occurrences {@code runId} scored strictly below {@code floor} (ADR-220 section 5). */
    public long countScoredBelow(RunId runId, double floor) {
        return scoreCache.countScoredBelow(runId, floor);
    }

    /**
     * Hands the occurrences {@code runId} scored strictly below {@code floor} to {@code page}, a page of up
     * to 1,000 at a time in the order the scores were written, each once its statement has finished
     * (ADR-220 section 5).
     */
    public void eachPageScoredBelow(RunId runId, double floor, Consumer<List<OccurrenceId>> page) {
        scoreCache.eachPageScoredBelow(runId, floor, page);
    }

    /**
     * Deletes every score recorded under {@code runId} — the discard half of ADR-115/ADR-116, for a
     * step whose completion under this run is not recorded.
     */
    public void discardForRun(RunId runId) {
        scoreCache.discardForRun(runId);
    }

    /**
     * Scores one corpus survivor against {@code residentSeedVectors} and stores the result under
     * {@code runId} — never materialising more than this one survivor's own chunk vectors alongside
     * the seed side already held resident (ADR-085).
     *
     * <p>Throws rather than scoring zero if {@code occurrenceId} has no stored vectors under {@code
     * embedderIdentity}: a zero would record a document nothing was measured of as one measured
     * irrelevant (ADR-020, "Confirm, do not assume"). Three ways into it are known: an occurrence no stage
     * ever examined; vectors another pull of the embedding model wrote (ADR-231); and a survivor whose only
     * text is in page headers and footers, which stage 2's no-text floor counts and the chunker leaves out,
     * so that it has no chunk. The last is not handled: it stops scoring on every invocation, and the
     * message, which still calls a survivor with no chunks impossible by construction, does not name it.
     */
    public void scoreAndRecord(
            OccurrenceId occurrenceId,
            RunId runId,
            String contentHash,
            String chunkerIdentity,
            String chunkingRuleIdentity,
            String embedderIdentity,
            Map<OccurrenceId, List<float[]>> residentSeedVectors) {
        List<float[]> survivorChunkVectors =
                vectorCache.vectorsFor(contentHash, chunkerIdentity, chunkingRuleIdentity, embedderIdentity);
        if (survivorChunkVectors.isEmpty()) {
            throw new IllegalStateException(
                    "occurrence " + occurrenceId.value() + " has no stored chunk vectors to score; a"
                            + " corpus survivor with no chunks was confirmed impossible by construction"
                            + " (#108) -- if this is reached, either no stage ever examined this occurrence,"
                            + " or another pull of the embedding model wrote its vectors");
        }
        RelevanceScore score = scorer.score(survivorChunkVectors, residentSeedVectors);
        scoreCache.record(occurrenceId, runId, score);
    }
}
