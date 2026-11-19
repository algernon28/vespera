package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The window draws its child, on each tick of its clock, from one look at it: the line above the tab says how the child
 * ended on the tick the Report tab first says so (ADR-278 section 5, ADR-362 section 8). A child that ended between
 * the window asking whether it runs and asking how it ended was drawn running beside its own ending for a whole tick.
 *
 * <p>The test makes that ending land where it did by chance on a slow machine: its child, {@link PrintingJars#printing()},
 * runs until it is killed, and is killed while the toolkit's thread is seen inside the clock's look at how the child
 * ended, {@code ControlPanelState.ending} called by the cockpit's tick itself, rather than inside a look at whether it
 * runs. The look waits a moment for the process to end, so the kill lands inside it.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("896")
@Link(name = "ADR-278", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_AS_ITS_CHILD_AND_STOPS_ONLY_ITS_OWN, type = "adr")
class AChildEndingAsTheWindowLooksIsDrawnEndedTest {

    /** How long the test watches the toolkit's thread for the clock's look at how the child ended: a tick is a second. */
    private static final Duration WATCHING = Duration.ofSeconds(20);

    /** The word the window says a child ended with when it was killed by no Stop of the window's. */
    private static final String FAILED_OR_REFUSED = "Failed or refused";

    /**
     * What the Report tab's status line and the line above the tab say, and whether the working-directory list can be
     * changed, read at one instant.
     */
    private record Said(String status, String child, boolean listChangeable) {
    }

    @Test
    @Story("The window says how its child ended")
    @DisplayName("A child that ends while the window looks at it is said to have ended on the tick the Report tab says it wrote no report, never to be running")
    void aChildEndingAsTheWindowLooksIsDrawnEndedOnTheTickItsReportIs(@TempDir Path folder) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, PrintingJars.printing());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));

        boolean caught;
        Said said;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.readEverySecond();
            assumeThat(state.start(ControlPanelState.Start.RUN, new ArrayList<>())).as("the child is started").isEmpty();
            // taken before the watch: the window's look holds the state's lock, so asking it then would wait the look out
            ProcessHandle child = ProcessHandle.of(state.watchedChild().orElseThrow().pid()).orElseThrow();
            Thread toolkit = HeadlessToolkit.onTheToolkit(Thread::currentThread);
            // killed before it prints, the child would have no last line to be said with, whatever the window does
            assertThat(WindowForTests.within(() -> state.tail().contains(APrintingChildForTests.ITS_LAST_LINE)))
                    .as("the child prints \"%s\" within the tests' bound, before it is killed",
                            APrintingChildForTests.ITS_LAST_LINE)
                    .isTrue();
            caught = caughtLookingAtHowTheChildEnded(toolkit);
            child.destroyForcibly();
            said = window.awaiting(root -> new Said(textOf(root, "report-status"), textOf(root, "child"),
                            listChangeable(root)),
                    each -> LiveReport.NO_REPORT.equals(each.status()));
        }
        Ending ending = state.ending().orElseThrow();
        String endedAs = state.childCommand() + ": " + FAILED_OR_REFUSED + ", exit " + ending.exitCode() + " ("
                + APrintingChildForTests.ITS_LAST_LINE + ")";

        claim("the window's clock is seen looking at how its child ended while the child still runs, within "
                        + WATCHING.toSeconds() + " seconds, twenty of its ticks, so that the child is killed inside that look",
                () -> assertThat(caught).isTrue());
        claim("on the tick the Report tab first says the invocation wrote no report, the line above the tab says the child"
                        + " failed, with its exit code and its last line of output, and not that it is running; and the"
                        + " working-directory list, with its New and Browse buttons, can be changed again, as Run can be"
                        + " pressed again (ADR-393 section 4)",
                () -> assertThat(said).isEqualTo(new Said(LiveReport.NO_REPORT, endedAs, true)));
    }

    /**
     * Whether the toolkit's thread is seen, within {@link #WATCHING}, inside {@code ControlPanelState.ending} called by
     * the cockpit's tick itself, {@code Cockpit.refresh}, and not by {@code ControlPanelState.childRuns}: the clock's
     * look at how the child ended. Looked at without pause, the look lasting as long as it waits for the process.
     */
    private static boolean caughtLookingAtHowTheChildEnded(Thread toolkit) {
        long end = System.nanoTime() + WATCHING.toNanos();
        while (System.nanoTime() < end) {
            if (looksAtHowTheChildEnded(toolkit.getStackTrace())) {
                return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }

    private static boolean looksAtHowTheChildEnded(StackTraceElement[] frames) {
        for (int i = 0; i + 1 < frames.length; i++) {
            if (frames[i].getClassName().equals(ControlPanelState.class.getName())
                    && frames[i].getMethodName().equals("ending")) {
                return frames[i + 1].getClassName().equals(Cockpit.class.getName())
                        && frames[i + 1].getMethodName().equals("refresh");
            }
        }
        return false;
    }

    /** Whether the working-directory list and its New and Browse buttons are all enabled. */
    private static boolean listChangeable(Parent root) {
        return !WindowForTests.node(root, "working-directories", ListView.class).isDisable()
                && !WindowForTests.node(root, "working-directory-new", Button.class).isDisable()
                && !WindowForTests.node(root, "working-directory-browse", Button.class).isDisable();
    }

    private static String textOf(Parent root, String id) {
        return WindowForTests.node(root, id, TextInputControl.class).getText();
    }
}
