package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.Clustering;
import io.algernon.vespera.embedding.ClusteringProgress;
import io.algernon.vespera.embedding.RetainedEdgeSpread;
import io.algernon.vespera.embedding.DocumentCluster;
import io.algernon.vespera.embedding.DocumentClusters;
import io.algernon.vespera.extraction.ChunkingRule;
import io.algernon.vespera.extraction.ExtractionCacheKeys;
import io.algernon.vespera.extraction.HybridChunker;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import io.algernon.vespera.ledger.RunId;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stage 5's fifth step (ADR-087, #109): each seed partition's survivors clustered, under
 * the same scoring run the scores were written beneath.
 *
 * <p><b>One partition at a time, never corpus-wide (ADR-045).</b> A partition is the set of survivors
 * one seed won, which the step before this one already recorded, so the work is bounded to N²/2
 * comparisons per partition rather than over the whole corpus — and within a partition it is bounded
 * again, to the two blocks of vectors {@link Clustering} streams past one another.
 *
 * <p><b>Nothing here removes anything.</b> No verdict is written, no document is left unclustered and
 * no cluster is merged into another: clustering arranges what survived, and the arrangement is a
 * measurement like any other (ADR-077: a re-run under another run id writes its own row set; under the
 * same run the step replaces its own, where unfinished or where the survivors have changed, ADR-230).
 *
 * <p>It ends by writing the size distribution, because the cluster count is not chosen and therefore
 * cannot be known in advance: whoever builds the deliverable above these clusters needs to see what
 * shape they came out in before they build it.
 *
 * <p>Its completion record is honoured only while its rows are of the survivors as they stand: the
 * relevance-floor step decides again on every invocation, so where a survivor of a partition has no
 * cluster row, or a clustered document is no longer a survivor, the step forms the clusters again under
 * the same run (ADR-230). A document with a cluster row and no score answers neither question, so the
 * arrangement step still stops on it.
 *
 * <p>Gated exactly as the scoring step before it is, and for the same reasons: with no model named,
 * no seed folder, or no usable seed, no score exists, so there is no partition to cluster.
 */
@Component
@StepScope
class ClusteringTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(ClusteringTasklet.class);

    /** Stage 5f's own name, which its statement lines open with. */
    private static final String STAGE = "Stage 5f (clustering)";

    /** The size report's name in the working directory, beside the profile and the database (ADR-054). */
    static final String CLUSTER_SIZES_FILE_NAME = "cluster-sizes.html";

    private final EmbeddingModelGate embeddingModelGate;
    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final StageRuns stageRuns;
    private final Ledger ledger;
    private final HybridChunker hybridChunker;
    private final Clustering clustering;
    private final DocumentClusters documentClusters;
    private final ExtractionCacheKeys cacheKeys;
    private final Path workingDirectory;

    ClusteringTasklet(
            EmbeddingModelGate embeddingModelGate,
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            StageRuns stageRuns,
            Ledger ledger,
            HybridChunker hybridChunker,
            Clustering clustering,
            DocumentClusters documentClusters,
            JdbcTemplate jdbcTemplate,
            @Value("${vespera.working-dir}") Path workingDirectory) {
        this.embeddingModelGate = embeddingModelGate;
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.stageRuns = stageRuns;
        this.ledger = ledger;
        this.hybridChunker = hybridChunker;
        this.clustering = clustering;
        this.documentClusters = documentClusters;
        this.cacheKeys = new ExtractionCacheKeys(jdbcTemplate);
        this.workingDirectory = workingDirectory;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        StageFiveGates.Preamble preamble = StageFiveGates.modelSeedWalkUsable(
                "stage 5's clustering step", embeddingModelGate, seedGate, usableSeedGate);
        if (!preamble.isOpen()) {
            LOG.info(preamble.shutSentence().orElseThrow());
            return RepeatStatus.FINISHED;
        }
        String modelName = preamble.modelName().orElseThrow();

        RunId scoring = stageRuns.embeddingScoring();

        // relevance-scoring and relevance-floor, which share this run, are not asked -- each step
        // answers only for itself. The empty-partition check sits between the finished check and the
        // discard (ADR-157 §5): the discard is at the head of work, after the check.
        TaskletSteps.StepWork formClusters =
                () -> {
                    List<OccurrenceId> partitions =
                            TimedStatement.of(STAGE, "reading", "read", "the seed partitions", () -> clustering.partitions(scoring));
                    if (partitions.isEmpty()) {
                        LOG.info(
                                "stage 5's clustering step is gated: no survivor carries a relevance score"
                                        + " under {}, so there is no partition to cluster. Nothing was"
                                        + " clustered.",
                                scoring.value());
                        return false;
                    }

                    documentClusters.discardForRun(scoring);

                    RunId extractionRun = stageRuns.upstream(StageModules.EXTRACTION);
                    String chunkerIdentity = hybridChunker.identity();
                    String chunkingRuleIdentity = ChunkingRule.DEFAULT.identity().value();

                    LOG.info(
                            "Stage 5f (clustering) starting under scoring run {}: {} seed partition(s) to"
                                    + " cluster",
                            scoring.value(),
                            partitions.size());
                    StageProgress partitionsDone =
                            StageProgress.over("Stage 5f (clustering, seed partitions)", partitions.size());
                    List<ClusterSizeReport.Partition> reported = new ArrayList<>();
                    for (int partition = 0; partition < partitions.size(); partition++) {
                        OccurrenceId winningSeed = partitions.get(partition);
                        String whichPartition = "partition " + (partition + 1) + " of " + partitions.size();
                        // One partition's members at a time, held until it is clustered and let go before the
                        // next is read (ADR-211 section 5). A document removed as below-threshold still
                        // carries the score row that put it in a partition, and clustering it would give a
                        // page to a document this run has just decided is not in the archive; ADR-060 keeps
                        // the verdict join in the ledger, so the filter is a question put to it, in the order
                        // the members came.
                        List<OccurrenceId> read = TimedStatement.of(
                                STAGE, "reading", "read",
                                "the members of " + whichPartition,
                                () -> clustering.membersOf(scoring, winningSeed));
                        Set<OccurrenceId> surviving = ledger.verdicts().survivingAmong(scoring, read);
                        List<OccurrenceId> members =
                                read.stream().filter(surviving::contains).toList();
                        if (members.isEmpty()) {
                            // Every document this seed won was removed by the floor. A partition of
                            // nothing is not a partition, and a heading with no page under it is not
                            // worth minting. It is counted all the same: the stage has been through it.
                            partitionsDone.itemDone();
                            continue;
                        }
                        // The spread of the kept edges comes back from the pass that built the graph,
                        // because that is the only place the similarities exist: recovering them
                        // afterwards would mean the N-squared-over-two pass a second time (ADR-096).
                        // Nothing reads it but the page.
                        int place = partition + 1;
                        int of = partitions.size();
                        StageProgress keysRead = StageProgress.over(
                                "Stage 5f (clustering, cache keys read, partition " + place + " of " + of + ")",
                                members.size());
                        Optional<RetainedEdgeSpread> spread = clustering.clusterAndRecord(
                                scoring,
                                winningSeed,
                                contentHashesOf(extractionRun, members, keysRead),
                                chunkerIdentity,
                                chunkingRuleIdentity,
                                modelName,
                                blocksOfPartition(partition + 1, partitions.size()));
                        // Read back rather than returned from the pass: a cluster exists as the set of
                        // rows carrying its identity, so the sizes a reader is shown are the rows, not
                        // what the arithmetic meant to write.
                        // The sizes are let go once the five numbers the page shows are taken from them
                        // (ADR-223 section 2).
                        reported.add(ClusterSizeReport.Partition.of(
                                pathOf(winningSeed),
                                TimedStatement.of(
                                        STAGE, "reading", "read",
                                        "the cluster sizes of " + whichPartition,
                                        () -> documentClusters.sizesFor(scoring, winningSeed)),
                                spread));
                        partitionsDone.itemDone();
                    }

                    write(CLUSTER_SIZES_FILE_NAME, ClusterSizeReport.render(reported));
                    LOG.info(
                            "Stage 5f (clustering) finished under scoring run {}: {} partition(s), {}"
                                    + " document(s) in {} cluster(s)",
                            scoring.value(),
                            reported.size(),
                            reported.stream().mapToInt(ClusterSizeReport.Partition::documentCount).sum(),
                            reported.stream().mapToInt(ClusterSizeReport.Partition::clusterCount).sum());
                    return true;
                };

        // The completion record is honoured only while the rows are of the survivors as they stand
        // (ADR-230 section 2); the record itself stays, so nothing is finished again.
        return TaskletSteps.once(
                ledger,
                scoring,
                StepNames.CLUSTERING,
                () -> {
                    if (clustersAreOfTheSurvivors(scoring)) {
                        LOG.info("Stage 5f (clustering) was already recorded under scoring run {}", scoring.value());
                        return;
                    }
                    LOG.info(
                            "Stage 5f (clustering) was recorded under scoring run {} over other survivors than"
                                    + " the run has now: the relevance-floor step has decided again since."
                                    + " The step does its work again.",
                            scoring.value());
                    // The work discards the run's rows at its head, past the empty-partition check.
                    formClusters.run();
                },
                () -> {},
                formClusters);
    }

    /**
     * Whether, for every seed partition of {@code scoring}, the clusters are of its survivors (ADR-230
     * section 2): no scored member that survives lacks a cluster row, and no clustered document has
     * stopped surviving. A document with a cluster row and no score survives and is no member, so it
     * answers neither question and the arrangement step still stops on it. {@code true} with no partition.
     */
    private boolean clustersAreOfTheSurvivors(RunId scoring) {
        List<OccurrenceId> partitions = TimedStatement.of(
                STAGE, "reading", "read", "the seed partitions", () -> clustering.partitions(scoring));
        for (OccurrenceId winningSeed : partitions) {
            List<OccurrenceId> read = TimedStatement.of(
                    STAGE, "reading", "read", "the members of a partition", () -> clustering.membersOf(scoring, winningSeed));
            Set<OccurrenceId> surviving = ledger.verdicts().survivingAmong(scoring, read);
            List<OccurrenceId> clustered = TimedStatement.of(
                            STAGE, "reading", "read", "the clustered documents of a partition",
                            () -> documentClusters.membersOf(scoring, winningSeed))
                    .stream()
                    .map(DocumentCluster::occurrenceId)
                    .toList();
            if (!Set.copyOf(clustered).containsAll(surviving)
                    || ledger.verdicts().survivingAmong(scoring, clustered).size() != clustered.size()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Each partition member's content hash, in the order clustering visits them.
     *
     * <p>Read here from the key stage 2 recorded under {@code extractionRun}, because only {@code
     * pipeline} reads that table for another module, exactly as the scoring step before it does (ADR-206).
     * No file is opened. This is a hash per document rather than a vector per document, which is what
     * ADR-085's ceiling is about: it is what lets {@link Clustering} address a block without holding
     * the partition.
     */
    private Map<OccurrenceId, String> contentHashesOf(
            RunId extractionRun, List<OccurrenceId> members, StageProgress keysRead) {
        Map<OccurrenceId, String> contentHashes = new LinkedHashMap<>();
        for (OccurrenceId member : members) {
            contentHashes.put(member, cacheKeys.requireForOccurrence(member, extractionRun));
            keysRead.itemDone();
        }
        return contentHashes;
    }

    /**
     * What {@code embedding} tells this stage about one partition's pass over pairs of blocks: a new counter
     * for each announcement, named for the partition's 1-based place among {@code of} (ADR-192 section 5).
     */
    private static ClusteringProgress blocksOfPartition(int place, int of) {
        return new ClusteringProgress() {
            private StageProgress compared;

            @Override
            public void toCompareBlocks(long blockPairs) {
                compared = StageProgress.over(
                        "Stage 5f (clustering, comparison blocks, partition " + place + " of " + of + ")",
                        blockPairs);
            }

            @Override
            public void blockPairCompared() {
                compared.itemDone();
            }
        };
    }

    private String pathOf(OccurrenceId occurrenceId) {
        return ledger.occurrences().factsFor(occurrenceId)
                .map(facts -> facts.path().value())
                .orElse("(path not recorded)");
    }

    private void write(String fileName, String content) {
        Path file = workingDirectory.resolve(fileName);
        try {
            Files.createDirectories(workingDirectory);
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + file, e);
        }
    }
}
