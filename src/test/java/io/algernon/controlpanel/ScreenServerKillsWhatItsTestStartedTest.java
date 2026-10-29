package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The processes {@link ScreenServer#close} kills are those the test's JVM started since the server was started, told
 * from those already running by pid and start instant together: Windows soon gives the pid of a process that ended to a
 * new one, and a child under a reused pid left running keeps the test's folder from being deleted (#661).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("661")
@Link(name = "ADR-297", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_TESTED_THROUGH_THE_PAGES_IT_SERVES, type = "adr")
class ScreenServerKillsWhatItsTestStartedTest {

    private static final long A_PID = 4242;

    private static final long ANOTHER_PID = 4343;

    private static final Instant BEFORE = Instant.parse("2026-10-29T10:15:30Z");

    private static final Instant SINCE = BEFORE.plus(Duration.ofSeconds(3));

    private static final long ENDED_BOUND_SECONDS = 60;

    @Test
    @Story("The screen tests leave no process running")
    @DisplayName("A process under the pid of one running when the server started, but started since, is killed")
    void aProcessUnderAReusedPidIsStartedSince() {
        Set<ScreenServer.Started> runningBefore = ScreenServer.snapshot(Stream.of(new Fake(A_PID, BEFORE)));
        Fake reused = new Fake(A_PID, SINCE);

        claim("a process with a pid of the snapshot and a start instant after it is among those started since",
                () -> assertThat(ScreenServer.startedSince(runningBefore, Stream.of(reused))).containsExactly(reused));
    }

    @Test
    @Story("The screen tests leave no process running")
    @DisplayName("A process running when the server started is left, one started since under a new pid is killed")
    void theSameProcessIsLeftAndANewOneIsKilled() {
        Fake same = new Fake(A_PID, BEFORE);
        Fake other = new Fake(ANOTHER_PID, SINCE);
        Set<ScreenServer.Started> runningBefore = ScreenServer.snapshot(Stream.of(new Fake(A_PID, BEFORE)));

        claim("of the process of the snapshot, same pid and start, and one under a new pid, only the new one was started"
                        + " since",
                () -> assertThat(ScreenServer.startedSince(runningBefore, Stream.of(same, other)))
                        .containsExactly(other));
    }

    @Test
    @Story("The screen tests leave no process running")
    @DisplayName("A process that gives no start instant is killed, though its pid was running when the server started")
    void aProcessWithNoStartInstantIsStartedSince() {
        Set<ScreenServer.Started> runningBefore = ScreenServer.snapshot(Stream.of(new Fake(A_PID, BEFORE)));
        Fake unknown = new Fake(A_PID, null);

        claim("a process under a pid of the snapshot that gives no start instant is among those started since",
                () -> assertThat(ScreenServer.startedSince(runningBefore, Stream.of(unknown)))
                        .containsExactly(unknown));
        claim("and a process that gave no start instant when the snapshot was taken is not in it",
                () -> assertThat(ScreenServer.snapshot(Stream.of(unknown))).isEmpty());
    }

    @Test
    @Story("The screen tests leave no process running")
    @DisplayName("Of the system's processes, a child running at the snapshot is left and one started after it is not")
    void theRuleReadsTheProcessesTheSystemNames() throws Exception {
        Process before = ChildForTests.builder("sleep").start();
        Process after = null;
        try {
            Set<ScreenServer.Started> runningBefore = ScreenServer.snapshot(ProcessHandle.current().descendants());
            after = ChildForTests.builder("sleep").start();
            long afterPid = after.pid();

            List<Long> started = ScreenServer.startedSince(runningBefore, Stream.of(before.toHandle(),
                    after.toHandle())).stream().map(ProcessHandle::pid).toList();
            claim("of a child running at the snapshot and one started after it, only the later one was started since",
                    () -> assertThat(started).containsExactly(afterPid));
        } finally {
            before.destroyForcibly().waitFor(ENDED_BOUND_SECONDS, TimeUnit.SECONDS);
            if (after != null) {
                after.destroyForcibly().waitFor(ENDED_BOUND_SECONDS, TimeUnit.SECONDS);
            }
        }
    }

    /** A process as the system would name it, with a pid and a start instant, {@code null} where it gives none. */
    private record Fake(long pid, Instant start) implements ProcessHandle {

        @Override
        public Optional<ProcessHandle> parent() {
            return Optional.empty();
        }

        @Override
        public Stream<ProcessHandle> children() {
            return Stream.empty();
        }

        @Override
        public Stream<ProcessHandle> descendants() {
            return Stream.empty();
        }

        @Override
        public Info info() {
            return new Info() {
                @Override
                public Optional<String> command() {
                    return Optional.empty();
                }

                @Override
                public Optional<String> commandLine() {
                    return Optional.empty();
                }

                @Override
                public Optional<String[]> arguments() {
                    return Optional.empty();
                }

                @Override
                public Optional<Instant> startInstant() {
                    return Optional.ofNullable(start);
                }

                @Override
                public Optional<Duration> totalCpuDuration() {
                    return Optional.empty();
                }

                @Override
                public Optional<String> user() {
                    return Optional.empty();
                }
            };
        }

        @Override
        public CompletableFuture<ProcessHandle> onExit() {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public boolean supportsNormalTermination() {
            return true;
        }

        @Override
        public boolean destroy() {
            return false;
        }

        @Override
        public boolean destroyForcibly() {
            return false;
        }

        @Override
        public boolean isAlive() {
            return false;
        }

        @Override
        public int compareTo(ProcessHandle other) {
            return Long.compare(pid, other.pid());
        }
    }
}
