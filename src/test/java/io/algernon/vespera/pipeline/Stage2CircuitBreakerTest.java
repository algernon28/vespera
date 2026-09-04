package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.FailureCategory;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.VerdictKind;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * When a run stops rather than continues (ADR-071's consecutive-service-scope-failure breaker): a
 * converter that has set aside a run of documents in a row is read as broken, and the run stops with
 * a cause rather than finishing having examined nothing.
 *
 * <p>Consecutive, not cumulative, is the whole of what this class defends. Spring Batch's own
 * {@code skipLimit} counts every skip over the life of a step, which a long run with sparse harmless
 * blips would eventually cross while nothing was ever wrong — ADR-071 keeps that configured as a
 * backstop and puts this counter beside it. So the second test here is the load-bearing one: it is
 * what would fail if the counter were ever changed to a cumulative one.
 *
 * <p>Both listener roles are called directly rather than driven through a running step: what a
 * service-scope response earns is {@link Stage2ItemProcessorTest}'s question, and what a streak of
 * those earns is this class's. No context and no database — the counter reads nothing but the
 * sequence of calls made to it.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("47")
@Link(name = "ADR-071", url = Adr.DOCLING_INVOCATION_CONTRACT_IS_ONE_SYNC_CALL, type = "adr")
class Stage2CircuitBreakerTest {

    /**
     * How many documents set aside in a row read as a broken converter (ADR-071), read off the
     * counter rather than repeated, so this class cannot disagree with the rule it is claiming.
     */
    private static final int SET_ASIDE_IN_A_ROW_THAT_STOPS_THE_RUN =
            Stage2CircuitBreaker.CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT;

    /** One fewer than that: a run this long is not yet evidence of anything. */
    private static final int SET_ASIDE_IN_A_ROW_THAT_DOES_NOT = SET_ASIDE_IN_A_ROW_THAT_STOPS_THE_RUN - 1;

    /**
     * The reasons a document is set aside, cycled through rather than repeated.
     *
     * <p>A different reason each time, deliberately: ADR-071 counts them summed together, because a
     * converter alternating between two ways of failing is exactly as broken as one repeating a single
     * way, and a counter kept per reason would let it evade every one of them.
     */
    private static final List<FailureCategory> REASONS_A_DOCUMENT_IS_SET_ASIDE = List.of(
            FailureCategory.CAPACITY,
            FailureCategory.INTERNAL,
            FailureCategory.TARGET_UNAVAILABLE,
            FailureCategory.UNKNOWN);

    /** Stands in for the occurrence a listener is told about; the counter never reads it. */
    private static final OccurrenceId AN_OCCURRENCE = new OccurrenceId(1L);

    private int reasonsUsedSoFar;

    @Test
    @Story("When a run stops instead of continuing")
    @DisplayName("A converter that has set aside document after document is read as broken, and the run stops")
    void stopsTheRunOnceEnoughDocumentsAreSetAsideInARow() {
        Stage2CircuitBreaker breaker = new Stage2CircuitBreaker();

        claim(
                "the first " + SET_ASIDE_IN_A_ROW_THAT_DOES_NOT + " documents set aside in a row do not stop"
                        + " the run: a converter that answers most of the time will do this occasionally, and"
                        + " a run that stopped on it would abandon the archive it had left to examine",
                () -> assertThatCode(() -> setAside(breaker, SET_ASIDE_IN_A_ROW_THAT_DOES_NOT))
                        .doesNotThrowAnyException());
        claim(
                "the next one -- " + SET_ASIDE_IN_A_ROW_THAT_STOPS_THE_RUN + " in a row, each for a different"
                        + " reason than the last -- stops the run, because a run that completes having"
                        + " examined nothing reports success and looks exactly like one that worked",
                () -> assertThatThrownBy(() -> setAside(breaker, 1))
                        .isInstanceOf(ServiceScopeCircuitBreakerTripped.class));
    }

    @Test
    @Story("When a run stops instead of continuing")
    @DisplayName("A run of set-aside documents is broken by one that converts, however many came before it")
    void oneConvertedDocumentEndsTheRun() {
        Stage2CircuitBreaker breaker = new Stage2CircuitBreaker();

        claim(
                "with " + SET_ASIDE_IN_A_ROW_THAT_DOES_NOT + " documents set aside, one that the converter"
                        + " answered about, and then " + SET_ASIDE_IN_A_ROW_THAT_DOES_NOT + " more set aside"
                        + " -- more in total than the " + SET_ASIDE_IN_A_ROW_THAT_STOPS_THE_RUN + " that stop"
                        + " a run -- the run continues, because what stops it is a converter that has stopped"
                        + " answering, not a tally of how often it has ever failed to",
                () -> assertThatCode(() -> {
                            setAside(breaker, SET_ASIDE_IN_A_ROW_THAT_DOES_NOT);
                            answeredAbout(breaker);
                            setAside(breaker, SET_ASIDE_IN_A_ROW_THAT_DOES_NOT);
                        })
                        .doesNotThrowAnyException());
    }

    /** {@code count} documents the step set aside, one after another, with nothing between them. */
    private void setAside(Stage2CircuitBreaker breaker, int count) {
        for (int i = 0; i < count; i++) {
            FailureCategory reason =
                    REASONS_A_DOCUMENT_IS_SET_ASIDE.get(reasonsUsedSoFar++ % REASONS_A_DOCUMENT_IS_SET_ASIDE.size());
            breaker.onSkipInProcess(
                    AN_OCCURRENCE,
                    new ServiceScopeFailure(AN_OCCURRENCE, reason.name().toLowerCase(Locale.ROOT), "set aside"));
        }
    }

    /**
     * One document the converter answered about — which is what ends a run, whether the answer earned
     * a verdict or passed the document on for something later to measure.
     */
    private static void answeredAbout(Stage2CircuitBreaker breaker) {
        breaker.afterProcess(
                AN_OCCURRENCE, new Stage2Outcome(AN_OCCURRENCE, VerdictKind.EXTRACTION_FAILED, "answered"));
    }
}
