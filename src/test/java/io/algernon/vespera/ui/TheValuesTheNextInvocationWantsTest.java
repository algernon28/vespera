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
import javafx.scene.control.Control;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The values the next start of Vespera wants, drawn beneath the actions with a Save of their own (ADR-344 §1): after a
 * Save that writes, each of their rows is loaded again from the file as it then stands (ADR-377 §1), so that a second
 * Save compares the form with what the first wrote and is not refused for a change it made itself (ADR-330 §5).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("803")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class TheValuesTheNextInvocationWantsTest {

    /** The words a value changed in the window is saved with (ADR-343 §1). */
    private static final String FILLED_IN = "set in the control panel";

    /** The value the next start wants. */
    private static final ProfileKey WANTED = ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR;

    /** A provenance an operator wrote by hand. */
    private static final String THE_OPERATORS_WORDS = "read off the frequency table";

    @Test
    @Story("The values the next start wants are saved beneath the actions")
    @DisplayName("A wanted value saved is loaded again, so that it can be changed and saved a second time")
    void aWantedValueSavedIsLoadedAgainAndSavedASecondTime(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Files.writeString(working.resolve(ProfileEdit.FILE_NAME),
                ProfileEdit.rewrite(Optional.empty(), WANTED, "0.4", THE_OPERATORS_WORDS), StandardCharsets.UTF_8);
        Files.writeString(working.resolve(NextInvocation.FILE_NAME),
                "invocation=run\nwants=" + WANTED.key() + "\nanswer=false\n", StandardCharsets.UTF_8);
        ControlPanelState state = WindowForTests.aState(folder, EndingJar.runtime());
        state.hold(new LaunchValues(corpus.toString(), working.toString(), "", "", "", "", "", ""));
        String id = "wanted-" + WANTED.key();
        Stated first;
        Stated second;
        String[] tooltip = new String[1];
        String[] outcome = new String[1];
        try (WindowForTests window = WindowForTests.shown(state)) {
            assumeThat(state.start(ControlPanelState.Start.RUN, new ArrayList<>()))
                    .as("the child that ends at once is started").isEmpty();
            boolean drawn = window.awaiting(root -> HeadlessToolkit.nodeWithId(root, id).isPresent(),
                    Boolean::booleanValue);
            assumeThat(drawn).as("the wanted value's row is drawn once the invocation has ended").isTrue();
            window.act(root -> {
                WindowForTests.node(root, id, Spinner.class).getEditor().setText("0.5");
                WindowForTests.node(root, "wanted-save", Button.class).fire();
            });
            first = savedIn(working);
            window.act(root -> {
                Tooltip tip = WindowForTests.node(root, id + ".title", Control.class).getTooltip();
                tooltip[0] = tip == null ? null : tip.getText();
                WindowForTests.node(root, id, Spinner.class).getEditor().setText("0.6");
                WindowForTests.node(root, "wanted-save", Button.class).fire();
            });
            window.act(root -> outcome[0] = WindowForTests.node(root, "outcome", TextInputControl.class).getText());
            second = savedIn(working);
        }

        claim("the first Save writes the value with the filled-in words",
                () -> assertThat(first).isEqualTo(new Stated("0.5", FILLED_IN)));
        claim("the row, loaded again, has its title's tooltip end by saying the value was set in the control panel",
                () -> assertThat(tooltip[0]).endsWith("\n\nWhere this value came from: " + FILLED_IN));
        claim("the second Save writes the second value",
                () -> assertThat(second).isEqualTo(new Stated("0.6", FILLED_IN)));
        claim("and says it saved it, with no refusal",
                () -> assertThat(outcome[0]).startsWith(WANTED.key() + " saved in ").doesNotContain("not saved"));
    }

    private static Stated savedIn(Path working) throws Exception {
        return ProfileEdit.read(Files.readString(working.resolve(ProfileEdit.FILE_NAME), StandardCharsets.UTF_8))
                .get(WANTED);
    }
}
