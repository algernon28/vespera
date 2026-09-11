package io.algernon.vespera.pipeline;

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
 * <p>Checks {@link RedundancyGate#floor()} itself, before reaching for {@link RedundancyRun} or {@link
 * RedundancyBoilerplate} through their providers — the gate's own mechanism (#75's hand-off comment):
 * with the floor unset, this step logs which key is missing and where its data lives, and completes
 * having minted nothing.
 */
@Component
class RedundancyResolutionTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(RedundancyResolutionTasklet.class);

    private final RedundancyGate redundancyGate;
    private final ObjectProvider<RedundancyRun> redundancyRunProvider;
    private final ObjectProvider<RedundancyBoilerplate> redundancyBoilerplateProvider;
    private final RedundancyResolution redundancyResolution;

    RedundancyResolutionTasklet(
            RedundancyGate redundancyGate,
            ObjectProvider<RedundancyRun> redundancyRunProvider,
            ObjectProvider<RedundancyBoilerplate> redundancyBoilerplateProvider,
            RedundancyResolution redundancyResolution) {
        this.redundancyGate = redundancyGate;
        this.redundancyRunProvider = redundancyRunProvider;
        this.redundancyBoilerplateProvider = redundancyBoilerplateProvider;
        this.redundancyResolution = redundancyResolution;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (redundancyGate.floor().isEmpty()) {
            // Silent on purpose (#135). Stage 4a's reader has already said what this gate wants, and
            // one gate with one missing value and one action was printing its whole paragraph twice in
            // a row -- once here and once there -- which reads as two problems.
            return RepeatStatus.FINISHED;
        }

        RedundancyRun redundancyRun = redundancyRunProvider.getObject();
        LOG.info("Stage 4b (redundancy resolution) starting under run {}", redundancyRun.runId().value());
        Set<Long> boilerplateHashes = redundancyBoilerplateProvider.getObject().hashes();
        redundancyResolution.resolve(
                redundancyRun.runId(), redundancyRun.stage3RunId(), redundancyRun.extractionRunId(), boilerplateHashes);
        LOG.info("Stage 4b (redundancy resolution) finished under run {}", redundancyRun.runId().value());
        return RepeatStatus.FINISHED;
    }
}
