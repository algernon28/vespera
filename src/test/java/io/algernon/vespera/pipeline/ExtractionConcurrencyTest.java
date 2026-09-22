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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What stage 2's concurrency settles (ADR-140): the conversion width is a fixed default of eight in
 * code, and the run-stopping condition is read off the one serial point every completed conversion
 * passes through, not off the order the documents were sent.
 *
 * <p>Three claims, and they are different in kind. The first is a number ADR-140 fixes the way ADR-071
 * fixed its timeout and streak counts — an operational parameter about the machine, not a corpus
 * judgement, so it is a code constant rather than a Profile gate. The second is the redefinition of
 * "consecutive": with documents converting together, a failure no longer follows or precedes anything
 * in a single order, so the rule counts the order outcomes were observed in. The third is the one this
 * class had to grow threads for — that the rule still holds when the conversions really do run at once,
 * and that the counter behind it is read from one thread and from none of the converting ones.
 *
 * <p><b>Why a drain is built here rather than borrowed from the code under test.</b> In what ships
 * there is no drain object to borrow: ADR-140 section 3 makes the drain the step's own single thread,
 * which exists only inside a running step. So the loop below stands in for it, and what is under test
 * is the shipped counter and the rule it enforces, driven at the shipped width by conversions that
 * genuinely overlap. The other half — that the shipped step really keeps this many conversions in
 * flight, and really converts somewhere other than the thread it was invoked on — is {@link
 * ExtractionStepTest}'s claim, because no unit can make it.
 *
 * <p>One shape is deliberately not claimed here: that a wave coming back part refused and part
 * converted leaves the run going. Under genuine concurrency the order those outcomes arrive in is not
 * this test's to choose, and one and the same wave can finish either side of the rule — which is not a
 * gap in the pin but the exposure ADR-140 books as a consequence. The definition itself is claimed
 * instead, over a sequence this class orders, which is what a definition can be held to.
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

    /**
     * How long one conversion waits for the rest of its wave before this test gives up on them.
     * Generous, because it bounds a scheduling delay and nothing else: nothing under it computes
     * anything, and a wave that has not assembled in this long has not assembled at all.
     */
    private static final Duration UNTIL_THE_WHOLE_WAVE_IS_CONVERTING = Duration.ofSeconds(30);

    @Test
    @Story("How many conversions run at once")
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

    @Test
    @Story("When a run stops instead of continuing")
    @DisplayName("A whole wave of conversions running together, every one of them refused, stops the run")
    void aWaveConvertingTogetherAndRefusedTogetherStopsTheRun() throws Exception {
        ExtractionCircuitBreaker breaker = new ExtractionCircuitBreaker();
        Wave wave = convertingTogether(CONVERSIONS_IN_FLIGHT, EVERY_ONE_REFUSED);

        claim(
                "all " + CONVERSIONS_IN_FLIGHT + " conversions really were running at the same moment — each one"
                        + " waited for every other before it answered at all, so what follows is a claim about a"
                        + " width that happened rather than about one the test asked for",
                () -> assertThat(wave.convertingThreads()).hasSize(CONVERSIONS_IN_FLIGHT));

        claim(
                "a wave the converter answered about nothing in stops the run, counted where the outcomes arrive"
                        + " rather than where the calls were sent: the run stops once"
                        + " " + SET_ASIDE_IN_A_ROW_THAT_STOPS_THE_RUN + " of them have arrived with nothing"
                        + " between them, which is the shape a converter that has stopped answering leaves behind"
                        + " however many calls were in flight when it stopped",
                () -> assertThatThrownBy(() -> wave.drainThrough(breaker))
                        .isInstanceOf(ExtractorStoppedAnsweringException.class));
    }

    @Test
    @Story("When a run stops instead of continuing")
    @DisplayName("The streak is read by the one draining thread and by none of the converting ones")
    void theStreakIsReadOnlyByTheDrainingThread() throws Exception {
        ExtractionCircuitBreaker breaker = new ExtractionCircuitBreaker();
        Wave wave = convertingTogether(CONVERSIONS_IN_FLIGHT, EVERY_ONE_ANSWERED);

        wave.drainThrough(breaker);

        claim(
                "the " + CONVERSIONS_IN_FLIGHT + " conversions ran on " + CONVERSIONS_IN_FLIGHT + " threads of"
                        + " their own and none of them was the thread that read their outcomes — which is what"
                        + " makes the next claim one about a real arrangement rather than about a loop",
                () -> assertThat(wave.convertingThreads())
                        .hasSize(CONVERSIONS_IN_FLIGHT)
                        .doesNotContainAnyElementsOf(wave.drainingThreads()));

        claim(
                "every outcome reached the streak from one and the same thread, so the count that decides whether"
                        + " a run stops is never two threads' arithmetic: a converting thread that touched it could"
                        + " lose a streak without losing a single outcome, and a run that should have stopped would"
                        + " carry on judging documents against a converter that had already stopped answering",
                () -> assertThat(wave.drainingThreads()).hasSize(1));
    }

    /** What the converter reported about one document in a wave: a refusal, or an answer about it. */
    private interface Answer {
        void report(ExtractionCircuitBreaker breaker);
    }

    /** A wave the converter refused in its entirety — the shape a converter that stopped leaves. */
    private static final Answer EVERY_ONE_REFUSED = ExtractionConcurrencyTest::setAside;

    /** A wave the converter answered about in its entirety — the healthy case, and the reset. */
    private static final Answer EVERY_ONE_ANSWERED = ExtractionConcurrencyTest::answeredAbout;

    /**
     * Conversions that genuinely ran at once, and the outcomes they produced, waiting to be read one at
     * a time.
     *
     * <p>This stands in for what ADR-140 section 3 makes the step's own single thread: the conversions
     * happen on threads of their own, and every outcome is handed to whichever one thread calls {@link
     * #drainThrough}. Nothing but that thread ever touches the breaker, which is half of what this
     * class claims.
     */
    private static final class Wave {

        private final List<String> convertingThreads;
        private final BlockingQueue<Answer> completed;
        private final List<String> drainingThreads = new ArrayList<>();

        private Wave(List<String> convertingThreads, BlockingQueue<Answer> completed) {
            this.convertingThreads = convertingThreads;
            this.completed = completed;
        }

        /** The threads the conversions themselves ran on, named. */
        Set<String> convertingThreads() {
            return Set.copyOf(convertingThreads);
        }

        /** The threads that read outcomes into the breaker. One of them, or the guarantee is gone. */
        Set<String> drainingThreads() {
            return Set.copyOf(drainingThreads);
        }

        /** Reads every outcome into the breaker, one at a time, in the order the conversions finished. */
        void drainThrough(ExtractionCircuitBreaker breaker) {
            Answer answer;
            while ((answer = completed.poll()) != null) {
                drainingThreads.add(Thread.currentThread().getName());
                answer.report(breaker);
            }
        }
    }

    /**
     * Runs {@code width} conversions that each wait for all the others before answering, so an overlap
     * is a fact this test established rather than a hope about the scheduler, and collects what they
     * answered in the order they finished.
     */
    private static Wave convertingTogether(int width, Answer answer) throws Exception {
        CyclicBarrier theWholeWaveIsConverting = new CyclicBarrier(width);
        BlockingQueue<Answer> completed = new LinkedBlockingQueue<>();
        List<String> convertingThreads = new ArrayList<>();
        ExecutorService conversions = Executors.newFixedThreadPool(width);
        try {
            List<Future<String>> running = new ArrayList<>();
            for (int i = 0; i < width; i++) {
                running.add(conversions.submit(() -> {
                    String convertedOn = Thread.currentThread().getName();
                    theWholeWaveIsConverting.await(
                            UNTIL_THE_WHOLE_WAVE_IS_CONVERTING.toMillis(), TimeUnit.MILLISECONDS);
                    completed.add(answer);
                    return convertedOn;
                }));
            }
            for (Future<String> conversion : running) {
                // A conversion that never joined its wave surfaces here, as the reason this call failed,
                // rather than further down as a puzzling claim about an empty queue.
                convertingThreads.add(threadThatConverted(conversion));
            }
        } finally {
            conversions.shutdownNow();
        }
        return new Wave(convertingThreads, completed);
    }

    /**
     * The thread one conversion ran on, or the reason it never finished — a barrier that timed out
     * means fewer conversions were running at once than the width claims, which is the failure worth
     * reading rather than the one it would otherwise cause.
     */
    private static String threadThatConverted(Future<String> conversion) throws Exception {
        try {
            return conversion.get(UNTIL_THE_WHOLE_WAVE_IS_CONVERTING.toMillis() * 2, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TimeoutException || e.getCause() instanceof BrokenBarrierException) {
                throw new IllegalStateException(
                        "fewer than the whole wave was converting at once: a conversion waited for the others"
                                + " and gave up, so nothing claimed below would be a claim about concurrency",
                        e.getCause());
            }
            throw e;
        }
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
