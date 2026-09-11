package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@code pipeline}'s only way into {@code embedding} for ADR-087's clustering (#109), the same shape
 * {@link RelevanceScoring} already gives it for ADR-020's scoring.
 *
 * <p><b>One partition at a time, and within a partition, two blocks of vectors at a time.</b> A seed
 * partition is what ADR-045 bounds the work to; the blocks are what make the ceiling independent of
 * how large that partition turns out to be. Nothing here ever holds a partition's vectors whole, and
 * the mean vectors it compares are computed and discarded (ADR-087) — no table stores one, and a
 * stored copy would be a cache nothing reads that has to be invalidated whenever the chunking or the
 * model changes.
 *
 * <p><b>What a partition is, is what scoring already recorded.</b> ADR-087 clusters a partition's
 * <em>survivors</em>, which is one rule covering both states of a threshold that ships unset: a
 * document with a blocking verdict is never scored, so it has no score row, so it is not in any
 * partition here and costs nothing. Once the relevance floor is set and {@code below-threshold} is
 * written, that same rule removes those documents without a line of code here changing.
 */
@Component
public class Clustering {

    /**
     * ADR-087's k, floored to one less than the partition by {@link NearestNeighbourGraph}. An
     * operational number rather than a corpus judgement, which is why it is a code default and not a
     * profile key (ADR-082's precedent).
     */
    static final int NEIGHBOURS = 15;

    /**
     * How many documents a block holds at most, and the count ADR-087's own table is worked in.
     *
     * <p>ADR-087 leaves the number open — the hand-off spec's arithmetic uses 4,096 illustratively and
     * nothing measured it — so it is chosen here: keeping the spec's own figure means the ceiling the
     * ADR tabulated is the ceiling the code actually has, at 128 MiB for the two blocks live at once.
     * Larger would buy fewer passes over the file at a ceiling ADR-085's contract would no longer
     * recognise; smaller would double the passes for every halving, since the pass reads
     * ⌈N/B⌉²/2 blocks.
     */
    static final int BLOCK_DOCUMENTS = 4_096;

    /**
     * How many bytes a block may occupy, which is what actually bounds it.
     *
     * <p>A document count alone is not a ceiling: 4,096 vectors of 384 dimensions are 6 MiB and the
     * same 4,096 at 4,096 dimensions are 64 MiB — the same block, ten times apart, chosen by a model
     * nobody has picked yet. So the count is capped by a byte budget as well, set at exactly the size
     * ADR-087's table gives a block at its widest dimension. Below that dimension the count binds and
     * above it the bytes do; either way two blocks are 128 MiB whatever the model and whatever the
     * partition, which is the claim the ADR makes and the one this discharges.
     */
    static final long BLOCK_BUDGET_BYTES = 64L * 1024 * 1024;

    private final VectorCache vectorCache;
    private final RelevanceScoreCache scoreCache;
    private final DocumentClusters documentClusters;

    Clustering(VectorCache vectorCache, RelevanceScoreCache scoreCache, DocumentClusters documentClusters) {
        this.vectorCache = vectorCache;
        this.scoreCache = scoreCache;
        this.documentClusters = documentClusters;
    }

    /** The seeds whose partitions {@code runId} scored, in occurrence order — one clustering pass each. */
    public List<OccurrenceId> partitions(RunId runId) {
        return scoreCache.winningSeeds(runId);
    }

    /** The survivors {@code winningSeed} won under {@code runId}, in the order clustering visits them. */
    public List<OccurrenceId> membersOf(RunId runId, OccurrenceId winningSeed) {
        return scoreCache.partitionMembers(runId, winningSeed);
    }

    /**
     * Clusters one seed partition and records where each of its documents landed.
     *
     * <p>{@code contentHashesByOccurrence} is the partition in the order clustering visits it — which
     * is occurrence order, from {@link #membersOf} — each document mapped to the content hash its
     * vectors are stored under. The caller owes the hashes because only it can reach the files;
     * nothing here touches one.
     *
     * <p><b>What comes back is an observation about the graph, not about the recording.</b> The
     * spread of the similarities on the edges the graph kept is the one number that tells a partition
     * grouped by resemblance from a partition grouped by k (ADR-096), and it is returned rather than
     * stored because nothing reads it but the page: no verdict, no profile key and no gate follows
     * from it. Empty where the graph kept no edge, which is a partition of one.
     *
     * <p>Every document in the partition comes back with exactly one cluster ordinal, including a
     * document that resembles nothing, which lands in a cluster of its own. Nothing is merged and
     * nothing is left out: a survivor with no cluster is a page-tree hole, and a partition-level
     * catch-all would be a page no document belongs to (ADR-022's unattributed bucket sits at the top
     * level, for documents with no winning seed, which is a different condition with a different
     * cause).
     */
    public Optional<RetainedEdgeSpread> clusterAndRecord(
            RunId runId,
            OccurrenceId winningSeed,
            Map<OccurrenceId, String> contentHashesByOccurrence,
            String chunkerIdentity,
            String chunkingRuleIdentity,
            String modelName) {
        List<OccurrenceId> members = List.copyOf(contentHashesByOccurrence.keySet());
        if (members.isEmpty()) {
            return Optional.empty();
        }
        StoredMeanVectors vectors = new StoredMeanVectors(
                List.copyOf(contentHashesByOccurrence.values()), chunkerIdentity, chunkingRuleIdentity, modelName);
        NearestNeighbourGraph.Graph graph =
                NearestNeighbourGraph.build(vectors, NEIGHBOURS, blockSizeFor(vectors.dimension()));
        int[] ordinals = Communities.of(graph, Communities.DEFAULT_RESOLUTION);
        for (int document = 0; document < members.size(); document++) {
            documentClusters.record(runId, members.get(document), winningSeed, ordinals[document]);
        }
        return RetainedEdgeSpread.over(graph);
    }

    /**
     * How many documents one block holds at {@code dimension}: {@link #BLOCK_DOCUMENTS}, reduced to
     * whatever {@link #BLOCK_BUDGET_BYTES} affords when a vector is wide enough for that to bite.
     *
     * <p>Never zero. A model wide enough that one vector exceeds the whole budget still gets blocks of
     * one document, which is slow rather than wrong — the alternative is a division that reads as a
     * ceiling and returns a pass that reads nothing.
     */
    static int blockSizeFor(int dimension) {
        long withinBudget = BLOCK_BUDGET_BYTES / ((long) dimension * Float.BYTES);
        return (int) Math.max(1, Math.min(BLOCK_DOCUMENTS, withinBudget));
    }

    /**
     * A partition's document mean vectors, read from {@code vector} a block at a time.
     *
     * <p>The roster this holds is one content hash per document, not one vector per document: what
     * ADR-085 forbids being resident is the vectors, and the roster is what makes a block addressable
     * without them. Each mean is computed from the document's own chunk vectors as they are read, and
     * the block is discarded when the pass moves on.
     */
    private final class StoredMeanVectors implements NearestNeighbourGraph.MeanVectors {

        private final List<String> contentHashes;
        private final String chunkerIdentity;
        private final String chunkingRuleIdentity;
        private final String modelName;

        StoredMeanVectors(
                List<String> contentHashes, String chunkerIdentity, String chunkingRuleIdentity, String modelName) {
            this.contentHashes = contentHashes;
            this.chunkerIdentity = chunkerIdentity;
            this.chunkingRuleIdentity = chunkingRuleIdentity;
            this.modelName = modelName;
        }

        @Override
        public int count() {
            return contentHashes.size();
        }

        @Override
        public List<float[]> block(int from, int size) {
            List<float[]> means = new ArrayList<>();
            for (int document = from; document < Math.min(from + size, contentHashes.size()); document++) {
                means.add(meanOf(document));
            }
            return means;
        }

        /**
         * How wide one vector is, taken from the partition's first document.
         *
         * <p>One document is enough: every vector under one embedder identity has the model's own
         * dimension (ADR-084), so this is a lookup of a property of the model rather than a survey of
         * the partition.
         */
        int dimension() {
            return meanOf(0).length;
        }

        /**
         * One document's mean vector, computed from its chunk vectors and returned to be discarded.
         *
         * <p>Throws rather than treating a document with no stored vectors as a point at the origin:
         * every document in a partition is there because it was scored, and scoring already refuses a
         * survivor with no chunks (#108). A zero vector here would silently place it in whichever
         * cluster the arithmetic put the origin in.
         */
        private float[] meanOf(int document) {
            List<float[]> chunks = vectorCache.vectorsFor(
                    contentHashes.get(document), chunkerIdentity, chunkingRuleIdentity, modelName);
            if (chunks.isEmpty()) {
                throw new IllegalStateException(
                        "no stored chunk vectors under content hash " + contentHashes.get(document)
                                + " for a document that carries a relevance score; scoring refuses a"
                                + " survivor with no chunks (#108), so the vectors were written under a"
                                + " different model, chunker or chunking rule than the ones asked for here");
            }
            float[] mean = new float[chunks.getFirst().length];
            for (float[] chunk : chunks) {
                for (int component = 0; component < mean.length && component < chunk.length; component++) {
                    mean[component] += chunk[component];
                }
            }
            for (int component = 0; component < mean.length; component++) {
                mean[component] /= chunks.size();
            }
            return mean;
        }
    }
}
