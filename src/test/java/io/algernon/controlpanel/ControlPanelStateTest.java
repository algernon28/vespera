package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel's writes and its starts one at a time (ADR-295 §1, ADR-306 §1): the server answers each request on
 * a thread of its own, and since a holder line of the control panel's own process reads as held by nobody, only the
 * {@link ControlPanelState}'s monitor keeps a start from passing its checks while a write holds the working-directory
 * lock. The state also tells whether a Confluence rendering is drawn, and reads the working directory's
 * {@code deliverable} folder, named as the output folder, as no folder named (ADR-334 §1).
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

    /** A generation run's folder, as the trees under the working directory's {@code deliverable} folder are named. */
    private static final String A_RUN = "abcdef0123456789";

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
     * The working directory's {@code deliverable} folder named as the output folder is read as none named (ADR-334 §1):
     * whether a Confluence rendering is drawn (ADR-308 §6) is asked of each run's folder under it, as where none is
     * named, and not of the folder itself. Another folder is the tree itself, as before.
     */
    @Test
    @Story("The operator publishes from the control panel")
    @DisplayName("Naming the working directory's deliverable folder as the output folder finds a rendering drawn in a run's folder under it, as naming none does")
    @Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
    void theDefaultFolderNamedFindsARenderingAsNoneNamedDoes(@TempDir Path folder) throws IOException {
        Path workingDirectory = Files.createDirectory(folder.resolve("working"));
        Path corpusRoot = Files.createDirectory(folder.resolve("corpus"));
        Path theDefault = workingDirectory.resolve("deliverable");
        aRenderingDrawnAtTheTopOf(theDefault.resolve(A_RUN));
        Path elsewhere = folder.resolve("elsewhere");
        aRenderingDrawnAtTheTopOf(elsewhere.resolve(A_RUN));
        Map<String, String> named = new LinkedHashMap<>();
        named.put("no output folder named", "");
        named.put("the working directory's deliverable folder named", theDefault.toString());
        named.put("that folder named with a separator after it", theDefault + File.separator);
        named.put("that folder named by way of a folder and ..",
                workingDirectory.resolve("elsewhere").resolve("..").resolve("deliverable").toString());
        named.put("another folder named, holding a run's folder but no rendering at its own top", elsewhere.toString());
        Path aTree = folder.resolve("a-tree");
        aRenderingDrawnAtTheTopOf(aTree);
        named.put("another folder named, holding a rendering at its own top", aTree.toString());
        ControlPanelState state = aState(folder);

        Map<String, Boolean> drawn = drawnFor(state, corpusRoot, workingDirectory, named);

        claim("with none named, and with the working directory's deliverable folder named however it is spelt, the"
                        + " rendering in a run's folder under it is found; another folder named is the tree itself, so"
                        + " a rendering is found only at its own top",
                () -> assertThat(drawn).containsExactly(
                        Map.entry("no output folder named", true),
                        Map.entry("the working directory's deliverable folder named", true),
                        Map.entry("that folder named with a separator after it", true),
                        Map.entry("that folder named by way of a folder and ..", true),
                        Map.entry("another folder named, holding a run's folder but no rendering at its own top", false),
                        Map.entry("another folder named, holding a rendering at its own top", true)));
    }

    /** As {@link #theDefaultFolderNamedFindsARenderingAsNoneNamedDoes}, names being compared without regard to case on Windows (ADR-334 §1, ADR-276 §2). */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Story("The operator publishes from the control panel")
    @DisplayName("On Windows the working directory's deliverable folder named in capitals finds a rendering drawn in a run's folder under it, as naming none does")
    @Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
    void onWindowsTheDefaultFolderInCapitalsFindsARendering(@TempDir Path folder) throws IOException {
        Path workingDirectory = Files.createDirectory(folder.resolve("working"));
        Path corpusRoot = Files.createDirectory(folder.resolve("corpus"));
        aRenderingDrawnAtTheTopOf(workingDirectory.resolve("deliverable").resolve(A_RUN));
        Map<String, String> named = new LinkedHashMap<>();
        named.put("the deliverable folder named in capitals", workingDirectory.resolve("DELIVERABLE").toString());

        Map<String, Boolean> drawn = drawnFor(aState(folder), corpusRoot, workingDirectory, named);

        claim("the folder named in capitals is the working directory's deliverable folder, and the rendering in a run's"
                        + " folder under it is found",
                () -> assertThat(drawn).containsExactly(Map.entry("the deliverable folder named in capitals", true)));
    }

    /** Whether a rendering is drawn, by what each output folder named is called, the state holding it as launch value. */
    private static Map<String, Boolean> drawnFor(ControlPanelState state, Path corpusRoot, Path workingDirectory,
            Map<String, String> named) {
        Map<String, Boolean> drawn = new LinkedHashMap<>();
        for (Map.Entry<String, String> each : named.entrySet()) {
            state.hold(new LaunchValues(corpusRoot.toString(), workingDirectory.toString(), each.getValue(), "", "", "",
                    "", ""));
            drawn.put(each.getKey(), state.aRenderingIsDrawn());
        }
        return drawn;
    }

    /** A Confluence rendering's page list at the top of {@code tree}, as each run's tree holds it. */
    private static void aRenderingDrawnAtTheTopOf(Path tree) throws IOException {
        Path confluence = Files.createDirectories(tree.resolve("confluence"));
        Files.writeString(confluence.resolve("pages.json"), "{}", StandardCharsets.UTF_8);
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
