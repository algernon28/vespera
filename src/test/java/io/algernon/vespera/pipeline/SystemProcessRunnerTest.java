package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
 * What the process runner that starts real processes reports for the commands that do not end on their own terms
 * (ADR-323 §5): one killed at its limit counts as exit -1 with what it printed, and one whose program cannot be
 * started throws an {@link IOException}, which the starter reads as exit 127, or as Docker not installed where the
 * program is {@code docker}. A temporary file the output goes to that cannot be created or read throws {@link
 * CommandOutputFileException} instead, which is not an {@link IOException}, so that the starter cannot read it as a
 * program not there (ADR-326).
 *
 * <p>The command killed is the Java running these tests, on a class of the test classpath that sleeps until killed, so
 * the test runs the same on every system. The runner is built on a folder of each test's own.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("652")
@Link(name = "ADR-323", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
class SystemProcessRunnerTest {

    /** The folder the runner creates each command's output file in. */
    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path outputFolder;

    /** How long a command is given before it is killed: half a second, and the sleeping command's first limit. */
    private static final Duration LIMIT = Duration.ofMillis(500);

    /**
     * The longest limit the sleeping command is given, its limit doubled from {@link #LIMIT} until it prints in time:
     * thirty-two seconds. A Java started on a machine busy with eight times as many threads as it has processors
     * printed its first line within 6.4 s in 30 starts out of 30.
     */
    private static final Duration LONGEST_LIMIT = Duration.ofSeconds(32);

    /** How long a command killed is waited for to be gone, in whole seconds. */
    private static final long GONE_WITHIN_SECONDS = 30;

    /** How long a command killed is waited for to be gone, so that the output file it held can be deleted. */
    private static final Duration GONE_WITHIN = Duration.ofSeconds(GONE_WITHIN_SECONDS);

    /** The exit code a command killed at its limit counts as. */
    private static final int KILLED = -1;

    /**
     * The command is a Java, which a machine busy with other builds can take seconds to start, so that a fixed limit
     * of half a second killed it before it printed anything. The limit is doubled, from {@link #LIMIT} up to {@link
     * #LONGEST_LIMIT}, until a run's command printed its line before it was killed, and every run is held to what the
     * runner owes it (ADR-352): killed at its limit and not before, counted as exit -1, and with the command's line at
     * most once in its output. What else the Java writes, such as a note or a warning of the options it inherited from
     * the machine's environment, or a log of the classes it loads, can come before that line or after it and is not
     * claimed on. On an idle machine the first run is the last.
     */
    @Test
    @Story("Vespera refuses, naming why, when it cannot start them")
    @Issue("698")
    @Link(name = "ADR-352", url = Adr.A_TIMING_BOUND_IN_A_UNIT_TEST_IS_ONE_A_BUSY_MACHINE_DOES_NOT_MOVE, type = "adr")
    @DisplayName("A command still running at its limit is killed and counts as exit -1, with what it printed before")
    void aCommandPastItsLimitIsKilledAndCountsAsMinusOne() throws Exception {
        List<String> sleeper = List.of(
                ProcessHandle.current().info().command().orElseThrow(),
                "-cp",
                System.getProperty("java.class.path"),
                SleepsUntilKilled.class.getName());

        List<Duration> limits = new java.util.ArrayList<>();
        List<Duration> tooks = new java.util.ArrayList<>();
        List<ProcessOutcome> outcomes = new java.util.ArrayList<>();
        for (Duration limit = LIMIT;
                limit.compareTo(LONGEST_LIMIT) <= 0
                        && (outcomes.isEmpty() || !outcomes.getLast().output().contains(SleepsUntilKilled.STARTED));
                limit = limit.multipliedBy(2)) {
            java.util.Set<ProcessHandle> before = descendants();
            long began = System.nanoTime();
            outcomes.add(new SystemProcessRunner(outputFolder).run(sleeper, limit));
            tooks.add(Duration.ofNanos(System.nanoTime() - began));
            limits.add(limit);
            awaitTheEndOfEveryDescendantNotIn(before);
        }

        claim("the last run's command printed its line before it was killed, and its output holds that line",
                () -> assertThat(outcomes.getLast().output()).contains(SleepsUntilKilled.STARTED));
        claim("every run counts as exit " + KILLED + ", and its command's line is in its output at most once, whatever"
                        + " else the Java wrote",
                () -> assertThat(outcomes).allSatisfy(outcome -> {
                    assertThat(outcome.exitCode()).isEqualTo(KILLED);
                    List<String> lines = outcome.output().lines().toList();
                    assertThat(lines)
                            .as("the lines of a killed command's output, %s", lines)
                            .filteredOn(SleepsUntilKilled.STARTED::equals)
                            .hasSizeLessThanOrEqualTo(1);
                }));
        claim("and every run returned no sooner than its limit, the half second it was first given and each doubled"
                        + " after (" + limits + ", taking " + tooks + ")",
                () -> {
                    for (int run = 0; run < limits.size(); run++) {
                        assertThat(tooks.get(run)).isGreaterThanOrEqualTo(limits.get(run));
                    }
                });
    }

    /** The processes this Java started, and those they started, still running. */
    private static java.util.Set<ProcessHandle> descendants() {
        return ProcessHandle.current().descendants().collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Waits for the processes started since {@code before} to be gone: a command killed holds its output file open
     * until it is, and on Windows the folder of a test's own cannot be deleted while it does.
     */
    private static void awaitTheEndOfEveryDescendantNotIn(java.util.Set<ProcessHandle> before) throws Exception {
        for (ProcessHandle started : descendants()) {
            if (!before.contains(started)) {
                started.destroyForcibly();
                started.onExit().get(GONE_WITHIN.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        }
    }
}
