package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ui.ProfileEdit.Stated;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import javafx.scene.control.Button;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Issue #869: the wanted rows are drawn as the window opens (ADR-404 §1), so a row being drawn says nothing of whether
 * the window's own child has ended; a Save pressed while it runs writes nothing (ADR-295), and the value already in
 * {@code profile.yaml} stays. The child here sleeps until it is stopped, so the interleaving the flaky first Save of
 * {@code TheValuesTheNextInvocationWantsTest} met on a slow machine is forced on every run.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("869")
@Link(name = "ADR-404", url = Adr.THE_VALUES_WANTED_ARE_READ_FROM_THE_WORKING_DIRECTORY_SHOWN, type = "adr")
@Link(name = "ADR-295", url = Adr.THE_CONTROL_PANEL_WRITES_HOLDING_THE_WORKING_DIRECTORY_LOCK, type = "adr")
class TheWantedRowsAreDrawnBeforeTheWindowsOwnChildEndsTest {

    private static final String FILLED_IN = "set in the control panel";

    private static final ProfileKey WANTED = ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR;

    private static final String THE_OPERATORS_WORDS = "read off the frequency table";

    @Test
    @Story("The values the next start wants are saved beneath the actions")
    @DisplayName("A wanted row is drawn while the window's own child runs, and its Save writes only once the child has ended")
    void aWantedRowIsDrawnWhileTheChildRunsAndItsSaveWritesOnlyOnceItHasEnded(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Files.writeString(working.resolve(ProfileEdit.FILE_NAME),
                ProfileEdit.rewrite(Optional.empty(), WANTED, "0.4", THE_OPERATORS_WORDS), StandardCharsets.UTF_8);
        Files.writeString(working.resolve(NextInvocation.FILE_NAME),
                "invocation=run\nwants=" + WANTED.key() + "\nanswer=false\n", StandardCharsets.UTF_8);
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(corpus.toString(), working.toString(), "", "", "", "", "", ""));
        String id = "wanted-" + WANTED.key();
        boolean drawn;
        boolean runningWhenDrawn;
        Stated whileItRuns;
        String refusal;
        boolean ended;
        Stated onceItEnded;
        String[] outcome = new String[1];
        try (WindowForTests window = WindowForTests.shown(state)) {
            assumeThat(state.start(ControlPanelState.Start.RUN, new ArrayList<>()))
                    .as("the sleeping child is started").isEmpty();
            // the wait TheValuesTheNextInvocationWantsTest makes, word for word
            drawn = window.awaiting(root -> HeadlessToolkit.nodeWithId(root, id).isPresent(), Boolean::booleanValue);
            runningWhenDrawn = state.childRuns();
            window.act(root -> {
                WindowForTests.node(root, id, Spinner.class).getEditor().setText("0.5");
                WindowForTests.node(root, "wanted-save", Button.class).fire();
                outcome[0] = WindowForTests.node(root, "outcome", TextInputControl.class).getText();
            });
            whileItRuns = savedIn(working);
            refusal = outcome[0];
            state.stop();
            ended = WindowForTests.within(() -> state.ending().isPresent());
            window.act(root -> {
                WindowForTests.node(root, id, Spinner.class).getEditor().setText("0.5");
                WindowForTests.node(root, "wanted-save", Button.class).fire();
            });
            onceItEnded = savedIn(working);
        }

        claim("the wanted row is drawn as the window opens, while the window's own child still runs",
                () -> {
                    assertThat(drawn).as("the row is drawn").isTrue();
                    assertThat(runningWhenDrawn).as("the child still runs when it is").isTrue();
                });
        claim("a Save pressed while the child runs leaves the value profile.yaml stated",
                () -> assertThat(whileItRuns).isEqualTo(new Stated("0.4", THE_OPERATORS_WORDS)));
        claim("and says nothing is written while the invocation the window started runs",
                () -> assertThat(refusal).contains("while the invocation it started runs")
                        .contains(ProfileEdit.FILE_NAME + " was not written"));
        claim("the child, stopped, has ended", () -> assertThat(ended).isTrue());
        claim("once the child has ended, the same Save writes the typed value with the filled-in words",
                () -> assertThat(onceItEnded).isEqualTo(new Stated("0.5", FILLED_IN)));
    }

    private static Stated savedIn(Path working) throws Exception {
        return ProfileEdit.read(Files.readString(working.resolve(ProfileEdit.FILE_NAME), StandardCharsets.UTF_8))
                .get(WANTED);
    }
}
