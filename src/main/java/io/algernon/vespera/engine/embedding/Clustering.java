package io.algernon.vespera.engine.embedding;

import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.RunId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
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
 * document with a blocking verdict under a run upstream of the scoring run is never scored, so it has
 * no score row, so it is not in any partition here and costs nothing. A {@code below-threshold}
 * verdict is written under the scoring run itself after the score, so its document is still in the
 * partition this reads; {@code ClusteringTasklet} drops it by reading the scoring run's survivors
 * (ADR-156), without a line of code here changing.
 */
@Component
public class Clustering {

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
    private final Placements placements;
    private final DocumentClusters documentClusters;

    Clustering(VectorCache vectorCache, Placements placements, DocumentClusters documentClusters) {
        this.vectorCache = vectorCache;
        this.placements = placements;
        this.documentClusters = documentClusters;
    }

    /**
     * How wide a vector stored under the three identities is, {@code 0} where none is (ADR-273 section 1):
     * one read of the vector cache, computing no mean vector.
     */
    int dimensionUnder(String chunkerIdentity, String chunkingRuleIdentity, String embedderIdentity) {
        return vectorCache.dimensionUnder(chunkerIdentity, chunkingRuleIdentity, embedderIdentity);
    }

    /**
     * One document's mean vector computed from its stored chunk vectors as clustering computes it (ADR-087), for
     * its own use: empty where none is stored, which is no error. Returned to be discarded.
     */
    float[] meanVectorOf(
            String contentHash, String chunkerIdentity, String chunkingRuleIdentity, String embedderIdentity) {
        List<float[]> chunks =
                vectorCache.vectorsFor(contentHash, chunkerIdentity, chunkingRuleIdentity, embedderIdentity);
        if (chunks.isEmpty()) {
            return new float[0];
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

    /**
     * The survivors of {@code contentHashes}, which are one subtopic's, strongest first (ADR-407 section 4, ADR-409
     * section 6): by the cosine of each survivor's mean vector to the subtopic's centroid, two of equal cosine in the
     * order of their paths as ADR-399 section 4 compares a seed's. The centroid is taken in one pass over the members,
     * summing their mean vectors each taken to unit length, and each member's cosine in a second, so that no mean
     * vector is held past its member (ADR-085).
     *
     * @param paths each survivor's path, which breaks a tie
     * @throws IllegalStateException for a survivor with no stored chunk vector under the identities
     */
    public List<OccurrenceId> strongestFirst(
            Map<OccurrenceId, String> contentHashes,
            Map<OccurrenceId, String> paths,
            String chunkerIdentity,
            String chunkingRuleIdentity,
            String embedderIdentity) {
        double[] centroid = null;
        for (String contentHash : contentHashes.values()) {
            double[] unit = unitLengthMeanOf(contentHash, chunkerIdentity, chunkingRuleIdentity, embedderIdentity);
            if (centroid == null) {
                centroid = new double[unit.length];
            }
            for (int component = 0; component < unit.length; component++) {
                centroid[component] += unit[component];
            }
        }
        Map<OccurrenceId, Double> cosines = new java.util.HashMap<>();
        for (Map.Entry<OccurrenceId, String> member : contentHashes.entrySet()) {
            double[] unit =
                    unitLengthMeanOf(member.getValue(), chunkerIdentity, chunkingRuleIdentity, embedderIdentity);
            double dot = 0;
            double norm = 0;
            for (int component = 0; component < unit.length; component++) {
                dot += unit[component] * centroid[component];
                norm += centroid[component] * centroid[component];
            }
            cosines.put(member.getKey(), norm == 0 ? 0.0 : dot / Math.sqrt(norm));
        }
        List<OccurrenceId> ordered = new ArrayList<>(contentHashes.keySet());
        ordered.sort(java.util.Comparator.<OccurrenceId>comparingDouble(cosines::get)
                .reversed()
                .thenComparing((left, right) -> SeedPassage.comparePaths(paths.get(left), paths.get(right))));
        return List.copyOf(ordered);
    }

    private double[] unitLengthMeanOf(
            String contentHash, String chunkerIdentity, String chunkingRuleIdentity, String embedderIdentity) {
        float[] mean = meanVectorOf(contentHash, chunkerIdentity, chunkingRuleIdentity, embedderIdentity);
        if (mean.length == 0) {
            throw new IllegalStateException("no stored chunk vectors under content hash " + contentHash
                    + " for a survivor of a subtopic being named, under the identities asked for");
        }
        double norm = 0;
        for (float component : mean) {
            norm += (double) component * component;
        }
        norm = Math.sqrt(norm);
        double[] unit = new double[mean.length];
        for (int component = 0; component < mean.length; component++) {
            unit[component] = norm == 0 ? 0 : mean[component] / norm;
        }
        return unit;
    }

    /** The topics {@code runId} placed at least one survivor under, in key order — one clustering pass each (ADR-409 section 8). */
    public List<TopicKey> topics(RunId runId) {
        return placements.topicsPlacedUnder(runId);
    }

    /** The documents with a link to {@code topic} under {@code runId}, a page of 1,000 at a time (ADR-416 section 8). */
    public void eachPageLinkedTo(
            RunId runId, TopicKey topic, java.util.function.Consumer<List<OccurrenceId>> page) {
        placements.eachPageLinkedTo(runId, topic, page);
    }

    /** The survivors {@code topic} holds under {@code runId}, in the order clustering visits them. */
    public List<OccurrenceId> membersOf(RunId runId, TopicKey topic) {
        return placements.membersOf(runId, topic);
    }

    /**
     * Clusters one topic and records where each of its documents landed: its subtopic, where the topic is crowded
     * and divided (ADR-406), and its cluster.
     *
     * <p>{@code contentHashesByOccurrence} is the topic in the order clustering visits it — which
     * is occurrence order, from {@link #membersOf} — each document mapped to the content hash its
     * vectors are stored under. The caller owes the hashes because only it can reach the files;
     * nothing here touches one.
     *
     * <p><b>What comes back is an observation about the graph, not about the recording.</b> The
     * spread of the similarities on the edges the graph kept over the whole topic is the one number that tells a topic
     * clustered by resemblance from a topic clustered by k (ADR-096), and it is returned rather than
     * stored because nothing reads it but the page: no verdict, no profile key and no gate follows
     * from it. Empty where the graph kept no edge, which is a topic of one.
     *
     * <p>Every document in the topic comes back with exactly one cluster ordinal, including a
     * document that resembles nothing, which lands in a cluster of its own. Nothing is merged and
     * nothing is left out: a survivor with no cluster is a page-tree hole, and a topic-level
     * catch-all would be a page no document belongs to (ADR-022's unattributed bucket sits at the top
     * level, for documents placed nowhere, which is a different condition with a different cause).
     *
     * <p>{@code neighbours} is ADR-087's k, the preset {@code vespera.clustering.neighbours} (shipped as 15), and
     * {@code resolution} the one {@code vespera.clustering.resolution} or the profile's {@code clusterResolution}
     * gives (shipped as 1.0; ADR-330 §8, §9). Both are the numbers the scoring run was minted under, handed down by
     * it, and neither is held by a constant here. {@code subtopicFloor} is the number of survivors from which a topic
     * is divided (ADR-406 section 1, shipped as 300).
     *
     * @throws IllegalArgumentException for {@code neighbours} below 1, a {@code subtopicFloor} below 1, or a {@code
     *     resolution} that is not a finite number above 0, whatever the topic holds
     */
    public Optional<RetainedEdgeSpread> clusterAndRecord(
            RunId runId,
            TopicKey topic,
            Map<OccurrenceId, String> contentHashesByOccurrence,
            String chunkerIdentity,
            String chunkingRuleIdentity,
            String embedderIdentity,
            int neighbours,
            double resolution,
            int subtopicFloor) {
        return clusterAndRecord(
                runId,
                topic,
                contentHashesByOccurrence,
                chunkerIdentity,
                chunkingRuleIdentity,
                embedderIdentity,
                neighbours,
                resolution,
                subtopicFloor,
                ClusteringProgress.NONE);
    }

    /**
     * As {@link #clusterAndRecord(RunId, TopicKey, Map, String, String, String, int, double, int)}, telling {@code
     * progress} how many pairs of blocks the pass over the whole topic compares and each pair as it is done (ADR-192
     * section 5). A topic with no member returns before the pass and calls neither method.
     */
    public Optional<RetainedEdgeSpread> clusterAndRecord(
            RunId runId,
            TopicKey topic,
            Map<OccurrenceId, String> contentHashesByOccurrence,
            String chunkerIdentity,
            String chunkingRuleIdentity,
            String embedderIdentity,
            int neighbours,
            double resolution,
            int subtopicFloor,
            ClusteringProgress progress) {
        return clusterAndRecordWithSubtopicSpreads(
                        runId,
                        topic,
                        contentHashesByOccurrence,
                        chunkerIdentity,
                        chunkingRuleIdentity,
                        embedderIdentity,
                        neighbours,
                        resolution,
                        subtopicFloor,
                        progress)
                .topic();
    }

    /**
     * What a topic's pass observed of its graphs (ADR-096, ADR-416 section 5).
     *
     * @param topic the spread over the whole topic's graph, as {@code clusterAndRecord} returns it
     * @param subtopics each subtopic's spread, by its number, over the graph its clusters were found over; empty
     *     where the topic is not divided
     */
    public record Spreads(Optional<RetainedEdgeSpread> topic, Map<Integer, Optional<RetainedEdgeSpread>> subtopics) {}

    /**
     * As {@link #clusterAndRecord(RunId, TopicKey, Map, String, String, String, int, double, int, ClusteringProgress)},
     * returning each subtopic's spread beside the topic's, from the same pass.
     */
    public Spreads clusterAndRecordWithSubtopicSpreads(
            RunId runId,
            TopicKey topic,
            Map<OccurrenceId, String> contentHashesByOccurrence,
            String chunkerIdentity,
            String chunkingRuleIdentity,
            String embedderIdentity,
            int neighbours,
            double resolution,
            int subtopicFloor,
            ClusteringProgress progress) {
        if (neighbours < 1) {
            throw new IllegalArgumentException("a graph keeps at least one neighbour, not " + neighbours);
        }
        if (!(resolution > 0) || Double.isInfinite(resolution)) {
            throw new IllegalArgumentException("the resolution must be a finite number above 0, not " + resolution);
        }
        if (subtopicFloor < 1) {
            throw new IllegalArgumentException(
                    "a topic must hold at least one survivor to be divided, not " + subtopicFloor);
        }
        List<OccurrenceId> members = List.copyOf(contentHashesByOccurrence.keySet());
        if (members.isEmpty()) {
            return new Spreads(Optional.empty(), Map.of());
        }
        StoredMeanVectors vectors = new StoredMeanVectors(
                List.copyOf(contentHashesByOccurrence.values()), chunkerIdentity, chunkingRuleIdentity, embedderIdentity);
        int blockSize = blockSizeFor(vectors.dimension());
        NearestNeighbourGraph.Graph graph = NearestNeighbourGraph.build(vectors, neighbours, blockSize, progress);
        TopicDivision division = TopicDivision.of(graph, vectors, neighbours, resolution, subtopicFloor, blockSize);
        for (int document = 0; document < members.size(); document++) {
            OptionalInt subtopic = division.hasSubtopics()
                    ? OptionalInt.of(division.subtopicOf().get(document))
                    : OptionalInt.empty();
            documentClusters.record(runId, members.get(document), topic, subtopic, division.clusterOf().get(document));
        }
        Map<Integer, Optional<RetainedEdgeSpread>> subtopics = new java.util.LinkedHashMap<>();
        for (int subtopic = 0; subtopic < division.subtopicSpreads().size(); subtopic++) {
            subtopics.put(subtopic, division.subtopicSpreads().get(subtopic));
        }
        return new Spreads(RetainedEdgeSpread.over(graph), subtopics);
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
        private final String embedderIdentity;

        StoredMeanVectors(
                List<String> contentHashes,
                String chunkerIdentity,
                String chunkingRuleIdentity,
                String embedderIdentity) {
            this.contentHashes = contentHashes;
            this.chunkerIdentity = chunkerIdentity;
            this.chunkingRuleIdentity = chunkingRuleIdentity;
            this.embedderIdentity = embedderIdentity;
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
            float[] mean = meanVectorOf(
                    contentHashes.get(document), chunkerIdentity, chunkingRuleIdentity, embedderIdentity);
            if (mean.length == 0) {
                throw new IllegalStateException(
                        "no stored chunk vectors under content hash " + contentHashes.get(document)
                                + " for a document that carries a relevance score; scoring refuses a"
                                + " survivor with no chunks (#108), so the vectors were written under a"
                                + " different embedder identity, chunker or chunking rule than the ones asked for here");
            }
            return mean;
        }
    }
}
