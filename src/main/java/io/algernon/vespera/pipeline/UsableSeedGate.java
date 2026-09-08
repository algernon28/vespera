package io.algernon.vespera.pipeline;

import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.stereotype.Component;

/**
 * ADR-083's gate carried from stage 5's first step to its second (ADR-092): whether any seed produced
 * text at all.
 *
 * <p>The one gate in stage 5 that cannot be re-derived from stored rows, and that is not an oversight
 * but the gate working. With no usable seed, seed extraction mints no run — so no measurement row and
 * no {@code unusable_seed} row exists either, because both carry the run that found them. There is
 * nothing in the database for a later step to read, by design, so the fact travels in process for the
 * length of one invocation instead.
 *
 * <p>{@code @JobScope} for the same reason {@link SeedMeasurementRun} is: one instance serves every
 * step of stage 5 within one invocation, and the next invocation asks its own question. Closed until
 * seed extraction says otherwise, which is also the right answer when that step never ran at all —
 * {@link SeedGate} shut means nothing was converted, and nothing converted means nothing usable.
 */
@Component
@JobScope
class UsableSeedGate {

    private boolean anySeedUsable;

    /** Called by {@link SeedExtractionItemWriter} once the whole seed folder has been converted. */
    void recordUsableSeeds(long usableSeeds) {
        this.anySeedUsable = usableSeeds > 0;
    }

    /** Whether ADR-020's maximum has anything to be taken over — and stage 5 anything to measure. */
    boolean anySeedUsable() {
        return anySeedUsable;
    }
}
