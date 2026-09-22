package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What stage 2's concurrency settles (ADR-140): the conversion width is a fixed default of eight in
 * code, and the run-stopping condition is read off the one serial drain every completed conversion
 * passes through, not off the order the documents were dispatched.
 *
 * <p>Two claims, and they are different in kind. The first is a number ADR-140 fixes the same way
 * ADR-071 fixed its timeout and streak counts — an operational parameter about the machine, not a
 * corpus judgement, so it is a code constant rather than a Profile gate. The second is the
 * redefinition of "consecutive": with documents in flight together, a failure no longer succeeds or
 * follows anything in a single order, so the breaker reads completion order on the drain. The
 * load-bearing property is that a run of failures interleaved with successes is <em>not</em> the
 * run-stopping condition — the sidecar answered for the neighbours — while a failed wave, where every
 * document in flight comes back refused with nothing converting, is, and stops the run. That is
 * ADR-139's "a step that completed is a step in which five service-scope failures never landed
 * consecutively," read under concurrency rather than weakened by it.
 *
 * <p>The breaker is driven directly rather than through a running step, on {@link
 * ExtractionCircuitBreakerTest}'s precedent: no context, no database, the counter reads nothing but
 * the sequence of completions handed to it.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("264")
@Link(name = "ADR-140", url = Adr.STAGE_2_CONVERTS_EIGHT_AT_A_TIME, type = "adr")
class ExtractionConcurrencyTest {

    /** ADR-140: how many Docling calls stage 2 keeps in flight, fixed in code rather than gated. */
    private static final int CONVERSIONS_IN_FLIGHT = 8;

    /** How many set-asides read as a broken converter, off the breaker rather than repeated. */
    private static final int SET_ASIDE_IN_A_ROW_THAT_STOPS_THE_RUN =
            ExtractionCircuitBreaker.CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT;

    /** Stands in for the occurrence a listener is told about; the counter never reads it. */
    private static final OccurrenceId AN_OCCURRENCE = new OccurrenceId(1L);

    @Test
    @Story("When a run stops instead of continuing")
    @DisplayName("Stage 2 keeps eight conversions in flight, as a fixed default in code")
    void theConversionWidthIsAFixedDefaultOfEightInCode() {
        claim(
                "the conversion width is " + CONVERSIONS_IN_FLIGHT + " — the number the code fixes rather than"
                        + " one a person sets per corpus — because how many conversions a machine can run at"
                        + " once is a fact about that machine, not about the archive being judged",
                () -> assertThat(ExtractionJobConfiguration.CONVERSION_CONCURRENCY)
                        .isEqualTo(CONVERSIONS_IN_FLIGHT));
    }

    @Test
    @Story("When a run stops instead of continuing")
    @DisplayName("Failures interleaved with conversions do not stop the run, and a wave of failures does")
    void failuresInterleavedWithConversionsDoNotStopTheRunAndAFailedWaveDoes() {
        ExtractionCircuitBreaker breaker = new ExtractionCircuitBreaker();

        claim(
                "more than " + SET_ASIDE_IN_A_ROW_THAT_STOPS_THE_RUN + " documents set aside in total, but each"
                        + " one followed by a document the converter answered about, does not stop the run — this is"
                        + " the interleaving that cannot happen in a serial pipeline and is ordinary under"
                        + " concurrency: what stops the run is a converter that stopped answering, not a tally of"
                        + " how often it has failed across threads",
                () -> assertThatCode(() -> {
                            for (int i = 0; i < SET_ASIDE_IN_A_ROW_THAT_STOPS_THE_RUN + 1; i++) {
                                setAside(breaker);
                                answeredAbout(breaker);
                            }
                        })
                        .doesNotThrowAnyException());

        claim(
                "as many documents set aside in a row as read as broken, with no conversion completing between"
                        + " them — the shape a sidecar that stopped answering leaves behind, every call in flight"
                        + " refused — stops the run, because the rule counts failures in the order they finished,"
                        + " rather than the order they were sent, and a whole wave of refusals finishing together is"
                        + " counted together",
                () -> assertThatThrownBy(() -> {
                            for (int i = 0; i < SET_ASIDE_IN_A_ROW_THAT_STOPS_THE_RUN; i++) {
                                setAside(breaker);
                            }
                        })
                        .isInstanceOf(ExtractorStoppedAnsweringException.class));
    }

    /** One document the step set aside, for the one reason the counter ever sees. */
    private static void setAside(ExtractionCircuitBreaker breaker) {
        breaker.onSkipInProcess(
                AN_OCCURRENCE,
                new ServiceScopeFailureException(
                        AN_OCCURRENCE, FailureCategory.INTERNAL.name().toLowerCase(Locale.ROOT), "set aside"));
    }

    /**
     * One document the converter answered about, which is the completion that resets a streak — whether
     * it earned a verdict or passed on for something later to measure.
     */
    private static void answeredAbout(ExtractionCircuitBreaker breaker) {
        breaker.afterProcess(
                AN_OCCURRENCE, new ExtractionOutcome(AN_OCCURRENCE, VerdictKind.EXTRACTION_FAILED, "answered"));
    }
}