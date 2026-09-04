package io.algernon.vespera.pipeline;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

/**
 * The consecutive-timeout counter ADR-071 fixes at 3: a Docling-reported {@code timeout} category and
 * a client-side {@link io.algernon.vespera.extraction.DoclingCallTimedOut} both count against it, since
 * both readings resolve document-scope-versus-consecutive by the same rule.
 *
 * <p>Step-scoped so the streak spans the whole step's occurrences rather than one chunk — the
 * consecutive count has to survive a chunk boundary to mean what ADR-071 says it means.
 */
@Component
@StepScope
class Stage2TimeoutStreak {

    /** ADR-071: three timeouts in a row flip the reading from document scope to service scope. */
    static final int CONSECUTIVE_TIMEOUT_COUNT = 3;

    private int consecutiveTimeouts = 0;

    /** Records one more timeout, returning the streak length including this one. */
    int recordTimeout() {
        return ++consecutiveTimeouts;
    }

    /** A call that did not time out: whatever streak had accumulated stops meaning anything. */
    void reset() {
        consecutiveTimeouts = 0;
    }
}
