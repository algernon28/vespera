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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * The invocation the control panel started ends with the control panel (ADR-307 §1): its shutdown hook kills the
 * child, with its descendants, as the control panel's JVM ends in an orderly way, and waits up to
 * {@link ControlPanelState#END_BOUND} to see it end; and no invocation is started once it has.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("629")
@Link(name = "ADR-307", url = Adr.THE_CHILD_ENDS_WITH_THE_CONTROL_PANEL, type = "adr")
class TheChildEndsWithTheControlPanelTest {

    private static final String USER = System.getProperty("user.name");

    /** How long the test waits for the control panel to print its address, to start its child and to end. */
    private static final Duration BOUND = Duration.ofSeconds(30);

    /** How long the test waits between two looks for the child. */
    private static final long POLL_MILLIS = 100;

    /**
     * A control panel started as a process of its own, by {@link ControlPanelForTests}, Run pressed on its launch
     * page, is ended by {@link Process#destroy()}, which on Linux and macOS sends it {@code SIGTERM}, an orderly end
     * of its JVM that runs its shutdown hooks, and which is sent to the control panel alone and not to its child.
     *
     * <p>Guarded off Windows: there {@code destroy()} is {@code TerminateProcess}, a forced end that runs no hook, and
     * Java offers no way to send another process the console's {@code Ctrl+C} or close event. A forced end leaving the
     * child running is what ADR-307 states, and no test holds it.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    @Story("The control panel ends its own child")
    @DisplayName("Ending the control panel in an orderly way ends the invocation it started")
    void endingTheControlPanelInAnOrderlyWayEndsTheChildItStarted(@TempDir Path folder) throws Exception {
        Path corpusRoot = Files.createDirectory(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectory(folder.resolve("working"));
        Files.createDirectory(folder.resolve("root-locks"));
        Process controlPanel = new ProcessBuilder(
                        ProcessHandle.current().info().command()
                                .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin", "java").toString()),
                        "-cp", System.getProperty("java.class.path"), ControlPanelForTests.class.getName(),
                        folder.toString(), corpusRoot.toString(), workingDirectory.toString())
                .redirectErrorStream(true)
                .start();
        List<ProcessHandle> started = new ArrayList<>();
        try {
            URI address = addressPrintedBy(controlPanel);
            PanelBrowser.opening(address).press("launch", "run", Map.of());
            started.addAll(childrenOf(controlPanel));
            boolean childRanBeforeTheEnd = started.size() == 1 && started.getFirst().isAlive();

            controlPanel.destroy();
            boolean controlPanelEnded = controlPanel.waitFor(BOUND.toMillis(), TimeUnit.MILLISECONDS);

            claim("pressing Run started one invocation, a child of the control panel's, which ran until the control"
                    + " panel was ended", () -> assertThat(childRanBeforeTheEnd).isTrue());
            claim("the control panel ended within " + BOUND.toSeconds() + " seconds of being asked to",
                    () -> assertThat(controlPanelEnded).isTrue());
            claim("and once it had, its invocation was gone too: the control panel waits up to "
                            + ControlPanelState.END_BOUND.toSeconds() + " seconds for it before it ends",
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

    /**
     * The shutdown hook holds the control panel's state while it kills the child, and a request answered on another
     * thread in the moments the JVM ends could still press Run after it: such a start is refused.
     */
    @Test
    @Story("The control panel ends its own child")
    @DisplayName("Once the control panel is ending, Run starts no invocation and says why")
    void noChildIsStartedOnceTheControlPanelIsEnding(@TempDir Path folder) throws Exception {
        Path corpusRoot = Files.createDirectory(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectory(folder.resolve("working"));
        ControlPanelState state = new ControlPanelState(ScreenServer.runtime(),
                ScreenServer.storedIn(folder, ScreenServer.values(corpusRoot, workingDirectory), USER),
                Files.createDirectory(folder.resolve("root-locks")), USER);

        state.endWithTheControlPanel();
        Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());

        claim("Run is refused with the line saying the control panel is ending",
                () -> assertThat(refusal).contains("Run is not started: the control panel is ending"));
        claim("and no invocation was started", () -> assertThat(state.childWasStarted()).isFalse());
    }

    /** The address {@link ControlPanelForTests} prints; the test fails where it prints none within the bound. */
    private static URI addressPrintedBy(Process controlPanel) throws Exception {
        CompletableFuture<String> line = CompletableFuture.supplyAsync(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(controlPanel.getInputStream(), StandardCharsets.UTF_8));
                String read;
                while ((read = reader.readLine()) != null) {
                    if (read.startsWith(ControlPanelForTests.OPEN)) {
                        return read.substring(ControlPanelForTests.OPEN.length()).strip();
                    }
                }
                return null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        try {
            String address = line.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);
            if (address == null) {
                throw new AssertionError("the control panel ended without printing an address");
            }
            return URI.create(address);
        } catch (TimeoutException notInTime) {
            throw new AssertionError("the control panel printed no address within " + BOUND.toSeconds() + " seconds");
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
