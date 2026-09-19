package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.RelevanceScoring;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.VerdictKind;
import java.util.List;
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
 * Stage 5's fifth step (ADR-088, #112): the last removal in the cascade. With a threshold set on
 * this run's own scale, a survivor scoring below it earns {@code below-threshold}, under the scoring
 * run its score was written beneath.
 *
 * <p><b>This is not a gate.</b> {@link RedundancyGate} stops stage 4 because a boilerplate floor is
 * an input that stage cannot work without; the relevance threshold is the opposite, since this run
 * is what produces the data the threshold is calibrated from. Gating on it would mean never
 * producing the report that lets anyone set it. So the step runs in all three of {@link
 * RelevanceFloor}'s states and finishes in each; what changes is only whether a verdict is written.
 *
 * <p><b>It runs before clustering, and that ordering is the whole of ADR-087's "costs nothing".</b>
 * A document removed here is not a survivor by the time the clustering step reads the partition, so
 * it is never clustered, never given an ordinal, and never occupies a page. Ordered the other way the
 * run would cluster documents it was about to remove.
 *
 * <p><b>The number is never written back.</b> Nothing here touches the profile: a threshold the
 * engine wrote would break the rule that the profile is authored by a person and never guessed at,
 * and ADR-062's census that never touches an existing value.
 */
@Component
@StepScope
class RelevanceFloorTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(RelevanceFloorTasklet.class);

    /**
     * The step's own name, which its own wiring builds it under. It is not the name of a completion
     * record, and there is no {@code finishStep} call in this class.
     *
     * <p>ADR-118 names this step and {@code relevance-report} as the only two that read the answers a
     * person gave, and takes them out of ADR-116's list for the reason ADR-116's own governing clause
     * gives: a completion record is safe only where a run's identity names everything the step
     * consumes. Answers are keyed by path and seed set (ADR-097) and no run names them -- deliberately,
     * since a run id that moved when someone answered the page would re-score the corpus in reply to
     * its own question. So this step decides again on every invocation, which is what lets an answer
     * given between two of them take effect at all.
     */
    static final String STEP = "relevance-floor";

    /**
     * What the verdict row records as its reason. It names the number and the scale it was read on,
     * because a removal a person is reading a year later has to say what it was measured against.
     */
    static final String REASON = "relevance score below the floor set in the profile";

    private final EmbeddingModelGate embeddingModelGate;
    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final ObjectProvider<ScoringRun> scoringRun;
    private final RelevanceFloor relevanceFloor;
    private final RelevanceScoring relevanceScoring;
    private final RelevanceDistribution relevanceDistribution;
    private final Ledger ledger;

    RelevanceFloorTasklet(
            EmbeddingModelGate embeddingModelGate,
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            ObjectProvider<ScoringRun> scoringRun,
            RelevanceFloor relevanceFloor,
            RelevanceScoring relevanceScoring,
            RelevanceDistribution relevanceDistribution,
            Ledger ledger) {
        this.embeddingModelGate = embeddingModelGate;
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.scoringRun = scoringRun;
        this.relevanceFloor = relevanceFloor;
        this.relevanceScoring = relevanceScoring;
        this.relevanceDistribution = relevanceDistribution;
        this.ledger = ledger;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Optional<String> modelName = embeddingModelGate.modelName();
        if (modelName.isEmpty()
                || seedGate.seedWalk().isEmpty()
                || !usableSeedGate.anySeedUsable()) {
            LOG.info("stage 5's relevance-floor step has nothing to apply a threshold to: no model is"
                    + " named, no seed folder is, or no seed produced text. Nothing was removed.");
            return RepeatStatus.FINISHED;
        }

        ScoringRun scoring = scoringRun.getObject();

        // This run's own scale, not a stamp over every model the database has held: comparing a
        // threshold against the wrong identity is what would let a number calibrated elsewhere remove
        // documents here. Not yet a decision this run can be finished on -- a later invocation, once
        // embedding-scoring has actually run, may answer differently under this very run id.
        Optional<String> currentIdentity = relevanceDistribution.embedderIdentityFor(modelName.get());
        if (currentIdentity.isEmpty()) {
            LOG.info(
                    "stage 5's relevance-floor step removed nothing: the vectors under {} carry no single"
                            + " embedder identity, so there is no one scale for a threshold to be on. A"
                            + " threshold is only applied where the scale it was read off is known to be"
                            + " this one.",
                    modelName.get());
            return RepeatStatus.FINISHED;
        }

        RelevanceFloor.State state = relevanceFloor.stateFor(currentIdentity.get());

        // Every removal this run has standing goes before the state is acted on, whichever way it
        // turns out (ADR-118). The answers decide this step and no run names them, so the decision can
        // turn either way between two invocations: a threshold that became applicable removes
        // documents, and one that stopped being applicable must withdraw the removals it already made.
        // Discarding only inside the applicable branch would keep the harsher half of that.
        ledger.discardVerdicts(scoring.runId(), VerdictKind.BELOW_THRESHOLD);

        switch (state) {
            case RelevanceFloor.Unset ignored -> {
                LOG.info("stage 5's relevance-floor step removed nothing: no relevance threshold is set."
                        + " Every scored survivor stands, and the labelling report is what a person reads"
                        + " to choose the number.");
            }
            case RelevanceFloor.CalibratedElsewhere elsewhere -> LOG.info(
                    "stage 5's relevance-floor step removed nothing: the threshold {} was read off"
                            + " labels given under {}, and this run scored under {}. A threshold is a"
                            + " number on a scale and the model is the scale, so applying it here would"
                            + " remove documents against a distribution it was never calibrated on. The"
                            + " labelling report says so too.",
                    elsewhere.value(),
                    elsewhere.calibratedUnder(),
                    elsewhere.currentIdentity());
            case RelevanceFloor.Applicable applicable -> {
                List<OccurrenceId> below = relevanceScoring.scoredBelow(scoring.runId(), applicable.value());
                for (OccurrenceId occurrenceId : below) {
                    ledger.verdict(occurrenceId, scoring.runId(), VerdictKind.BELOW_THRESHOLD, REASON);
                }
                LOG.info(
                        "Stage 5e (relevance floor) finished under scoring run {}: threshold {}, {}"
                                + " survivor(s) removed as below-threshold",
                        scoring.runId().value(),
                        applicable.value(),
                        below.size());
            }
        }
        return RepeatStatus.FINISHED;
    }
}
