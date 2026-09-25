package io.algernon.vespera.pipeline;

import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.stereotype.Component;

/**
 * ADR-083's gate carried from stage 5's first step to its second (ADR-092): whether any seed produced
 * text at all, and — ADR-155's addition — whether any seed file would not open while the step was
 * unfinished.
 *
 * <p>The one gate in stage 5 that cannot be re-derived from stored rows, and that is not an oversight
 * but the gate working. With no usable seed, seed extraction mints no run — so no measurement row and
 * no {@code unusable_seed} row exists either, because both carry the run that found them. There is
 * nothing in the database for a later step to read, by design, so the fact travels in process for the
 * length of one invocation instead.
 *
 * <p>The second fact, a seed file that would not open, is set only while the step is <em>unfinished</em>
 * (ADR-155 section 2): the rows it wrote that invocation go under a run whose step is not recorded
 * complete, and the next invocation discards and rewrites them, so nothing here would be true about a
 * later invocation for a later step to have read anyway. It carries this second fact for a reason of
 * its own, not the first's: by the time it is set, the run has been minted and the {@code
 * unusable_seed} rows that record it have been written, but the fact belongs to this invocation's read
 * of the archive, and those rows sit under a step that is not recorded as finished, which no later
 * step may trust (ADR-116) — so the fact is not read back from them.
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
    private boolean seedFileCouldNotOpen;

    /** Called by {@link SeedExtractionItemWriter} once the whole seed folder has been converted. */
    void recordUsableSeeds(long usableSeeds) {
        this.anySeedUsable = usableSeeds > 0;
    }

    /** Whether ADR-020's maximum has anything to be taken over — and stage 5 anything to measure. */
    boolean anySeedUsable() {
        return anySeedUsable;
    }

    /**
     * Called by {@link SeedExtractionItemWriter} when the step is not recorded as finished this
     * invocation because at least one seed file would not open (ADR-155 section 2).
     */
    void recordSeedFileCouldNotOpen() {
        this.seedFileCouldNotOpen = true;
    }

    /** Whether a seed file could not be opened while seed extraction's step was unfinished this invocation. */
    boolean seedFileCouldNotOpen() {
        return seedFileCouldNotOpen;
    }
}
