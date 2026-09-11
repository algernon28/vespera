package io.algernon.vespera.pipeline;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

/**
 * The consecutive-timeout counter ADR-071 fixes at 3: a Docling-reported {@code timeout} category and
 * a client-side {@link io.algernon.vespera.extraction.DoclingCallTimeoutException} both count against it, since
 * both readings resolve document-scope-versus-consecutive by the same rule.
 *
 * <p>Step-scoped so the streak spans the whole step's occurrences rather than one chunk — the
 * consecutive count has to survive a chunk boundary to mean what ADR-071 says it means.
 *
 * <p><b>Counted atomically, because stage 2's chunks run in parallel</b> ({@link
 * ExtractionJobConfiguration#CONCURRENT_CONVERSIONS}). That makes "in a row" mean <em>with no call
 * that answered in between</em> rather than "adjacent in the corpus", and ADR-071's reading survives
 * the change intact: what three consecutive timeouts are evidence of is a sidecar that has stopped
 * answering, and a sidecar that answers any one of four in-flight calls resets the streak exactly as
 * it should. What would <em>not</em> survive is an unsynchronised {@code ++}, where two threads
 * timing out together can leave the streak one short of the truth and the reading never flips.
 */
@Component
@StepScope
class ExtractionTimeoutStreak {

    /** ADR-071: three timeouts in a row flip the reading from document scope to service scope. */
    static final int CONSECUTIVE_TIMEOUT_COUNT = 3;

    private final AtomicInteger consecutiveTimeouts = new AtomicInteger();

    /** Records one more timeout, returning the streak length including this one. */
    int recordTimeout() {
        return consecutiveTimeouts.incrementAndGet();
    }

    /** A call that did not time out: whatever streak had accumulated stops meaning anything. */
    void reset() {
        consecutiveTimeouts.set(0);
    }
}
