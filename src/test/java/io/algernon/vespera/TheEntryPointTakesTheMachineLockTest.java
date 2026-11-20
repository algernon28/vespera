package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.engine.pipeline.DisplayPresence;
import io.algernon.vespera.engine.pipeline.MachineLock;
import io.algernon.vespera.ui.ControlPanel;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The jar's entry point takes the machine lock before it hands a start on (ADR-395 §4): as the command for a command
 * line whose first argument, a first {@code --headless} dropped, is {@code run}, {@code label} or {@code publish}, and
 * as the control panel for a start with no argument once a display is found, before the window; a start refused writes
 * its one line on standard error and exits 1, and nothing after it runs. {@code --help} and {@code --version} are
 * answered before the lock is asked and before the command line (ADR-436 §1), so they take none.
 *
 * <p>{@code EntryPoint.start} is given every seam: the display, the lock, the window, the command line, standard error
 * and the exit. Each records what it was asked, in one list, so the order of the lock, the window and the command line
 * is read off it. What the lock itself does is {@code MachineLockTest}'s; the packaged jar's refusal is {@code
 * CliExitIT}'s.
 */
@Epic("Architecture")
@Feature("One Vespera on a machine")
@Issue("818")
@Link(name = "ADR-395", url = Adr.ONE_VESPERA_RUNS_ON_A_MACHINE_AT_A_TIME, type = "adr")
class TheEntryPointTakesTheMachineLockTest {

    /** A refusal's line as the lock would answer it; its words are {@code MachineLockTest}'s to hold. */
    private static final String REFUSED = "another Vespera is running on this machine (pid=4242 started="
            + "2026-11-15T09:00:00+01:00 command= kind=control-panel); wait for it to end, or stop it or close its"
            + " control panel, then run the same command again. One Vespera runs on a machine at a time, so that none"
            + " stops the sidecars another is using.";

    /**
     * The value of {@code VESPERA_CORPUS_ROOT_LOCK_FOLDER} that is no path on Windows, which refuses every character of
     * it in a file name; {@code MachineLockTest} holds the line a lock answers for such a value.
     */
    private static final String NO_PATH = "<>|?*\"";

    /** The line a lock asked about that folder answers (ADR-395 §5), quoting it as written. */
    private static final String NO_PATH_REFUSED = "the machine lock could not be taken in the folder " + NO_PATH
            + " (Illegal char <<> at index 0: " + NO_PATH + "); make that folder one this user can write, or name"
            + " another in VESPERA_CORPUS_ROOT_LOCK_FOLDER, then run the same command again. Vespera does not start"
            + " while it cannot tell whether another Vespera is running.";

    /** The exit code of a refused start (ADR-141). */
    private static final int REFUSED_EXIT = 1;

    /** The exit code of {@code --help} or {@code --version}, answered before Spring (ADR-436 §1). */
    private static final int ANSWERED_EXIT = 0;

    /** The exit code of a window that opened and was closed (ADR-342 §2). */
    private static final int CLOSED_EXIT = 0;

    private static final DisplayPresence.Answer A_DISPLAY = new DisplayPresence.Answer(true, "display-variable", null);

    private static final DisplayPresence.Answer NO_DISPLAY =
            new DisplayPresence.Answer(false, "no-display-variable", null);

    /** What a start did, each seam's call in the order made, and standard error. */
    private record Did(List<String> calls, String err) {
    }

    /** What the JVM's headless property held before the test: a start with --headless first sets it. */
    private String headlessBefore;

    @BeforeEach
    void rememberTheHeadlessProperty() {
        headlessBefore = System.getProperty(DisplayPresence.HEADLESS_PROPERTY);
    }

    @AfterEach
    void putTheHeadlessPropertyBack() {
        if (headlessBefore == null) {
            System.clearProperty(DisplayPresence.HEADLESS_PROPERTY);
        } else {
            System.setProperty(DisplayPresence.HEADLESS_PROPERTY, headlessBefore);
        }
    }

    @Test
    @Story("One Vespera runs on a machine at a time")
    @DisplayName("Run, label and publish take the machine lock as their kind before the command line starts, and help, version, serve and no command take none")
    @Issue("905")
    @Link(name = "ADR-436", url = Adr.NOTHING_USES_A_WORKING_DIRECTORY_NOTHING_NAMES, type = "adr")
    void aCommandThatUsesTheSidecarsTakesTheMachineLockFirst() {
        claim("run, label and publish, named first or after --headless, each take the lock as their kind and then start"
                        + " the command line with the arguments it is given, and nothing exits the process",
                () -> assertThat(List.of(
                                started(new String[] {"run", "D:/corpus"}, A_DISPLAY, Optional.empty()).calls(),
                                started(new String[] {"--headless", "label", "--auto"}, A_DISPLAY, Optional.empty())
                                        .calls(),
                                started(new String[] {"publish", "--to", "website"}, NO_DISPLAY, Optional.empty())
                                        .calls()))
                        .containsExactly(
                                List.of("lock RUN", "command line [run, D:/corpus]"),
                                List.of("lock LABEL", "command line [label, --auto]"),
                                List.of("lock PUBLISH", "command line [publish, --to, website]")));
        claim("--version and --help take no lock and are answered before the command line, exiting " + ANSWERED_EXIT
                        + "; serve and --headless alone take no lock and start the command line as before",
                () -> assertThat(List.of(
                                started(new String[] {"--version"}, A_DISPLAY, Optional.empty()).calls(),
                                started(new String[] {"--help"}, A_DISPLAY, Optional.empty()).calls(),
                                started(new String[] {"serve", "--db-dir=D:/work"}, A_DISPLAY, Optional.empty()).calls(),
                                started(new String[] {"--headless"}, A_DISPLAY, Optional.empty()).calls()))
                        .containsExactly(
                                List.of("exit " + ANSWERED_EXIT),
                                List.of("exit " + ANSWERED_EXIT),
                                List.of("command line [serve, --db-dir=D:/work]"),
                                List.of("command line []")));
    }

    @Test
    @Story("One Vespera runs on a machine at a time")
    @DisplayName("A command refused by the machine lock says its one line on standard error and exits 1, and the command line never starts")
    void aRefusedCommandSaysOneLineAndExitsOneBeforeTheCommandLine() {
        Did did = started(new String[] {"run", "D:/corpus"}, A_DISPLAY, Optional.of(REFUSED));

        claim("the lock was asked for run, then the process was made to exit " + REFUSED_EXIT + ", and the command line"
                        + " was not started",
                () -> assertThat(did.calls()).containsExactly("lock RUN", "exit " + REFUSED_EXIT));
        claim("standard error holds the refusal's line and nothing else",
                () -> assertThat(did.err()).isEqualTo(REFUSED + System.lineSeparator()));
    }

    @Test
    @Story("One Vespera runs on a machine at a time")
    @DisplayName("The control panel takes the machine lock once a display is found and before its window, and with no display takes none")
    void theControlPanelTakesTheMachineLockAfterTheDisplayAndBeforeTheWindow() {
        Did shown = started(new String[0], A_DISPLAY, Optional.empty());
        claim("with a display, the lock was taken as the control panel, then the window was opened, and its closing"
                        + " exited " + CLOSED_EXIT,
                () -> assertThat(shown.calls()).containsExactly("lock CONTROL_PANEL", "window", "exit " + CLOSED_EXIT));

        Did none = started(new String[0], NO_DISPLAY, Optional.empty());
        claim("with no display, no lock was taken and the command line was started with no argument",
                () -> assertThat(none.calls()).containsExactly("command line []"));
    }

    @Test
    @Story("One Vespera runs on a machine at a time")
    @DisplayName("A second control panel is refused in one line on standard error, exits 1, and tries no window")
    void aSecondControlPanelIsRefusedInOneLineAndTriesNoWindow() {
        Did did = started(new String[0], A_DISPLAY, Optional.of(REFUSED));

        claim("the lock was asked for the control panel, then the process was made to exit " + REFUSED_EXIT + ": no"
                        + " window was tried and no command line started",
                () -> assertThat(did.calls()).containsExactly("lock CONTROL_PANEL", "exit " + REFUSED_EXIT));
        claim("standard error holds the refusal's line and nothing else",
                () -> assertThat(did.err()).isEqualTo(REFUSED + System.lineSeparator()));
    }

    @Test
    @Story("One Vespera runs on a machine at a time")
    @DisplayName("A folder of the locks that is no path refuses only a start that takes the lock: version, help and a start with no display take nothing")
    @Issue("905")
    @Link(name = "ADR-436", url = Adr.NOTHING_USES_A_WORKING_DIRECTORY_NOTHING_NAMES, type = "adr")
    void aFolderThatIsNoPathRefusesOnlyAStartThatTakesTheLock() {
        claim("--version and --help never ask the lock and are answered before the command line, exiting "
                        + ANSWERED_EXIT + ", and a start with no argument and no display never asks it and starts the"
                        + " command line as before",
                () -> assertThat(List.of(
                                started(new String[] {"--version"}, A_DISPLAY, Optional.of(NO_PATH_REFUSED)).calls(),
                                started(new String[] {"--help"}, A_DISPLAY, Optional.of(NO_PATH_REFUSED)).calls(),
                                started(new String[0], NO_DISPLAY, Optional.of(NO_PATH_REFUSED)).calls()))
                        .containsExactly(
                                List.of("exit " + ANSWERED_EXIT),
                                List.of("exit " + ANSWERED_EXIT),
                                List.of("command line []")));

        Did run = started(new String[] {"run", "D:/corpus"}, A_DISPLAY, Optional.of(NO_PATH_REFUSED));
        claim("a run asks the lock, and is refused in its line quoting the folder as written, exit " + REFUSED_EXIT
                        + ", and the command line never starts",
                () -> assertThat(List.of(run.calls(), run.err())).containsExactly(
                        List.of("lock RUN", "exit " + REFUSED_EXIT), NO_PATH_REFUSED + System.lineSeparator()));
    }

    /**
     * Starts {@code arguments} with {@code display}, a lock answering {@code refusal} and a window that opens and closes.
     * The exit seam records its code and returns, so a test reads what would have followed it.
     */
    private static Did started(String[] arguments, DisplayPresence.Answer display, Optional<String> refusal) {
        List<String> calls = new CopyOnWriteArrayList<>();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        EntryPoint.start(arguments, () -> display,
                (MachineLock.Kind kind) -> {
                    calls.add("lock " + kind.name());
                    return refusal;
                },
                () -> {
                    calls.add("window");
                    return new ControlPanel.Opening(ControlPanel.Opening.Result.CLOSED, "");
                },
                passed -> calls.add("command line " + List.of(passed)),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                code -> calls.add("exit " + code));
        return new Did(List.copyOf(calls), err.toString(StandardCharsets.UTF_8));
    }
}
