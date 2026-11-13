package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * lock. The state also tells whether a Confluence rendering is drawn under the working directory and the output folder
 * it is given, as the window's fields name them and not as the launch values hold them (ADR-344 §1, §3), and reads the
 * working directory's {@code deliverable} folder, named as the output folder, as no folder named (ADR-334 §1).
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
        ControlPanelState state = aState(folder);
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

    /**
     * A start is refused where the working directory it passes is held by another invocation (ADR-278 §4, ADR-344 §3):
     * the one the window passes, which may have come from the environment and be kept nowhere, and not the one the state
     * held before. The holder is a live process of the test's own, named by a holder line as the jar writes one.
     */
    @Test
    @Issue("700")
    @Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
    @Link(name = "ADR-278", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_AS_ITS_CHILD_AND_STOPS_ONLY_ITS_OWN, type = "adr")
    @Story("The control panel starts no invocation where another holds the working directory")
    @DisplayName("A start is refused where the working directory it passes is held by another invocation, whatever the one held before")
    void aStartIsRefusedWhereTheWorkingDirectoryItPassesIsHeld(@TempDir Path folder) throws Exception {
        Path corpusRoot = Files.createDirectory(folder.resolve("corpus"));
        Path free = Files.createDirectory(folder.resolve("free"));
        Path held = Files.createDirectory(folder.resolve("held"));
        Process holder = ChildForTests.builder("sleep").start();
        try {
            Files.writeString(held.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME), "pid=" + holder.pid() + " started="
                    + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS))
                    + " command=run\n", StandardCharsets.UTF_8);
            ControlPanelState passingTheHeldOne = new ControlPanelState(SleepingJar.runtime(),
                    LaunchValues.fileIn(Files.createDirectories(folder.resolve("tmp-one")), USER),
                    Files.createDirectories(folder.resolve("root-locks-one")), USER);
            passingTheHeldOne.hold(new LaunchValues(corpusRoot.toString(), free.toString(), "", "", "", "", "", ""));
            ControlPanelState passingTheFreeOne = new ControlPanelState(SleepingJar.runtime(),
                    LaunchValues.fileIn(Files.createDirectories(folder.resolve("tmp-two")), USER),
                    Files.createDirectories(folder.resolve("root-locks-two")), USER);
            passingTheFreeOne.hold(new LaunchValues(corpusRoot.toString(), held.toString(), "", "", "", "", "", ""));
            Optional<String> refusedForTheHeldOne;
            boolean startedForTheHeldOne;
            Optional<String> refusedForTheFreeOne;
            boolean startedForTheFreeOne;
            try {
                refusedForTheHeldOne = passingTheHeldOne.start(ControlPanelState.Start.RUN, passing(corpusRoot, held),
                        new ArrayList<>());
                startedForTheHeldOne = passingTheHeldOne.childWasStarted();
                refusedForTheFreeOne = passingTheFreeOne.start(ControlPanelState.Start.RUN, passing(corpusRoot, free),
                        new ArrayList<>());
                startedForTheFreeOne = passingTheFreeOne.childWasStarted();
            } finally {
                passingTheHeldOne.stop();
                passingTheFreeOne.stop();
            }

            claim("passing a working directory another invocation holds, while the one held before is free, is refused,"
                            + " naming the one passed, and starts nothing",
                    () -> {
                        assertThat(refusedForTheHeldOne).hasValueSatisfying(line -> assertThat(line).startsWith(
                                "Run is not started: the working directory " + held
                                        + " is held by another Vespera invocation ("));
                        assertThat(startedForTheHeldOne).isFalse();
                    });
            claim("and passing a free one, while the one held before is held, starts the invocation",
                    () -> {
                        assertThat(refusedForTheFreeOne).isEmpty();
                        assertThat(startedForTheFreeOne).isTrue();
                    });
        } finally {
            holder.destroyForcibly().waitFor(BOUND.toSeconds(), TimeUnit.SECONDS);
        }
    }

    /**
     * What the window passes for a Run whose corpus root and working directory came from the environment: both passed,
     * as the command's own arguments, and neither kept (ADR-344 §3).
     */
    private static ControlPanelState.Plan passing(Path corpusRoot, Path workingDirectory) {
        return new ControlPanelState.Plan(
                new LaunchValues(corpusRoot.toString(), workingDirectory.toString(), "", "", "", "", "", ""),
                new LaunchValues("", "", "", "", "", "", "", ""), Map.of());
    }

    /** A control panel's state with no invocation started, its launch values file and lock folder under {@code folder}. */
    private static ControlPanelState aState(Path folder) throws IOException {
        return new ControlPanelState(
                new ChildRuntime(Path.of(System.getProperty("java.home"), "bin",
                        System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java"),
                        folder.resolve("no-such.jar")),
                LaunchValues.fileIn(Files.createDirectory(folder.resolve("tmp")), USER),
                Files.createDirectory(folder.resolve("root-locks")), USER);
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
