package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.similarity.RedundancyResolution;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Stage 4's corpus-wide half: candidate generation, exact scoring, component resolution and verdict
 * writing over rows {@code redundancySignatureStep} already stored (ADR-079, ADR-081, ADR-082) — a
 * tasklet, like stage 3's own corpus-wide pass, since this work is one traversal over already-written
 * rows rather than a per-item conversion.
 *
 * <p>Checks {@link RedundancyGate#floor()} itself, before reaching {@link StageRuns} or {@link
 * RedundancyBoilerplate} through its provider — the gate's own mechanism (#75's hand-off comment):
 * with the floor unset, this step logs which key is missing and where its data lives, and completes
 * having minted nothing.
 */
@Component
class RedundancyResolutionTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(RedundancyResolutionTasklet.class);

    private final RedundancyGate redundancyGate;
    private final StageRuns stageRuns;
    private final ObjectProvider<RedundancyBoilerplate> redundancyBoilerplateProvider;
    private final RedundancyResolution redundancyResolution;
    private final Ledger ledger;

    RedundancyResolutionTasklet(
            RedundancyGate redundancyGate,
            StageRuns stageRuns,
            ObjectProvider<RedundancyBoilerplate> redundancyBoilerplateProvider,
            RedundancyResolution redundancyResolution,
            Ledger ledger) {
        this.redundancyGate = redundancyGate;
        this.stageRuns = stageRuns;
        this.redundancyBoilerplateProvider = redundancyBoilerplateProvider;
        this.redundancyResolution = redundancyResolution;
        this.ledger = ledger;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        if (redundancyGate.floor().isEmpty()) {
            // Silent on purpose (#135). Stage 4a's reader has already said what this gate wants, and
            // one gate with one missing value and one action was printing its whole paragraph twice in
            // a row -- once here and once there -- which reads as two problems.
            return RepeatStatus.FINISHED;
        }

        RunId runId = stageRuns.contentRedundancy();
        RunId stage3RunId = stageRuns.upstream(StageModules.CONTENT_CENSUS);
        RunId extractionRunId = stageRuns.upstream(StageModules.EXTRACTION);

        return TaskletSteps.once(
                ledger,
                runId,
                StepNames.CONTENT_REDUNDANCY,
                // redundancy-signature, the step before it, is not asked: the two share a run but each
                // answers only for itself.
                () -> LOG.info("Stage 4b (redundancy resolution) was already recorded under run {}", runId.value()),
                () -> {
                    ledger.discardVerdicts(runId, VerdictKind.REDUNDANT_WITH);
                    redundancyResolution.discardForRun(runId);
                },
                () -> {
                    LOG.info("Stage 4b (redundancy resolution) starting under run {}", runId.value());
                    Set<Long> boilerplateHashes = redundancyBoilerplateProvider.getObject().hashes();
                    redundancyResolution.resolve(runId, stage3RunId, extractionRunId, boilerplateHashes);
                    LOG.info("Stage 4b (redundancy resolution) finished under run {}", runId.value());
                    return true;
                });
    }
}
