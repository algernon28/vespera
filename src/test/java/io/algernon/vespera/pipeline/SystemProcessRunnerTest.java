package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the process runner that starts real processes reports for the two commands that do not end on their own terms
 * (ADR-323 §5): one killed at its limit counts as exit -1 with what it printed, and one whose program cannot be
 * started throws, which the starter reads as exit 127, or as Docker not installed where the program is {@code docker}.
 *
 * <p>The command killed is the Java running these tests, on a class of the test classpath that sleeps until killed, so
 * the test runs the same on every system.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("652")
@Link(name = "ADR-323", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
class SystemProcessRunnerTest {

    /** How long the sleeping command is given before it is killed: half a second. */
    private static final Duration LIMIT = Duration.ofMillis(500);

    /** The exit code a command killed at its limit counts as. */
    private static final int KILLED = -1;

    /** A program no system has. */
    private static final String NO_SUCH_PROGRAM = "vespera-no-such-program-anywhere";

    @Test
    @Story("Vespera refuses, naming why, when it cannot start them")
    @DisplayName("A command still running at its limit is killed and counts as exit -1, with what it printed before")
    void aCommandPastItsLimitIsKilledAndCountsAsMinusOne() throws IOException {
        List<String> sleeper = List.of(
                ProcessHandle.current().info().command().orElseThrow(),
                "-cp",
                System.getProperty("java.class.path"),
                SleepsUntilKilled.class.getName());

        ProcessOutcome outcome = new SystemProcessRunner().run(sleeper, LIMIT);

        claim("it counts as exit " + KILLED, () -> assertThat(outcome.exitCode()).isEqualTo(KILLED));
        claim("and what it printed before it was killed is its output", () -> assertThat(outcome.output())
                .contains(SleepsUntilKilled.STARTED));
    }

    @Test
    @Story("Vespera refuses, naming why, when it cannot start them")
    @DisplayName("A command whose program cannot be started throws, as the process builder does")
    void aProgramThatCannotBeStartedThrows() {
        Throwable thrown = catchThrowable(() -> new SystemProcessRunner().run(List.of(NO_SUCH_PROGRAM), LIMIT));

        claim("it throws the input-output exception the starter reads as a program not there",
                () -> assertThat(thrown).isInstanceOf(IOException.class));
    }
}
