package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel's writes and its starts one at a time (ADR-295 §1, ADR-306 §1): the server answers each request on
 * a thread of its own, and since a holder line of the control panel's own process reads as held by nobody, only the
 * {@link ControlPanelState}'s monitor keeps a start from passing its checks while a write holds the working-directory
 * lock.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("629")
@Link(name = "ADR-295", url = Adr.THE_CONTROL_PANEL_WRITES_HOLDING_THE_WORKING_DIRECTORY_LOCK, type = "adr")
@Link(name = "ADR-306", url = Adr.THE_CONTROL_PANELS_OWN_HOLDER_LINE_IS_HELD_BY_NOBODY, type = "adr")
class ControlPanelStateTest {

    private static final String USER = System.getProperty("user.name");

    /** How long the test waits for either thread before it fails. */
    private static final Duration BOUND = Duration.ofSeconds(10);

    @Test
    @Story("The control panel writes into a working directory")
    @DisplayName("No invocation is started while a save of the control panel's holds the working directory's lock")
    void noChildIsStartedWhileAWriteHoldsTheWorkingDirectoryLock(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectory(folder.resolve("working"));
        Path corpusRoot = Files.createDirectory(folder.resolve("corpus"));
        ControlPanelState state = new ControlPanelState(
                new ChildRuntime(Path.of(System.getProperty("java.home"), "bin",
                        System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java"),
                        folder.resolve("no-such.jar")),
                LaunchValues.fileIn(Files.createDirectory(folder.resolve("tmp")), USER),
                Files.createDirectory(folder.resolve("root-locks")), USER);
        state.hold(new LaunchValues(corpusRoot.toString(), workingDirectory.toString(), "", "", "", "", "", ""));

        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Optional<String>> written = new AtomicReference<>();
        Thread write = Thread.ofPlatform().start(() -> written.set(state.write(workingDirectory, "profile.yaml",
                current -> {
                    writing.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return "---\n";
                })));
        assertThat(writing.await(BOUND.toSeconds(), TimeUnit.SECONDS)).isTrue();

        AtomicReference<Optional<String>> started = new AtomicReference<>();
        List<String> stored = new ArrayList<>();
        Thread start = Thread.ofPlatform().start(() -> started.set(state.start(ControlPanelState.Start.RUN, stored)));
        Thread.State whileWriting = settled(start);

        claim("while the save's change waits inside the working directory's lock, Run waits for the save to end"
                        + " and has neither started an invocation nor been refused",
                () -> {
                    assertThat(whileWriting).isEqualTo(Thread.State.BLOCKED);
                    assertThat(started.get()).isNull();
                });

        release.countDown();
        write.join(BOUND);
        start.join(BOUND);
        try {
            claim("once the save has ended, it has written the file, and Run then starts the invocation",
                    () -> {
                        assertThat(written.get()).isEmpty();
                        assertThat(started.get()).isEmpty();
                        assertThat(state.childWasStarted()).isTrue();
                    });
        } finally {
            state.stop();
        }
    }

    /** The state {@code thread} comes to rest in: waiting on a monitor, or ended; the test fails where it does neither. */
    private static Thread.State settled(Thread thread) throws InterruptedException {
        Instant until = Instant.now().plus(BOUND);
        while (Instant.now().isBefore(until)) {
            Thread.State now = thread.getState();
            if (now == Thread.State.BLOCKED || now == Thread.State.TERMINATED) {
                return now;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("the thread asking for Run neither waited on the control panel nor ended within "
                + BOUND.toSeconds() + " seconds");
    }
}
