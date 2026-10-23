package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.DoclingClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Stage 2's start check waits for a converter that gives no answer yet, and fails as it always has where the
 * converter answers with an error or still gives no answer once the wait is over (ADR-322 §7, amending ADR-071 and
 * ADR-175).
 *
 * <p>The converter is a client whose two health questions are scripted: the one check through the conversion client,
 * which throws what the transport or the converter said, and the quick question the wait asks, which answers yes or
 * no. The wait is the production {@link SidecarRecovery} on a clock short enough to run.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("651")
@Link(name = "ADR-322", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
class StageTwoWaitsForAConverterStillStartingTest {

    /** How long these tests wait for a converter to start: one second. */
    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    /** How often they ask it meanwhile. */
    private static final Duration CHECK_EVERY = Duration.ofMillis(20);

    /** How many of the wait's questions a converter that is still starting says no to before it answers. */
    private static final int NOT_YET = 3;

    /** A converter that says no to every question of the wait. */
    private static final int NEVER = Integer.MAX_VALUE;

    /** The line that says the step is waiting, as written for a converter that refused the connection. */
    private static final String WAITING = "Stage 2 (extraction) docling-serve is not answering its health check yet"
            + " (%s); waiting up to " + WAIT_AT_MOST.toSeconds() + " seconds for it to start";

    /** The line that says the wait ended because the converter answered, with however many seconds it took. */
    private static final String ANSWERED =
            "Stage 2 \\(extraction\\) docling-serve answers its health check after \\d+ seconds";

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger listenerLog;

    @BeforeEach
    void captureTheListenersLines() {
        logged = new ListAppender<>();
        logged.start();
        listenerLog = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ExtractionHealthCheckListener.class);
        listenerLog.addAppender(logged);
    }

    @AfterEach
    void release() {
        listenerLog.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("A converter still starting is waited for")
    @DisplayName("A converter that refuses the connection and then answers is waited for, and the log says when the wait began and how long it took")
    void aConverterStillStartingIsWaitedFor() {
        ScriptedConverter converter = ScriptedConverter.notAnswering(NOT_YET);

        claim("the start check returns without a failure once the converter answers",
                () -> assertThatCode(() -> listener(converter).beforeStep(null)).doesNotThrowAnyException());
        claim("the wait asked until it answered: " + NOT_YET + " questions it said no to, and the one after",
                () -> assertThat(converter.waitQuestions.get()).isEqualTo(NOT_YET + 1));
        claim("one warning said the converter is not answering yet, named what the transport reported, and said how"
                        + " long it would be waited for",
                () -> assertThat(lines(Level.WARN))
                        .containsExactly(WAITING.formatted(converter.checkFailures.getFirst())));
        claim("and one line said it answered, with the seconds it took", () -> assertThat(lines(Level.INFO))
                .filteredOn(line -> line.matches(ANSWERED))
                .hasSize(1));
    }

    @Test
    @Story("A converter still starting is waited for")
    @DisplayName("A converter that answers its health check with an error fails the start at once with that error, and is not waited for")
    void anErrorAnswerFailsAtOnce() {
        ScriptedConverter converter = ScriptedConverter.answeringUnavailable();

        Throwable failed = catchThrowable(() -> listener(converter).beforeStep(null));

        claim("the start check fails with the very error the converter answered with, which is what the closing line"
                        + " names today",
                () -> assertThat(failed).isSameAs(converter.checkFailures.getFirst()));
        claim("the converter was not waited for: the wait asked it nothing", () -> assertThat(converter.waitQuestions.get())
                .isZero());
        claim("and no line says it is being waited for", () -> assertThat(lines(Level.WARN)).isEmpty());
    }

    @Test
    @Story("A converter still starting is waited for")
    @DisplayName("A converter that still gives no answer when the wait is over is checked once more, and the start fails with what that check reported")
    void noAnswerAtTheBoundFailsWithTheLastCheck() {
        ScriptedConverter converter = ScriptedConverter.notAnswering(NEVER);

        Throwable failed = catchThrowable(() -> listener(converter).beforeStep(null));

        claim("the converter was checked twice through the conversion client: once before the wait and once after it",
                () -> assertThat(converter.checkFailures).hasSize(2));
        claim("and the start check fails with what the second check reported, so the closing line names the converter"
                        + " as it does today",
                () -> assertThat(failed).isSameAs(converter.checkFailures.getLast()));
        claim("the wait asked it more than once before it gave up", () -> assertThat(converter.waitQuestions.get())
                .isGreaterThan(1));
    }

    @Test
    @Story("A converter still starting is waited for")
    @DisplayName("A converter that answers the first check is not waited for")
    void anAnsweringConverterIsNotWaitedFor() {
        ScriptedConverter converter = ScriptedConverter.notAnswering(0);
        converter.checkAnswers = true;

        claim("the start check returns without a failure",
                () -> assertThatCode(() -> listener(converter).beforeStep(null)).doesNotThrowAnyException());
        claim("the wait asked it nothing", () -> assertThat(converter.waitQuestions.get()).isZero());
        claim("and no line says it is being waited for", () -> assertThat(lines(Level.WARN)).isEmpty());
    }

    private static ExtractionHealthCheckListener listener(ScriptedConverter converter) {
        return new ExtractionHealthCheckListener(converter, new SidecarRecovery(converter, WAIT_AT_MOST, CHECK_EVERY));
    }

    private List<String> lines(Level level) {
        return logged.list.stream()
                .filter(event -> event.getLevel().equals(level))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    /**
     * A converter whose check through the conversion client throws a fresh failure each time it is asked, unless it
     * answers, and whose quick health question says no {@code notYet} times and yes after.
     */
    private static final class ScriptedConverter extends DoclingClient {

        private final int notYet;
        private final boolean answersWithAnError;
        private final List<RuntimeException> checkFailures = new ArrayList<>();
        private final AtomicInteger waitQuestions = new AtomicInteger();
        private boolean checkAnswers;

        private ScriptedConverter(int notYet, boolean answersWithAnError) {
            super("http://unused.invalid");
            this.notYet = notYet;
            this.answersWithAnError = answersWithAnError;
        }

        static ScriptedConverter notAnswering(int notYet) {
            return new ScriptedConverter(notYet, false);
        }

        static ScriptedConverter answeringUnavailable() {
            return new ScriptedConverter(0, true);
        }

        @Override
        public void checkHealth() {
            if (checkAnswers) {
                return;
            }
            RuntimeException failure = answersWithAnError
                    ? new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)
                    : new ResourceAccessException("I/O error on GET request for \"http://127.0.0.1:5001/health\":"
                            + " Connection refused (check " + (checkFailures.size() + 1) + ")");
            checkFailures.add(failure);
            throw failure;
        }

        @Override
        public boolean isHealthy() {
            return waitQuestions.incrementAndGet() > notYet;
        }
    }
}
