package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.Clustering;
import io.algernon.vespera.embedding.RetainedEdgeSpread;
import io.algernon.vespera.embedding.DocumentClusters;
import io.algernon.vespera.extraction.ChunkingRule;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.HybridChunker;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
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
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stage 5's fifth step (ADR-087, #109): each seed partition's survivors grouped into clusters, under
 * the same scoring run the scores were written beneath.
 *
 * <p><b>One partition at a time, never corpus-wide (ADR-045).</b> A partition is the set of survivors
 * one seed won, which the step before this one already recorded, so the work is bounded to N²/2
 * comparisons per partition rather than over the whole corpus — and within a partition it is bounded
 * again, to the two blocks of vectors {@link Clustering} streams past one another.
 *
 * <p><b>Nothing here removes anything.</b> No verdict is written, no document is left unclustered and
 * no cluster is merged into another: clustering arranges what survived, and the arrangement is a
 * measurement like any other (ADR-077 — a re-run writes its own row set rather than editing this
 * one).
 *
 * <p>It ends by writing the size distribution, because the cluster count is not chosen and therefore
 * cannot be known in advance: whoever builds the page tree above these clusters needs to see what
 * shape they came out in before they build it.
 *
 * <p>Gated exactly as the scoring step before it is, and for the same reasons: with no model named,
 * no seed folder, or no usable seed, no score exists, so there is no partition to cluster.
 */
@Component
@StepScope
class ClusteringTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(ClusteringTasklet.class);

    /** The size report's name in the working directory, beside the profile and the database (ADR-054). */
    static final String CLUSTER_SIZES_FILE_NAME = "cluster-sizes.html";

    private final EmbeddingModelGate embeddingModelGate;
    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final ObjectProvider<ScoringRun> scoringRun;
    private final Ledger ledger;
    private final DoclingExtractor extractor;
    private final HybridChunker hybridChunker;
    private final Clustering clustering;
    private final DocumentClusters documentClusters;
    private final Path root;
    private final Path workingDirectory;

    ClusteringTasklet(
            EmbeddingModelGate embeddingModelGate,
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            ObjectProvider<ScoringRun> scoringRun,
            Ledger ledger,
            DoclingExtractor extractor,
            HybridChunker hybridChunker,
            Clustering clustering,
            DocumentClusters documentClusters,
            @Value("#{jobParameters['root']}") Path root,
            @Value("${vespera.working-dir}") Path workingDirectory) {
        this.embeddingModelGate = embeddingModelGate;
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.scoringRun = scoringRun;
        this.ledger = ledger;
        this.extractor = extractor;
        this.hybridChunker = hybridChunker;
        this.clustering = clustering;
        this.documentClusters = documentClusters;
        this.root = root;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Optional<String> modelName = embeddingModelGate.modelName();
        if (modelName.isEmpty()) {
            LOG.info("stage 5's clustering step is gated: no embedding model is named. Nothing was"
                    + " clustered.");
            return RepeatStatus.FINISHED;
        }
        Optional<SeedGate.SeedWalk> seedWalk = seedGate.seedWalk();
        if (seedWalk.isEmpty()) {
            LOG.info("stage 5's clustering step is gated: no seed folder is named, or stage 4's gate is"
                    + " shut, or the seed walk has not finished. Nothing was clustered.");
            return RepeatStatus.FINISHED;
        }
        if (!usableSeedGate.anySeedUsable()) {
            LOG.info("stage 5's clustering step is gated: no seed document produced any text, so no"
                    + " survivor carries a winning seed to be partitioned by. Nothing was clustered.");
            return RepeatStatus.FINISHED;
        }

        ScoringRun scoring = scoringRun.getObject();
        List<OccurrenceId> partitions = clustering.partitions(scoring.runId());
        if (partitions.isEmpty()) {
            LOG.info(
                    "stage 5's clustering step is gated: no survivor carries a relevance score under {},"
                            + " so there is no partition to cluster. Nothing was clustered.",
                    scoring.runId().value());
            return RepeatStatus.FINISHED;
        }

        Path canonicalRoot = Walk.canonicalRoot(root);
        String chunkerIdentity = hybridChunker.identity();
        String chunkingRuleIdentity = ChunkingRule.DEFAULT.identity().value();

        // The survivor set as it stands after the floor step, drained once: a document removed as
        // below-threshold still carries the score row that put it in a partition, and clustering it
        // would give a page to a document this run has just decided is not in the archive. ADR-060
        // keeps the verdict join in the ledger, so the filter is here rather than in the partition
        // query embedding owns.
        Set<OccurrenceId> survivors =
                ItemStreamReaders.drain(ledger.survivors(scoring.runId()));

        LOG.info(
                "Stage 5f (clustering) starting under scoring run {}: {} seed partition(s) to group",
                scoring.runId().value(),
                partitions.size());
        List<ClusterSizeReport.Partition> reported = new ArrayList<>();
        for (OccurrenceId winningSeed : partitions) {
            List<OccurrenceId> members = clustering.membersOf(scoring.runId(), winningSeed).stream()
                    .filter(survivors::contains)
                    .toList();
            if (members.isEmpty()) {
                // Every document this seed won was removed by the floor. A partition of nothing is not
                // a partition, and a heading with no page under it is not worth minting.
                continue;
            }
            // The spread of the kept edges comes back from the pass that built the graph, because that
            // is the only place the similarities exist: recovering them afterwards would mean the
            // N-squared-over-two pass a second time (ADR-096). Nothing reads it but the page.
            Optional<RetainedEdgeSpread> spread = clustering.clusterAndRecord(
                    scoring.runId(),
                    winningSeed,
                    contentHashesOf(canonicalRoot, members),
                    chunkerIdentity,
                    chunkingRuleIdentity,
                    modelName.get());
            // Read back rather than returned from the pass: a cluster exists as the set of rows carrying
            // its identity, so the sizes a reader is shown are the rows, not what the arithmetic meant to
            // write.
            reported.add(new ClusterSizeReport.Partition(
                    pathOf(winningSeed), documentClusters.sizesFor(scoring.runId(), winningSeed), spread));
        }

        write(CLUSTER_SIZES_FILE_NAME, ClusterSizeReport.render(reported));
        LOG.info(
                "Stage 5f (clustering) finished under scoring run {}: {} partition(s), {} document(s) in"
                        + " {} cluster(s)",
                scoring.runId().value(),
                reported.size(),
                reported.stream().mapToInt(ClusterSizeReport.Partition::documentCount).sum(),
                reported.stream().mapToInt(ClusterSizeReport.Partition::clusterCount).sum());
        return RepeatStatus.FINISHED;
    }

    /**
     * Each partition member's content hash, in the order clustering visits them.
     *
     * <p>Resolved here because only {@code pipeline} can reach a file, exactly as the scoring step
     * before it does. This is a hash per document rather than a vector per document, which is what
     * ADR-085's ceiling is about: it is what lets {@link Clustering} address a block without holding
     * the partition.
     */
    private Map<OccurrenceId, String> contentHashesOf(Path canonicalRoot, List<OccurrenceId> members) {
        Map<OccurrenceId, String> contentHashes = new LinkedHashMap<>();
        for (OccurrenceId member : members) {
            OccurrenceFacts facts = ledger.factsFor(member)
                    .orElseThrow(() -> new IllegalStateException(
                            "no facts recorded for occurrence " + member.value()));
            contentHashes.put(member, extractor.contentHashFor(canonicalRoot.resolve(facts.path().value())));
        }
        return contentHashes;
    }

    private String pathOf(OccurrenceId occurrenceId) {
        return ledger.factsFor(occurrenceId)
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
