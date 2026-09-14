package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import java.nio.file.Path;
import java.util.Optional;
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
 * Stage 6b's gate and its run (ADR-107, ADR-108, ADR-110, ADR-114, #179). Nothing is generated here
 * yet: this step opens the gate the operator's approval closes, mints the run everything 6b writes
 * will be recorded under, and stops.
 *
 * <p><b>A shut gate is not an error</b> (ADR-080's shape, applied a fifth time): the invocation ends,
 * the job succeeds, no run is minted and nothing is removed. Unset and naming-no-arrangement are one
 * outcome on purpose — in each case no arrangement has been approved, and a typo that quietly
 * generated over the latest arrangement instead would be the gate spending an approval it never got.
 *
 * <p><b>An ambiguous approval is not that.</b> A prefix matching two arrangements stops the run
 * rather than choosing one, which is ADR-099's rule for an ambiguous upstream and is deliberately not
 * caught here: the pipeline never guesses which arrangement a person meant.
 *
 * <p><b>It writes no verdict</b>, and neither will anything 6b adds beneath it. Generation removes
 * nothing: its one way of failing is a cluster fault recorded against the cluster (ADR-111), and a
 * verdict is about a document and removes one.
 */
@Component
@StepScope
class GenerationTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(GenerationTasklet.class);

    private final ArrangementGate arrangementGate;
    private final ObjectProvider<GenerationRun> generationRun;
    private final Ledger ledger;
    private final Path root;

    GenerationTasklet(
            ArrangementGate arrangementGate,
            ObjectProvider<GenerationRun> generationRun,
            Ledger ledger,
            @Value("#{jobParameters['root']}") Path root) {
        this.arrangementGate = arrangementGate;
        this.generationRun = generationRun;
        this.ledger = ledger;
        this.root = root;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Path canonicalRoot = Walk.canonicalRoot(root);
        Optional<WalkId> walk = ledger.finishedWalkFor(canonicalRoot);
        if (walk.isEmpty()) {
            LOG.info("the generation step is gated: no finished walk is recorded for {}. Nothing was"
                    + " generated.", canonicalRoot);
            return RepeatStatus.FINISHED;
        }

        // Deliberately outside any catch: an approval naming two arrangements stops the run.
        Optional<RunId> approved = arrangementGate.approvedArrangement(walk.get());
        if (approved.isEmpty()) {
            LOG.info("the generation step is gated: no arrangement of this corpus has been approved --"
                    + " arrangementApproved is unset, or names no arrangement of this walk. Nothing was"
                    + " generated.");
            return RepeatStatus.FINISHED;
        }

        RunId generation = generationRun.getObject().runId();
        LOG.info(
                "The generation step opened under {}, over the arrangement approved as {}",
                generation.value(),
                ArrangementGate.shortNameOf(approved.get()));
        return RepeatStatus.FINISHED;
    }
}
