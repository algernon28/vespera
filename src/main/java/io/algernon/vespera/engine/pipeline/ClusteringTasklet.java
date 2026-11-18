package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.embedding.Clustering;
import io.algernon.vespera.engine.embedding.ClusteringProgress;
import io.algernon.vespera.engine.embedding.RelevanceDistribution;
import io.algernon.vespera.engine.embedding.RetainedEdgeSpread;
import io.algernon.vespera.engine.embedding.TopicKey;
import io.algernon.vespera.engine.embedding.DocumentCluster;
import io.algernon.vespera.engine.embedding.DocumentClusters;
import io.algernon.vespera.engine.extraction.ChunkingRule;
import io.algernon.vespera.engine.extraction.ExtractionCacheKeys;
import io.algernon.vespera.engine.extraction.HybridChunker;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
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
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.synthesis.Arrangement;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stage 5's fifth step (ADR-087, #109, ADR-409 section 8): each topic's survivors divided into subtopics where the
 * topic is crowded and clustered, under the same scoring run the scores were written beneath.
 *
 * <p><b>One topic at a time, never corpus-wide (ADR-045).</b> A topic holds the survivors placed under it, which the
 * step before this one already recorded, so the work is bounded to N²/2
 * comparisons per topic rather than over the whole corpus — and within a topic it is bounded
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

    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final StageRuns stageRuns;
    private final Ledger ledger;
    private final HybridChunker hybridChunker;
    private final Clustering clustering;
    private final RelevanceDistribution relevanceDistribution;
    private final DocumentClusters documentClusters;
    private final ExtractionCacheKeys cacheKeys;
    private final Path workingDirectory;

    ClusteringTasklet(
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            StageRuns stageRuns,
            Ledger ledger,
            HybridChunker hybridChunker,
            Clustering clustering,
            RelevanceDistribution relevanceDistribution,
            DocumentClusters documentClusters,
            JdbcTemplate jdbcTemplate,
            @Value("${vespera.working-dir}") Path workingDirectory) {
        this.relevanceDistribution = relevanceDistribution;
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
                "stage 5's clustering step", stageRuns, seedGate, usableSeedGate);
        if (!preamble.isOpen()) {
            LOG.info(preamble.shutSentence().orElseThrow());
            ShutGates.in(chunkContext).record(StepNames.CLUSTERING, preamble.key().orElse(null));
            return RepeatStatus.FINISHED;
        }

        RunId scoring = stageRuns.embeddingScoring();
        String modelName = stageRuns.embeddingModelName();
        int clusterNeighbours = stageRuns.clusterNeighbours();
        double clusterResolution = stageRuns.clusterResolution();
        int subtopicFloor = stageRuns.subtopicFloor();

        // relevance-scoring and relevance-floor, which share this run, are not asked -- each step
        // answers only for itself. The empty-partition check sits between the finished check and the
        // discard (ADR-157 §5): the discard is at the head of work, after the check.
        TaskletSteps.StepWork formClusters =
                () -> {
                    List<TopicKey> topics =
                            TimedStatement.of(STAGE, "reading", "read", "the topics", () -> clustering.topics(scoring));
                    if (topics.isEmpty()) {
                        LOG.info(
                                "stage 5's clustering step is gated: no survivor is placed under a topic"
                                        + " under {}, so there is no topic to cluster. Nothing was"
                                        + " clustered.",
                                scoring.value());
                        ShutGates.in(chunkContext).record(StepNames.CLUSTERING);
                        return false;
                    }

                    // Scores are recorded, so the vectors have to be under one identity (ADR-228).
                    String embedderIdentity = TimedStatement.of(
                                    STAGE,
                                    "reading",
                                    "read",
                                    "the embedder identities",
                                    () -> relevanceDistribution.embedderIdentityFor(
                                            modelName, stageRuns.embeddingModelArtefact()))
                            .orElseThrow(() -> new IllegalStateException("the vectors under " + modelName
                                    + " carry no single embedder identity for the digest and weight dtype scoring"
                                    + " run " + scoring.value()
                                    + " names, so there is no one set of vectors to cluster"));

                    documentClusters.discardForRun(scoring);

                    RunId extractionRun = stageRuns.upstream(StageModules.EXTRACTION);
                    String chunkerIdentity = hybridChunker.identity();
                    String chunkingRuleIdentity = ChunkingRule.DEFAULT.identity().value();

                    LOG.info(
                            "Stage 5f (clustering) starting under scoring run {}: {} topic(s) to"
                                    + " cluster",
                            scoring.value(),
                            topics.size());
                    StageProgress topicsDone = StageProgress.over("Stage 5f (clustering, topics)", topics.size());
                    List<ClusterSizeReport.Topic> reported = new ArrayList<>();
                    for (int topicPlace = 0; topicPlace < topics.size(); topicPlace++) {
                        TopicKey topic = topics.get(topicPlace);
                        String whichTopic = "topic " + (topicPlace + 1) + " of " + topics.size();
                        // One topic's members at a time, held until it is clustered and let go before the
                        // next is read (ADR-211 section 5, ADR-409 section 13). A document removed as
                        // below-threshold still carries the score row that put it under a topic, and clustering
                        // it would give a page to a document this run has just decided is not in the archive;
                        // ADR-060 keeps the verdict join in the ledger, so the filter is a question put to it,
                        // in the order the members came.
                        List<OccurrenceId> read = TimedStatement.of(
                                STAGE, "reading", "read",
                                "the members of " + whichTopic,
                                () -> clustering.membersOf(scoring, topic));
                        Set<OccurrenceId> surviving = ledger.verdicts().survivingAmong(scoring, read);
                        List<OccurrenceId> members =
                                read.stream().filter(surviving::contains).toList();
                        if (members.isEmpty()) {
                            // Every document placed under this topic was removed by the floor. A topic of
                            // nothing is not a topic to arrange, and a heading with no page under it is not
                            // worth minting. It is counted all the same: the stage has been through it.
                            topicsDone.itemDone();
                            continue;
                        }
                        // The spread of the kept edges comes back from the pass that built the graph,
                        // because that is the only place the similarities exist: recovering them
                        // afterwards would mean the N-squared-over-two pass a second time (ADR-096).
                        // Nothing reads it but the page.
                        int place = topicPlace + 1;
                        int of = topics.size();
                        StageProgress keysRead = StageProgress.over(
                                "Stage 5f (clustering, cache keys read, topic " + place + " of " + of + ")",
                                members.size());
                        Clustering.Spreads spreads = clustering.clusterAndRecordWithSubtopicSpreads(
                                scoring,
                                topic,
                                contentHashesOf(extractionRun, members, keysRead),
                                chunkerIdentity,
                                chunkingRuleIdentity,
                                embedderIdentity,
                                // The numbers the scoring run was minted under, so the topic is divided and
                                // clustered as the run that names it says (ADR-330 section 9, ADR-409 section 8,
                                // ADR-117).
                                clusterNeighbours,
                                clusterResolution,
                                subtopicFloor,
                                blocksOfTopic(place, of));
                        // Read back rather than returned from the pass: a cluster exists as the set of
                        // rows carrying its identity, so the sizes a reader is shown are the rows, not
                        // what the arithmetic meant to write.
                        // The sizes are let go once the five numbers the page shows are taken from them
                        // (ADR-223 section 2).
                        // A divided topic's row is followed by a row for each subtopic, most survivors first
                        // (ADR-416 section 5), each with the sizes of its own clusters and the spread of its own graph.
                        Map<Integer, Integer> survivorsOfSubtopics = TimedStatement.of(
                                STAGE, "reading", "read",
                                "the subtopics of " + whichTopic,
                                () -> documentClusters.survivorsOfSubtopics(scoring, topic));
                        if (survivorsOfSubtopics.isEmpty()) {
                            reported.add(ClusterSizeReport.Topic.of(
                                    topic.firstPassage().seedPath(),
                                    topic.firstPassage().chunkIndex(),
                                    TimedStatement.of(
                                            STAGE, "reading", "read",
                                            "the cluster sizes of " + whichTopic,
                                            () -> documentClusters.sizesFor(scoring, topic)),
                                    spreads.topic()));
                        } else {
                            List<ClusterSizeReport.Subtopic> subtopics = new ArrayList<>();
                            for (int subtopic : Arrangement.subtopicsInOrder(survivorsOfSubtopics)) {
                                subtopics.add(ClusterSizeReport.Subtopic.of(
                                        subtopic,
                                        TimedStatement.of(
                                                STAGE, "reading", "read",
                                                "the cluster sizes of subtopic " + subtopic + " of " + whichTopic,
                                                () -> documentClusters.sizesFor(scoring, topic, subtopic)),
                                        spreads.subtopics().getOrDefault(subtopic, Optional.empty())));
                            }
                            reported.add(ClusterSizeReport.Topic.divided(
                                    topic.firstPassage().seedPath(),
                                    topic.firstPassage().chunkIndex(),
                                    subtopics,
                                    spreads.topic()));
                        }
                        topicsDone.itemDone();
                    }

                    // The JSON is moved into place before the page is written (ADR-362 section 9).
                    writeJson(ClusterSizeReport.document(reported, Clock.systemUTC().instant()));
                    write(CLUSTER_SIZES_FILE_NAME, ClusterSizeReport.render(reported));
                    LOG.info(
                            "Stage 5f (clustering) finished under scoring run {}: {} topic(s), {}"
                                    + " document(s) in {} cluster(s)",
                            scoring.value(),
                            reported.size(),
                            reported.stream().mapToInt(ClusterSizeReport.Topic::documentCount).sum(),
                            reported.stream().mapToInt(ClusterSizeReport.Topic::clusterCount).sum());
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
     * Whether, for every topic of {@code scoring}, the clusters are of its survivors (ADR-230
     * section 2, ADR-409 section 8): no placed member that survives lacks a cluster row, and no clustered document has
     * stopped surviving. A document with a cluster row and no score survives and is no member, so it
     * answers neither question and the arrangement step still stops on it. {@code true} with no topic.
     */
    private boolean clustersAreOfTheSurvivors(RunId scoring) {
        List<TopicKey> topics = TimedStatement.of(
                STAGE, "reading", "read", "the topics", () -> clustering.topics(scoring));
        for (TopicKey topic : topics) {
            List<OccurrenceId> read = TimedStatement.of(
                    STAGE, "reading", "read", "the members of a topic", () -> clustering.membersOf(scoring, topic));
            Set<OccurrenceId> surviving = ledger.verdicts().survivingAmong(scoring, read);
            List<OccurrenceId> clustered = TimedStatement.of(
                            STAGE, "reading", "read", "the clustered documents of a topic",
                            () -> documentClusters.membersOf(scoring, topic))
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
     * What {@code embedding} tells this stage about one topic's pass over pairs of blocks: a new counter
     * for each announcement, named for the topic's 1-based place among {@code of} (ADR-192 section 5).
     */
    private static ClusteringProgress blocksOfTopic(int place, int of) {
        return new ClusteringProgress() {
            private StageProgress compared;

            @Override
            public void toCompareBlocks(long blockPairs) {
                compared = StageProgress.over(
                        "Stage 5f (clustering, comparison blocks, topic " + place + " of " + of + ")",
                        blockPairs);
            }

            @Override
            public void blockPairCompared() {
                compared.itemDone();
            }
        };
    }

    private void writeJson(AnalysisReportJson.Document document) {
        Path file = workingDirectory.resolve(ClusterSizeReport.NAME + ".json");
        try {
            Files.createDirectories(workingDirectory);
            AnalysisReportJson.write(file, document);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + file, e);
        }
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
