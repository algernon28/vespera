package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DocumentTitles;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * <p>Gated exactly as the clustering step before it is, and for the same reasons: with no model
 * named, no seed folder or no usable seed, no score exists, so there is nothing to arrange.
 */
@Component
@StepScope
class ArrangementTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(ArrangementTasklet.class);

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
    private final DoclingExtractor extractor;
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
            DoclingExtractor extractor,
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
        this.extractor = extractor;
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
        List<DocumentCluster> membership = documentClusters.forRun(scoring);
        if (membership.isEmpty()) {
            LOG.info(
                    "the arrangement step is gated: no survivor was grouped under {}, so there is nothing"
                            + " to arrange.",
                    scoring.value());
            return RepeatStatus.FINISHED;
        }

        RunId arrangement = stageRuns.arrangement();
        Map<OccurrenceId, Double> scores = relevanceScoring.scoresFor(
                scoring, membership.stream().map(DocumentCluster::occurrenceId).toList(), scoresReadProgress());

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
                    write(ARRANGEMENT_FILE_NAME, ArrangementReport.render(
                            ArrangementGate.shortNameOf(arrangement),
                            Walk.canonicalRoot(root).toString(),
                            reportOf(clusters.forRun(arrangement), Map.of(), clusteredDocuments(membership, scores))));
                },
                () -> clusters.discardForRun(arrangement),
                () -> {
                    List<ClusteredDocument> documents = clusteredDocuments(membership, scores);
                    List<Partition> partitions = Arrangement.partitionsOf(documents);

                    List<ArrangedCluster> arranged = Arrangement.order(partitions);
                    Map<ClusterSlot, OccurrenceId> leads = new HashMap<>();
                    StageProgress labelledAndRecorded =
                            StageProgress.over("Stage 6a (arrangement, clusters)", arranged.size());
                    for (ArrangedCluster cluster : arranged) {
                        LabelledCluster labelled =
                                LeadDocument.labelled(cluster, documents, this::titleOf, this::pathObjectOf);
                        clusters.record(arrangement, cluster, labelled.label());
                        leads.put(new ClusterSlot(cluster.winningSeed(), cluster.ordinal()), labelled.leadDocument());
                        labelledAndRecorded.itemDone();
                    }
                    write(ARRANGEMENT_FILE_NAME, ArrangementReport.render(
                            ArrangementGate.shortNameOf(arrangement),
                            Walk.canonicalRoot(root).toString(),
                            reportOf(clusters.forRun(arrangement), leads, documents)));
                    LOG.info(
                            "The arrangement step finished under {}: {} seed partition(s), {} cluster(s), {}"
                                    + " document(s)",
                            arrangement.value(),
                            partitions.size(),
                            arranged.size(),
                            arranged.stream().mapToInt(ArrangedCluster::documentCount).sum());
                    return true;
                });
    }

    /**
     * The arrangement as the page shows it, read back off the rows recorded under this run (ADR-112,
     * ADR-154 §2) rather than off the arithmetic that produced them — so what a reviewer is shown, and
     * an approval then names, is the same whether this invocation just wrote those rows or is only
     * rendering a page for a run an earlier invocation finished (ADR-115, ADR-154 §2).
     *
     * <p>Each cluster's lead is the one {@link LeadDocument#labelled} found while the cluster was being
     * labelled where this invocation arranged it, and {@link LeadDocument#of} where the rows were
     * recorded earlier (ADR-106, ADR-190).
     */
    private List<ArrangementReport.Partition> reportOf(
            List<RecordedCluster> recordedClusters,
            Map<ClusterSlot, OccurrenceId> kept,
            List<ClusteredDocument> documents) {
        Map<OccurrenceId, List<ArrangementReport.Cluster>> bySeed = new LinkedHashMap<>();
        StageProgress rowsDrawn = StageProgress.over("Stage 6a (arrangement, page rows)", recordedClusters.size());
        for (RecordedCluster recorded : recordedClusters) {
            ArrangedCluster cluster = recorded.cluster();
            OccurrenceId lead = kept.get(ClusterSlot.of(recorded));
            if (lead == null) {
                lead = LeadDocument.of(cluster, documents);
            }
            bySeed.computeIfAbsent(cluster.winningSeed(), seed -> new ArrayList<>())
                    .add(new ArrangementReport.Cluster(
                            recorded.label().value(), cluster.documentCount(), pathOf(lead), linkTo(lead)));
            rowsDrawn.itemDone();
        }
        List<ArrangementReport.Partition> partitions = new ArrayList<>();
        StageProgress partitionsDrawn = StageProgress.over("Stage 6a (arrangement, page partitions)", bySeed.size());
        bySeed.forEach((seed, clusters) -> {
            partitions.add(new ArrangementReport.Partition(pathOf(seed), clusters));
            partitionsDrawn.itemDone();
        });
        return partitions;
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

    /**
     * Stage 5's membership rows as plain values {@code synthesis} can gather into the two levels it
     * orders (ADR-110) — the score joined on here, because only this module may read it.
     *
     * <p>A survivor that carries no score is passed through as such rather than dropped: whether that
     * is something the arrangement can survive is {@code synthesis}'s rule, and it refuses.
     */
    private List<ClusteredDocument> clusteredDocuments(
            List<DocumentCluster> membership, Map<OccurrenceId, Double> scores) {
        Map<OccurrenceId, String> seedPaths = new LinkedHashMap<>();
        StageProgress gathered = StageProgress.over("Stage 6a (arrangement, members gathered)", membership.size());
        List<ClusteredDocument> documents = new ArrayList<>(membership.size());
        for (DocumentCluster member : membership) {
            documents.add(new ClusteredDocument(
                    member.occurrenceId(),
                    member.winningSeedOccurrenceId(),
                    seedPaths.computeIfAbsent(member.winningSeedOccurrenceId(), this::pathOf),
                    member.clusterOrdinal(),
                    scores.get(member.occurrenceId())));
            gathered.itemDone();
        }
        return List.copyOf(documents);
    }

    /** {@code embedding} tells this stage the total and each score read; this stage owns the line. */
    private static ScoringProgress scoresReadProgress() {
        return new ScoringProgress() {
            private StageProgress read;

            @Override
            public void toReadScores(long occurrences) {
                read = StageProgress.over("Stage 6a (arrangement, scores read)", occurrences);
            }

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
     * <p>Resolved here because only {@code pipeline} can reach a file and only it may name both
     * modules — {@code synthesis} is handed the string (ADR-110). It costs no conversion: the response
     * is in the cache, keyed by the content hash this resolves the same way every other step does.
     */
    private Optional<String> titleOf(OccurrenceId occurrenceId) {
        Path file = Walk.canonicalRoot(root).resolve(pathObjectOf(occurrenceId).value());
        return documentTitles.forContentHash(extractor.contentHashFor(file));
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
        Optional<OccurrenceFacts> facts = ledger.factsFor(occurrenceId);
        return facts.map(OccurrenceFacts::path)
                .orElseThrow(() -> new IllegalStateException(
                        "no facts recorded for occurrence " + occurrenceId.value()));
    }
}
