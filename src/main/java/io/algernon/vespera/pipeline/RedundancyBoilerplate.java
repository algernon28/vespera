package io.algernon.vespera.pipeline;

import io.algernon.vespera.similarity.BoilerplateShingles;
import java.util.Set;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.stereotype.Component;

/**
 * The boilerplate hash set stage 4's run computed against, resolved once per job execution and shared
 * by both steps — job-scoped for the same reason {@link RedundancyRun} is: resolving it is two SQL
 * queries ({@link BoilerplateShingles}), cheap enough once but wasteful to repeat for every one of a
 * chunk step's items.
 *
 * <p>Depends on {@link RedundancyRun} directly rather than through an {@code ObjectProvider}: this bean
 * is itself only ever reached through an {@code ObjectProvider} at its own call sites, so its target is
 * never constructed — and therefore never triggers {@link RedundancyRun}'s own construction — while the
 * gate is closed.
 */
@Component
@JobScope
class RedundancyBoilerplate {

    private final Set<Long> hashes;

    RedundancyBoilerplate(BoilerplateShingles boilerplateShingles, RedundancyRun redundancyRun) {
        this.hashes = boilerplateShingles.resolve(redundancyRun.stage3RunId(), redundancyRun.floor());
    }

    Set<Long> hashes() {
        return hashes;
    }
}
