package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.DocumentCluster;
import io.algernon.vespera.embedding.DocumentClusters;
import io.algernon.vespera.embedding.RelevanceScoring;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.synthesis.ArrangedCluster;
import io.algernon.vespera.synthesis.Arrangement;
import io.algernon.vespera.synthesis.Cluster;
import io.algernon.vespera.synthesis.ClusterLabel;
import io.algernon.vespera.synthesis.Clusters;
import io.algernon.vespera.synthesis.Partition;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.springframework.beans.factory.ObjectProvider;
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
 * <p><b>The arrangement is total, and this asserts it.</b> Every survivor has a score, a winning seed
 * and a cluster ordinal, so a cluster whose members carry no score is a broken invariant rather than a
 * case to accommodate — it stops, in the same way and for the same reason {@code scoreAndRecord}
 * refuses to score an unvectored survivor. A defensive miscellaneous bucket would turn that into a
 * silently rendered section of the deliverable.
 *
 * <p>Gated exactly as the clustering step before it is, and for the same reasons: with no model
 * named, no seed folder or no usable seed, no score exists, so there is nothing to arrange.
 */
@Component
@StepScope
class ArrangementTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(ArrangementTasklet.class);

    private final EmbeddingModelGate embeddingModelGate;
    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final ObjectProvider<ScoringRun> scoringRun;
    private final ObjectProvider<ArrangementRun> arrangementRun;
    private final DocumentClusters documentClusters;
    private final RelevanceScoring relevanceScoring;
    private final Clusters clusters;
    private final Ledger ledger;

    ArrangementTasklet(
            EmbeddingModelGate embeddingModelGate,
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            ObjectProvider<ScoringRun> scoringRun,
            ObjectProvider<ArrangementRun> arrangementRun,
            DocumentClusters documentClusters,
            RelevanceScoring relevanceScoring,
            Clusters clusters,
            Ledger ledger) {
        this.embeddingModelGate = embeddingModelGate;
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.scoringRun = scoringRun;
        this.arrangementRun = arrangementRun;
        this.documentClusters = documentClusters;
        this.relevanceScoring = relevanceScoring;
        this.clusters = clusters;
        this.ledger = ledger;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (embeddingModelGate.modelName().isEmpty()) {
            LOG.info("the arrangement step is gated: no embedding model is named. Nothing was arranged.");
            return RepeatStatus.FINISHED;
        }
        if (seedGate.seedWalk().isEmpty()) {
            LOG.info("the arrangement step is gated: no seed folder is named, or stage 4's gate is shut,"
                    + " or the seed walk has not finished. Nothing was arranged.");
            return RepeatStatus.FINISHED;
        }
        if (!usableSeedGate.anySeedUsable()) {
            LOG.info("the arrangement step is gated: no seed document produced any text, so no survivor"
                    + " carries a winning seed to be partitioned by. Nothing was arranged.");
            return RepeatStatus.FINISHED;
        }

        RunId scoring = scoringRun.getObject().runId();
        List<DocumentCluster> membership = documentClusters.forRun(scoring);
        if (membership.isEmpty()) {
            LOG.info(
                    "the arrangement step is gated: no survivor was grouped under {}, so there is nothing"
                            + " to arrange.",
                    scoring.value());
            return RepeatStatus.FINISHED;
        }

        Map<OccurrenceId, Double> scores = relevanceScoring.scoresFor(
                scoring, membership.stream().map(DocumentCluster::occurrenceId).toList());
        List<Partition> partitions = partitionsOf(membership, scores);

        RunId arrangement = arrangementRun.getObject().runId();
        List<ArrangedCluster> arranged = Arrangement.order(partitions);
        for (ArrangedCluster cluster : arranged) {
            clusters.record(arrangement, cluster, labelFor(cluster, membership, scores));
        }
        LOG.info(
                "The arrangement step finished under {}: {} seed partition(s), {} cluster(s), {}"
                        + " document(s)",
                arrangement.value(),
                partitions.size(),
                arranged.size(),
                arranged.stream().mapToInt(ArrangedCluster::documentCount).sum());
        return RepeatStatus.FINISHED;
    }

    /** Stage 5's membership rows, gathered into the two levels the arrangement orders. */
    private List<Partition> partitionsOf(List<DocumentCluster> membership, Map<OccurrenceId, Double> scores) {
        Map<OccurrenceId, Map<Integer, List<Double>>> bySeedThenOrdinal = new LinkedHashMap<>();
        for (DocumentCluster member : membership) {
            Double score = scores.get(member.occurrenceId());
            if (score == null) {
                // Every survivor carries a score by the time it carries a cluster. One that does not is a
                // broken invariant, and a miscellaneous bucket would render it as a section of the
                // deliverable rather than as something someone reads about.
                throw new IllegalStateException("occurrence " + member.occurrenceId().value()
                        + " was grouped but carries no relevance score, so the arrangement is not total");
            }
            bySeedThenOrdinal
                    .computeIfAbsent(member.winningSeedOccurrenceId(), seed -> new LinkedHashMap<>())
                    .computeIfAbsent(member.clusterOrdinal(), ordinal -> new ArrayList<>())
                    .add(score);
        }
        List<Partition> partitions = new ArrayList<>();
        bySeedThenOrdinal.forEach((seed, byOrdinal) -> {
            List<Cluster> members = byOrdinal.entrySet().stream()
                    .map(entry -> new Cluster(entry.getKey(), entry.getValue()))
                    .toList();
            partitions.add(new Partition(seed, pathOf(seed), members));
        });
        return partitions;
    }

    /**
     * The label for one arranged cluster: derived from the cluster's own highest-scoring document
     * (ADR-106), which is looked up here because only {@code pipeline} may read the scores and the
     * occurrence facts the rule needs.
     */
    private ClusterLabel labelFor(
            ArrangedCluster cluster, List<DocumentCluster> membership, Map<OccurrenceId, Double> scores) {
        OccurrenceId lead = membership.stream()
                .filter(member -> member.winningSeedOccurrenceId().equals(cluster.winningSeed()))
                .filter(member -> member.clusterOrdinal() == cluster.ordinal())
                .map(DocumentCluster::occurrenceId)
                .max(Comparator.comparingDouble(occurrence -> scores.getOrDefault(occurrence, 0.0)))
                .orElseThrow(() -> new IllegalStateException(
                        "cluster " + cluster.ordinal() + " was arranged with no members"));
        return ClusterLabel.derivedFrom(null, pathObjectOf(lead), cluster.ordinal());
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
