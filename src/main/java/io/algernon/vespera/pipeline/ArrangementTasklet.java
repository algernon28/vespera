package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.DocumentTitles;
import io.algernon.vespera.extraction.ExtractionCacheKeys;
import io.algernon.vespera.embedding.DocumentCluster;
import io.algernon.vespera.embedding.DocumentClusters;
import io.algernon.vespera.embedding.RelevanceScoring;
import io.algernon.vespera.embedding.ScoringProgress;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.synthesis.ArrangedCluster;
import io.algernon.vespera.synthesis.Arrangement;
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.ClusteredDocument;
import io.algernon.vespera.synthesis.Clusters;
import io.algernon.vespera.synthesis.LabelledCluster;
import io.algernon.vespera.synthesis.LeadDocument;
import io.algernon.vespera.synthesis.Partition;
import io.algernon.vespera.synthesis.RecordedCluster;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stage 6a (ADR-105, ADR-106, ADR-110, ADR-112, #175): the clusters stage 5 formed are given a name,
 * a size and a place, under a run of their own.
 *
 * <p><b>It adds a level rather than restating one.</b> Stage 5 records which documents share a
 * cluster, and records it without the cluster having a row anywhere. What this writes is the row that
 * did not exist; not one membership row is written again.
 *
 * <p><b>It judges nothing.</b> No verdict of any kind is written, including a passing one: ADR-049
 * says every stage writes a row per occurrence and no stage does, and that gap deserves its own
 * decision rather than being closed as a side effect of the one stage in the cascade that removes
 * nothing.
 *
 * <p><b>The arrangement is total, and nothing here papers over a gap in it.</b> Every survivor has a
 * score, a winning seed and a cluster ordinal, so a cluster whose members carry no score is a broken
 * invariant rather than a case to accommodate — the rows go to {@code synthesis} exactly as they were
 * read, and it stops, in the same way and for the same reason {@code scoreAndRecord} refuses to score
 * an unvectored survivor. A defensive miscellaneous bucket here would turn that into a silently
 * rendered section of the deliverable.
 *
 * <p><b>One seed partition at a time</b> (ADR-223 section 3). The partitions are ordered from their sizes
 * and their seeds' paths alone; then each is read, arranged, labelled, recorded, read back and written to the
 * page, and let go before the next is read.
 *
 * <p>Gated exactly as the clustering step before it is, and for the same reasons: with no model
 * named, no seed folder or no usable seed, no score exists, so there is nothing to arrange.
 */
@Component
@StepScope
class ArrangementTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(ArrangementTasklet.class);

    /** Stage 6a's own name, which its statement lines open with. */
    private static final String STAGE = "Stage 6a (arrangement)";

    /**
     * The page's name in the working directory, beside the profile and the database (ADR-054).
     *
     * <p>Written inside this step rather than by a report step of its own, following
     * {@code cluster-sizes.html} inside {@link ClusteringTasklet}: it is this step's own gate page and
     * not a report over somebody else's run (ADR-110).
     */
    static final String ARRANGEMENT_FILE_NAME = "arrangement.html";

    private final EmbeddingModelGate embeddingModelGate;
    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final StageRuns stageRuns;
    private final DocumentClusters documentClusters;
    private final RelevanceScoring relevanceScoring;
    private final Clusters clusters;
    private final Ledger ledger;
    private final ExtractionCacheKeys cacheKeys;
    private final DocumentTitles documentTitles;
    private final Path root;
    private final Path workingDirectory;

    ArrangementTasklet(
            EmbeddingModelGate embeddingModelGate,
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            StageRuns stageRuns,
            DocumentClusters documentClusters,
            RelevanceScoring relevanceScoring,
            Clusters clusters,
            Ledger ledger,
            JdbcTemplate jdbcTemplate,
            DocumentTitles documentTitles,
            @Value("#{jobParameters['root']}") Path root,
            @Value("${vespera.working-dir}") Path workingDirectory) {
        this.embeddingModelGate = embeddingModelGate;
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.stageRuns = stageRuns;
        this.documentClusters = documentClusters;
        this.relevanceScoring = relevanceScoring;
        this.clusters = clusters;
        this.ledger = ledger;
        this.cacheKeys = new ExtractionCacheKeys(jdbcTemplate);
        this.documentTitles = documentTitles;
        this.root = root;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        StageFiveGates.Preamble preamble = StageFiveGates.modelSeedWalkUsable(
                "the arrangement step", embeddingModelGate, seedGate, usableSeedGate);
        if (!preamble.isOpen()) {
            LOG.info(preamble.shutSentence().orElseThrow());
            return RepeatStatus.FINISHED;
        }

        RunId scoring = stageRuns.embeddingScoring();
        // The seeds that won a survivor, and how many documents sit under each: a count a seed, so that the
        // partitions are ordered without reading a document of any of them (ADR-223 section 3). Made on both
        // branches, before the step knows which it is on.
        List<OccurrenceId> winningSeeds = TimedStatement.of(
                STAGE, "reading", "read", "the seed partitions", () -> relevanceScoring.winningSeeds(scoring));
        Map<OccurrenceId, Integer> memberCounts = new LinkedHashMap<>();
        for (OccurrenceId seed : winningSeeds) {
            int members = documentClusters.sizeOf(scoring, seed);
            if (members > 0) {
                memberCounts.put(seed, members);
            }
        }
        if (memberCounts.isEmpty()) {
            LOG.info(
                    "the arrangement step is gated: no survivor was grouped under {}, so there is nothing"
                            + " to arrange.",
                    scoring.value());
            return RepeatStatus.FINISHED;
        }
        Map<OccurrenceId, String> seedPaths = new HashMap<>();
        for (OccurrenceId seed : memberCounts.keySet()) {
            seedPaths.put(seed, pathOf(seed));
        }
        long members = memberCounts.values().stream().mapToLong(Integer::longValue).sum();
        StageProgress scoresRead = StageProgress.over("Stage 6a (arrangement, scores read)", members);
        StageProgress gathered = StageProgress.over("Stage 6a (arrangement, members gathered)", members);

        RunId arrangement = stageRuns.arrangement();

        // The page an approval copies its name off is written every time this invocation arrives at an
        // arrangement, from the rows recorded under it, including when they were recorded by an
        // earlier invocation (ADR-154 §2, amending ADR-115's "a skipped step writes no report" for this
        // one page) -- kept as its own alreadyRecorded action (ADR-157 §5).
        return TaskletSteps.once(
                ledger,
                arrangement,
                StepNames.ARRANGEMENT,
                () -> {
                    LOG.info("the arrangement step was already recorded under run {}", arrangement.value());
                    List<OccurrenceId> recordedSeeds = TimedStatement.of(
                            STAGE, "reading", "read", "the seed partitions of the arrangement",
                            () -> clusters.seedsOf(arrangement));
                    writePage(scoring, arrangement, recordedSeeds, seedPaths, scoresRead, gathered, false);
                },
                () -> clusters.discardForRun(arrangement),
                () -> {
                    List<OccurrenceId> seeds = Arrangement.inOrder(memberCounts, seedPaths);
                    int[] clustersAndDocuments = writePage(scoring, arrangement, seeds, seedPaths, scoresRead, gathered, true);
                    LOG.info(
                            "The arrangement step finished under {}: {} seed partition(s), {} cluster(s), {}"
                                    + " document(s)",
                            arrangement.value(),
                            seeds.size(),
                            clustersAndDocuments[0],
                            clustersAndDocuments[1]);
                    return true;
                });
    }

    /**
     * Writes the page an approval names, a partition at a time, to a file beside {@code arrangement.html} and
     * moves it over that file once it is whole, so that a step that fails partway leaves the page before it
     * or none (ADR-223 section 3). Where {@code recording}, each partition is arranged, labelled and recorded
     * first; either way its rows are read back before its part of the page is written, so what a reviewer is
     * shown, and an approval then names, is the same whether this invocation just wrote those rows or is only
     * rendering a page for a run an earlier invocation finished (ADR-112, ADR-115, ADR-154 §2).
     *
     * @return how many clusters were arranged and how many documents they hold, both nothing where not
     *     {@code recording}
     */
    private int[] writePage(
            RunId scoring,
            RunId arrangement,
            List<OccurrenceId> seeds,
            Map<OccurrenceId, String> seedPaths,
            StageProgress scoresRead,
            StageProgress gathered,
            boolean recording) {
        Path file = workingDirectory.resolve(ARRANGEMENT_FILE_NAME);
        Path part = workingDirectory.resolve(ARRANGEMENT_FILE_NAME + ".part");
        int[] arranged = new int[2];
        try {
            Files.createDirectories(workingDirectory);
            try (Writer page = Files.newBufferedWriter(part, StandardCharsets.UTF_8)) {
                ArrangementReport.open(
                        page, ArrangementGate.shortNameOf(arrangement), Walk.canonicalRoot(root).toString());
                StageProgress partitionsDrawn =
                        StageProgress.over("Stage 6a (arrangement, page partitions)", seeds.size());
                int of = seeds.size();
                for (int place = 1; place <= of; place++) {
                    OccurrenceId seed = seeds.get(place - 1);
                    String whichPartition = "partition " + place + " of " + seeds.size();
                    List<DocumentCluster> read = TimedStatement.of(
                            STAGE, "reading", "read", "the members of " + whichPartition,
                            () -> documentClusters.membersOf(scoring, seed));
                    Map<OccurrenceId, Double> scores = relevanceScoring.scoresFor(
                            scoring, read.stream().map(DocumentCluster::occurrenceId).toList(), scoresReadProgress(scoresRead));
                    List<ClusteredDocument> documents = clusteredDocuments(
                            read, scores, seedPaths.computeIfAbsent(seed, this::pathOf), gathered);

                    Map<ClusterSlot, OccurrenceId> leads = new HashMap<>();
                    if (recording) {
                        Partition gatheredPartition = Arrangement.partitionsOf(documents).getFirst();
                        List<ArrangedCluster> ordered = Arrangement.order(gatheredPartition, place);
                        StageProgress labelledAndRecorded = StageProgress.over(
                                "Stage 6a (arrangement, clusters, partition " + place + " of " + of + ")",
                                ordered.size());
                        for (ArrangedCluster cluster : ordered) {
                            LabelledCluster labelled =
                                    LeadDocument.labelled(cluster, documents, this::titleOf, this::pathObjectOf);
                            clusters.record(arrangement, cluster, labelled.label());
                            leads.put(new ClusterSlot(cluster.winningSeed(), cluster.ordinal()), labelled.leadDocument());
                            arranged[0]++;
                            arranged[1] += cluster.documentCount();
                            labelledAndRecorded.itemDone();
                        }
                    }
                    List<RecordedCluster> recorded = TimedStatement.of(
                            STAGE, "reading", "read", "the recorded clusters of " + whichPartition,
                            () -> clusters.ofPartition(arrangement, seed));
                    ArrangementReport.partition(
                            page,
                            new ArrangementReport.Partition(
                                    seedPaths.get(seed), rowsOf(recorded, leads, documents, place, seeds.size())));
                    partitionsDrawn.itemDone();
                }
                ArrangementReport.close(page, seeds.size());
            }
            Files.move(part, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + file, e);
        }
        return arranged;
    }

    /**
     * One partition's clusters as the page shows them, read back off the rows recorded under this run
     * (ADR-112, ADR-154 §2) rather than off the arithmetic that produced them.
     *
     * <p>Each cluster's lead is the one {@link LeadDocument#labelled} found while the cluster was being
     * labelled where this invocation arranged it, and {@link LeadDocument#of} where the rows were
     * recorded earlier (ADR-106, ADR-190).
     */
    private List<ArrangementReport.Cluster> rowsOf(
            List<RecordedCluster> recordedClusters,
            Map<ClusterSlot, OccurrenceId> kept,
            List<ClusteredDocument> documents,
            int place,
            int of) {
        List<ArrangementReport.Cluster> rows = new ArrayList<>();
        StageProgress rowsDrawn = StageProgress.over(
                "Stage 6a (arrangement, page rows, partition " + place + " of " + of + ")",
                recordedClusters.size());
        for (RecordedCluster recorded : recordedClusters) {
            ArrangedCluster cluster = recorded.cluster();
            OccurrenceId lead = kept.get(ClusterSlot.of(recorded));
            if (lead == null) {
                lead = LeadDocument.of(cluster, documents);
            }
            rows.add(new ArrangementReport.Cluster(
                    recorded.label().value(), cluster.documentCount(), pathOf(lead), linkTo(lead)));
            rowsDrawn.itemDone();
        }
        return rows;
    }

    /**
     * One partition's membership rows as plain values {@code synthesis} can gather into the clusters it
     * orders (ADR-110) — the score joined on here, because only this module may read it.
     *
     * <p>A survivor that carries no score is passed through as such rather than dropped: whether that
     * is something the arrangement can survive is {@code synthesis}'s rule, and it refuses.
     */
    private static List<ClusteredDocument> clusteredDocuments(
            List<DocumentCluster> membership,
            Map<OccurrenceId, Double> scores,
            String seedPath,
            StageProgress gathered) {
        List<ClusteredDocument> documents = new ArrayList<>(membership.size());
        for (DocumentCluster member : membership) {
            documents.add(new ClusteredDocument(
                    member.occurrenceId(),
                    member.winningSeedOccurrenceId(),
                    seedPath,
                    member.clusterOrdinal(),
                    scores.get(member.occurrenceId())));
            gathered.itemDone();
        }
        return List.copyOf(documents);
    }

    /** {@code embedding} tells this stage each score read; this stage owns the line, and the total is the run's. */
    private static ScoringProgress scoresReadProgress(StageProgress read) {
        return new ScoringProgress() {
            @Override
            public void scoreRead() {
                read.itemDone();
            }
        };
    }

    /**
     * The lead document's own title, read out of the conversion {@code extraction} already cached for
     * it (ADR-106).
     *
     * <p>Resolved here because only {@code pipeline} may name both modules — {@code synthesis} is handed
     * the string (ADR-110). It costs no conversion and opens no file: the response is in the cache, keyed
     * by the content hash stage 2 recorded for the document (ADR-206), which is read from there.
     */
    private Optional<String> titleOf(OccurrenceId occurrenceId) {
        return documentTitles.forContentHash(
                cacheKeys.requireForOccurrence(occurrenceId, stageRuns.upstream(StageModules.EXTRACTION)));
    }

    /**
     * Where a document actually is, as something a reader's browser can open (ADR-104): the recorded
     * corpus root joined to the occurrence's root-relative path, rendered as an absolute {@code file:}
     * target.
     *
     * <p>Nothing is stat-ed. A link that has gone dead because the archive moved is a fact about the
     * archive, and a page that quietly dropped such a link would be hiding the document rather than
     * reporting it.
     */
    private String linkTo(OccurrenceId occurrenceId) {
        return Walk.canonicalRoot(root)
                .resolve(pathObjectOf(occurrenceId).value())
                .toUri()
                .toString();
    }

    private String pathOf(OccurrenceId occurrenceId) {
        return pathObjectOf(occurrenceId).value();
    }

    private io.algernon.vespera.ledger.OccurrencePath pathObjectOf(OccurrenceId occurrenceId) {
        Optional<OccurrenceFacts> facts = ledger.occurrences().factsFor(occurrenceId);
        return facts.map(OccurrenceFacts::path)
                .orElseThrow(() -> new IllegalStateException(
                        "no facts recorded for occurrence " + occurrenceId.value()));
    }
}
