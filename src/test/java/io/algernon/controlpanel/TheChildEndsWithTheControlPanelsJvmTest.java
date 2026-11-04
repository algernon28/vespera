package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The invocation the control panel started ends with the control panel's JVM (ADR-307 §1, which ADR-339 keeps): the
 * shutdown hook {@code ControlPanel.endChildWithThisJvm} registers kills the child, with its descendants, as the JVM
 * ends in an orderly way; and no invocation is started once it has. Held with no window and no server, through the
 * state and the hook alone, which is all ADR-307 rests on.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-307", url = Adr.THE_CHILD_ENDS_WITH_THE_CONTROL_PANEL, type = "adr")
@Link(name = "ADR-339", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
class TheChildEndsWithTheControlPanelsJvmTest {

    private static final String USER = System.getProperty("user.name");

    /** How long the test waits for the child to start and for the control panel's JVM to end. */
    private static final Duration BOUND = Duration.ofSeconds(30);

    /** How long the test waits between two looks for the child. */
    private static final long POLL_MILLIS = 100;

    /**
     * A control panel's state in a JVM of its own, its child started and its hook registered, is ended by
     * {@link Process#destroy()}, which on Linux and macOS sends it {@code SIGTERM}, an orderly end that runs its hooks,
     * and is sent to it alone and not to its child.
     *
     * <p>Guarded off Windows: there {@code destroy()} is {@code TerminateProcess}, a forced end that runs no hook, which
     * ADR-307 §2 says leaves the child running, and Java sends no console event to another process.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    @Story("The control panel ends its own child")
    @DisplayName("Ending the control panel's JVM in an orderly way ends the invocation it started")
    void endingTheControlPanelsJvmInAnOrderlyWayEndsTheChildItStarted(@TempDir Path folder) throws Exception {
        Path corpusRoot = Files.createDirectory(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectory(folder.resolve("working"));
        Process controlPanel = new ProcessBuilder(
                        ProcessHandle.current().info().command()
                                .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin", "java").toString()),
                        "-cp", System.getProperty("java.class.path"), AChildStartedForTests.class.getName(),
                        folder.toString(), corpusRoot.toString(), workingDirectory.toString())
                .redirectErrorStream(true)
                .start();
        List<ProcessHandle> started = new ArrayList<>();
        try {
            waitForTheLine(controlPanel);
            started.addAll(childrenOf(controlPanel));
            boolean childRanBeforeTheEnd = started.size() == 1 && started.getFirst().isAlive();

            controlPanel.destroy();
            boolean controlPanelEnded = controlPanel.waitFor(BOUND.toMillis(), TimeUnit.MILLISECONDS);

            claim("the state started one invocation, a child of the control panel's JVM, which ran until the JVM was"
                    + " ended", () -> assertThat(childRanBeforeTheEnd).isTrue());
            claim("the control panel's JVM ended within " + BOUND.toSeconds() + " seconds of being asked to",
                    () -> assertThat(controlPanelEnded).isTrue());
            claim("and once it had, its invocation was gone too",
                    () -> assertThat(started.getFirst().isAlive()).isFalse());
        } finally {
            started.forEach(ProcessHandle::destroyForcibly);
            controlPanel.destroyForcibly();
            controlPanel.waitFor(BOUND.toMillis(), TimeUnit.MILLISECONDS);
            for (ProcessHandle process : started) {
                process.onExit().get(BOUND.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    @Test
    @Story("The control panel ends its own child")
    @DisplayName("Once the control panel is ending, Run starts no invocation and says why")
    void noChildIsStartedOnceTheControlPanelIsEnding(@TempDir Path folder) throws Exception {
        Path launchValues = LaunchValues.fileIn(Files.createDirectories(folder.resolve("tmp")), USER);
        new LaunchValues(Files.createDirectory(folder.resolve("corpus")).toString(),
                Files.createDirectory(folder.resolve("working")).toString(), "", "", "", "", "", "")
                .store(launchValues, USER, line -> {
                    throw new AssertionError("the launch values were not stored: " + line);
                });
        ControlPanelState state = new ControlPanelState(SleepingJar.runtime(), launchValues,
                Files.createDirectory(folder.resolve("root-locks")), USER);

        state.endWithTheControlPanel();
        Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());

        claim("Run is refused with the line saying the control panel is ending",
                () -> assertThat(refusal).contains("Run is not started: the control panel is ending"));
        claim("and no invocation was started", () -> assertThat(state.childWasStarted()).isFalse());
    }

    /** Waits for the line {@link AChildStartedForTests} prints once its child runs; fails where none comes in time. */
    private static void waitForTheLine(Process controlPanel) throws Exception {
        CompletableFuture<Boolean> line = CompletableFuture.supplyAsync(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(controlPanel.getInputStream(), StandardCharsets.UTF_8));
                String read;
                while ((read = reader.readLine()) != null) {
                    if (read.equals(AChildStartedForTests.STARTED)) {
                        return true;
                    }
                }
                return false;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        try {
            if (!line.get(BOUND.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("the control panel's JVM ended without starting its child");
            }
        } catch (TimeoutException notInTime) {
            throw new AssertionError("the control panel's JVM started no child within " + BOUND.toSeconds() + " seconds");
        }
    }

    /** The control panel's children once it has one, or none once the bound has passed. */
    private static List<ProcessHandle> childrenOf(Process controlPanel) throws InterruptedException {
        long giveUp = System.nanoTime() + BOUND.toNanos();
        List<ProcessHandle> children = controlPanel.children().toList();
        while (children.isEmpty() && System.nanoTime() < giveUp) {
            Thread.sleep(POLL_MILLIS);
            children = controlPanel.children().toList();
        }
        return children;
    }
}
