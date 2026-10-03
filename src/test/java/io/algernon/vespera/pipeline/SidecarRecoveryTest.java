package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.DoclingClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The wait for a document converter that dropped a connection to answer its health check again
 * (ADR-175): at once if it already answers, after however many checks it takes if it comes back inside
 * the bound, and a failure of its own if it does not.
 *
 * <p>The converter is a client whose health answer is scripted, because only the wait is asked about
 * here. That the production client reaches a real one is {@code DoclingClientTest}'s to claim.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("326")
@Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
class SidecarRecoveryTest {

    /** How long these tests wait for a converter to come back. */
    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    /** How often they ask it meanwhile. */
    private static final Duration CHECK_EVERY = Duration.ofMillis(20);

    /** How many checks a converter that is on its way back fails before it answers. */
    private static final int CHECKS_FAILED_BEFORE_IT_IS_BACK = 3;

    /** A converter that never answers, however often it is asked. */
    private static final int NEVER = Integer.MAX_VALUE;

    @Test
    @Story("Waiting for a converter that dropped a connection")
    @DisplayName("A converter that already answers its health check is asked once and not waited for")
    void aHealthyConverterIsNotWaitedFor() {
        ScriptedHealth converter = unhealthyFor(0);

        claim(
                "the wait ends without a failure",
                () -> assertThatCode(() -> new SidecarRecovery(converter, WAIT_AT_MOST, CHECK_EVERY).awaitHealthy())
                        .doesNotThrowAnyException());
        claim(
                "it was asked once: an answer on the first check is all the wait there is",
                () -> assertThat(converter.checks.get()).isEqualTo(1));
    }

    @Test
    @Story("Waiting for a converter that dropped a connection")
    @DisplayName("A converter that comes back within the bound is waited for until it answers")
    void aConverterThatComesBackIsWaitedFor() {
        ScriptedHealth converter = unhealthyFor(CHECKS_FAILED_BEFORE_IT_IS_BACK);

        claim(
                "the wait ends without a failure",
                () -> assertThatCode(() -> new SidecarRecovery(converter, WAIT_AT_MOST, CHECK_EVERY).awaitHealthy())
                        .doesNotThrowAnyException());
        claim(
                "it was asked until it answered: " + CHECKS_FAILED_BEFORE_IT_IS_BACK + " checks it failed,"
                        + " and the one after them",
                () -> assertThat(converter.checks.get()).isEqualTo(CHECKS_FAILED_BEFORE_IT_IS_BACK + 1));
    }

    @Test
    @Story("Waiting for a converter that dropped a connection")
    @DisplayName("A converter that does not come back within the bound fails the wait, and the failure says how long was waited")
    void aConverterThatStaysGoneFailsTheWait() {
        ScriptedHealth converter = unhealthyFor(NEVER);
        SidecarRecovery recovery = new SidecarRecovery(converter, WAIT_AT_MOST, CHECK_EVERY);

        claim(
                "the wait ends in a failure of its own, saying the converter dropped the connection and did"
                        + " not answer its health check again within the " + WAIT_AT_MOST.toSeconds()
                        + " second(s) these tests wait",
                () -> assertThatThrownBy(recovery::awaitHealthy)
                        .isInstanceOf(DoclingDidNotComeBackException.class)
                        .hasMessage("docling-serve dropped the connection and did not answer its health check"
                                + " again within " + WAIT_AT_MOST.toSeconds() + " seconds"));
        claim(
                "and it was asked more than once before the wait gave up",
                () -> assertThat(converter.checks.get()).isGreaterThan(1));
    }

    private static ScriptedHealth unhealthyFor(int checks) {
        return new ScriptedHealth(checks);
    }

    /** A client that fails its first {@code failing} health checks and answers every one after. */
    private static final class ScriptedHealth extends DoclingClient {

        private final int failing;
        private final AtomicInteger checks = new AtomicInteger();

        private ScriptedHealth(int failing) {
            super("http://unused.invalid");
            this.failing = failing;
        }

        @Override
        public boolean isHealthy() {
            return checks.incrementAndGet() > failing;
        }
    }
}
