package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.similarity.BoilerplateShingles;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.stereotype.Component;

/**
 * The boilerplate hash set stage 4's run computed against, resolved once per job execution and shared
 * by both steps — job-scoped for the same reason {@link StageRuns} is: resolving it is two SQL queries
 * ({@link BoilerplateShingles}), cheap enough once but wasteful to repeat for every one of a chunk
 * step's items.
 *
 * <p>Depends on {@link StageRuns} directly rather than through an {@code ObjectProvider}: this bean is
 * itself only ever reached through an {@code ObjectProvider} at its own call sites, so its target is
 * never constructed — and therefore never triggers stage 4's own run being minted — while the gate is
 * closed.
 */
@Component
@JobScope
class RedundancyBoilerplate {

    private static final Logger log = LoggerFactory.getLogger(RedundancyBoilerplate.class);

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

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
        log.info("Stage 4 (content redundancy) is reading the boilerplate shingles");
        long started = System.nanoTime();
        this.hashes = boilerplateShingles.resolve(censusRun, floor);
        log.info(
                "Stage 4 (content redundancy) read the boilerplate shingles in {} s",
                String.format(Locale.ROOT, "%.1f", (System.nanoTime() - started) / NANOS_PER_SECOND));
    }

    Set<Long> hashes() {
        return hashes;
    }
}
