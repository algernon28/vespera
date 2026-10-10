package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.FloorReach;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.RelevanceScoring;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Stage 5's fifth step (ADR-088, #112): the last removal in the cascade. With a threshold set on
 * this run's own scale, a survivor scoring below it earns {@code below-threshold}, under the scoring
 * run its score was written beneath.
 *
 * <p><b>This is not a gate.</b> {@link RedundancyGate} stops stage 4 because a boilerplate floor is
 * an input that stage cannot work without; the relevance threshold is the opposite, since this run
 * is what produces the data the threshold is calibrated from. Gating on it would mean never
 * producing the report that lets anyone set it. So the step runs whatever {@link
 * RelevanceFloor} says the floor lets it do and finishes in each case; what changes is only whether a
 * verdict is written.
 *
 * <p><b>It runs before clustering, and that ordering is the whole of ADR-087's "costs nothing".</b>
 * A document removed here is not a survivor by the time the clustering step reads the partition, so
 * it is never clustered, never given an ordinal, and never occupies a page. Ordered the other way the
 * run would cluster documents it was about to remove. Clustering follows a later change of this step's
 * decision too: it forms its clusters again where they are not of the survivors as they now stand (ADR-230).
 *
 * <p><b>The number is never written back.</b> Nothing here touches the profile: a threshold the
 * engine wrote would break the rule that the profile is authored by a person and never guessed at,
 * and ADR-062's census that never touches an existing value.
 *
 * <p><b>There is no {@code finishStep} call in this class.</b> ADR-118 names this step and {@code
 * relevance-report} as the only two that read the answers a person gave, and takes them out of
 * ADR-116's list for the reason ADR-116's own governing clause gives: a completion record is safe only
 * where a run's identity names everything the step consumes. Answers are keyed by path and seed set
 * (ADR-097) and no run names them -- deliberately, since a run id that moved when someone answered the
 * page would re-score the corpus in reply to its own question. So this step decides again on every
 * invocation, which is what lets an answer given between two of them take effect at all.
 */
@Component
@StepScope
class RelevanceFloorTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(RelevanceFloorTasklet.class);

    /** Stage 5e's own name, which its statement lines open with. */
    private static final String STAGE = "Stage 5e (relevance floor)";

    private final EmbeddingModelGate embeddingModelGate;
    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final StageRuns stageRuns;
    private final RelevanceFloor relevanceFloor;
    private final RelevanceScoring relevanceScoring;
    private final RelevanceDistribution relevanceDistribution;
    private final Ledger ledger;

    RelevanceFloorTasklet(
            EmbeddingModelGate embeddingModelGate,
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            StageRuns stageRuns,
            RelevanceFloor relevanceFloor,
            RelevanceScoring relevanceScoring,
            RelevanceDistribution relevanceDistribution,
            Ledger ledger) {
        this.embeddingModelGate = embeddingModelGate;
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.stageRuns = stageRuns;
        this.relevanceFloor = relevanceFloor;
        this.relevanceScoring = relevanceScoring;
        this.relevanceDistribution = relevanceDistribution;
        this.ledger = ledger;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        StageFiveGates.Preamble preamble = StageFiveGates.modelSeedWalkUsable(
                "stage 5's relevance-floor step", embeddingModelGate, seedGate, usableSeedGate);
        if (!preamble.isOpen()) {
            LOG.info(preamble.shutSentence().orElseThrow());
            return RepeatStatus.FINISHED;
        }
        String modelName = preamble.modelName().orElseThrow();

        RunId scoring = stageRuns.embeddingScoring();

        // This run's own scale, not a stamp over every model the database has held: comparing a
        // threshold against the wrong identity is what would let a number calibrated elsewhere remove
        // documents here. Not yet a decision this run can be finished on -- a later invocation, once
        // embedding-scoring has actually run, may answer differently under this very run id.
        Optional<String> currentIdentity =
                TimedStatement.of(STAGE, "reading", "read", "the embedder identities", () -> relevanceDistribution.embedderIdentityFor(modelName));
        FloorReach reach = relevanceFloor.reachFor(currentIdentity, STAGE);

        // Every removal this run has standing goes before the reach is acted on, whichever way it
        // turns out, and whether or not the vectors carry one identity (ADR-118, ADR-227). The answers
        // decide this step and no run names them, so the decision can turn either way between two
        // invocations: a threshold that became applicable removes documents, and one that stopped being
        // applicable must withdraw the removals it already made. Discarding only where it applies would
        // keep the harsher half of that.
        ledger.verdicts().discardVerdicts(scoring, VerdictKind.BELOW_THRESHOLD);

        // A removal is written where the reach gives a number and on no other condition (ADR-227): the
        // identity read above only chooses which line says why nothing was removed.
        if (reach.removesBelow().isPresent()) {
            double threshold = reach.removesBelow().getAsDouble();
            // Counted, then written a page at a time: nothing holds every occurrence below the floor
            // (ADR-220 section 5). The verdicts of a page go in the step's one transaction before the
            // next page is read.
            long below = TimedStatement.of(
                    STAGE, "counting", "counted", "the scores below the floor",
                    () -> relevanceScoring.countScoredBelow(scoring, threshold));
            StageProgress written =
                    StageProgress.over("Stage 5e (relevance floor, below-threshold verdicts)", below);
            relevanceScoring.eachPageScoredBelow(scoring, threshold, page -> {
                for (OccurrenceId occurrenceId : page) {
                    ledger.verdicts().verdict(occurrenceId, scoring, VerdictKind.BELOW_THRESHOLD, FloorReach.REASON);
                    written.itemDone();
                }
            });
            LOG.info(
                    "Stage 5e (relevance floor) finished under scoring run {}: threshold {}, {}"
                            + " survivor(s) removed as below-threshold",
                    scoring.value(),
                    threshold,
                    below);
        } else if (currentIdentity.isEmpty()) {
            LOG.info(
                    "stage 5's relevance-floor step removed nothing: the vectors under {} carry no single"
                            + " embedder identity, so there is no one scale for a threshold to be on. A"
                            + " threshold is only applied where the scale it was read off is known to be"
                            + " this one.",
                    modelName);
        } else if (reach.floor().isEmpty()) {
            LOG.info("stage 5's relevance-floor step removed nothing: no relevance threshold is set."
                    + " Every scored survivor stands, and the labelling report is what a person reads"
                    + " to choose the number.");
        } else {
            LOG.info(
                    "stage 5's relevance-floor step removed nothing: the threshold {} was read off"
                            + " labels given under {}, and this run scored under {}. A threshold is a"
                            + " number on a scale and the model is the scale, so applying it here would"
                            + " remove documents against a distribution it was never calibrated on. The"
                            + " labelling report says so too.",
                    reach.floor().getAsDouble(),
                    String.join(", ", reach.answeredUnder()),
                    currentIdentity.get());
        }
        return RepeatStatus.FINISHED;
    }
}
