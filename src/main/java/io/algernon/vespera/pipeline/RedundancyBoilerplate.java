package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.similarity.BoilerplateShingles;
import java.util.Set;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.stereotype.Component;

/**
 * The boilerplate hash set stage 4's run computed against, resolved once per job execution and shared
 * by both steps — job-scoped for the same reason {@link StageRuns} is: resolving it is two SQL queries
 * ({@link BoilerplateShingles}), cheap enough once but wasteful to repeat for every one of a chunk
 * step's items.
 *
 * <p>Depends on {@link StageRuns} directly rather than through an {@code ObjectProvider}, and is itself
 * reached two ways: {@link RedundancyResolutionTasklet} asks an {@code ObjectProvider} for it, and {@link
 * RedundancySignatureItemWriter} takes it as a constructor argument. Either way what is handed over is
 * the job-scoped proxy, and the target, whose constructor asks {@link StageRuns} for stage 3's run and
 * the floor, is built at the first call
 * of {@link #hashes()} and not before. While the gate is closed neither caller makes that call: 4a's
 * reader yields nothing, so its writer is never asked to write a chunk, and 4b's tasklet returns before it
 * asks its provider. So the target is not built, and this class asks {@link StageRuns} for nothing, behind a shut gate.
 */
@Component
@JobScope
class RedundancyBoilerplate {

    private final Set<Long> hashes;

    /**
     * The read of the boilerplate shingles is a timed statement (ADR-193 sections 1 and 6): a one-row lookup
     * and the read of {@code shingle_document_frequency}, called once an invocation, wherever the bean is
     * first asked for. It names stage 4 with no letter, since 4a's writer and 4b's tasklet are each the first
     * to ask on some invocation, and both are stage 4's under one run.
     */
    RedundancyBoilerplate(BoilerplateShingles boilerplateShingles, StageRuns stageRuns) {
        RunId censusRun = stageRuns.upstream(StageModules.CONTENT_CENSUS);
        double floor = stageRuns.contentRedundancyFloor();
        this.hashes = TimedStatement.of(
                "Stage 4 (content redundancy)",
                "reading",
                "read",
                "the boilerplate shingles",
                () -> boilerplateShingles.resolve(censusRun, floor));
    }

    Set<Long> hashes() {
        return hashes;
    }
}
