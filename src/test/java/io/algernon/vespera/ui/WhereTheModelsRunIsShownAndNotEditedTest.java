package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where the models run is shown on the Profile tab and cannot be changed there (ADR-384 §7): its row's control shows
 * the value, {@code local} where {@code profile.yaml} states none, and is disabled; the pane holding it, which is not,
 * carries the tooltip, since JavaFX shows none over a disabled control; and a Save of the tab writes nothing of it, so a
 * file holding the key as the jar comments it out is left byte for byte as it was.
 *
 * <p>The file is written as the jar writes it while the key is unset: the key commented out in its place, beside one
 * value of another key. The ids are the row's as the Profile tab gives them, {@code profile-} before the key.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-384", url = Adr.WHETHER_THE_MODELS_RUN_LOCALLY_OR_IN_THE_CLOUD_IS_THE_PROFILES_TO_SAY, type = "adr")
class WhereTheModelsRunIsShownAndNotEditedTest {

    /** The row's control, by the id the Profile tab gives it. */
    private static final String THE_CONTROL = "profile-modelsRunOn";

    /** The pane holding the control, which carries its tooltip. */
    private static final String ITS_HOLDER = "profile-modelsRunOn.holder";

    /** The look-up key under which {@link Tooltip#install} keeps a node's tooltip among its properties. */
    private static final String TOOLTIP_KEY = "javafx.scene.control.Tooltip";

    /** The three lines the jar writes in the key's place while it is unset (ADR-384 §6). */
    private static final String THE_COMMENTED_KEY = "# Where the models run: local, on this machine. Cloud is not"
            + " supported yet.\n# modelsRunOn:\n#   value: local\n";

    /** What the tooltip says, as ADR-384 §7 words it. */
    private static final String NOT_SUPPORTED_YET = "Cloud is not supported yet.";

    /** What the row's control shows: the one value supported. */
    private static final String LOCAL = "local";

    /** A row's control the window shows, its value, whether it is disabled, and its holder's state and tooltip. */
    private record Shown(boolean disabled, Object value, boolean holderDisabled, Optional<String> tooltip) {
    }

    @Test
    @Story("Where the models run is the operator's to say")
    @DisplayName("Where the models run shows local, greyed out with a tooltip saying cloud is not supported yet, and a Save of the Profile tab leaves the file as it was")
    void theRowShowsItsValueGreyedOutAndASaveWritesNothingOfIt(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Path profile = working.resolve(ProfileEdit.FILE_NAME);
        Files.writeString(profile, ProfileEdit.rewrite(Optional.empty(), ProfileKey.GENERATION_MODEL,
                "a-writing-model:8b", "measured on the seed set") + THE_COMMENTED_KEY, StandardCharsets.UTF_8);
        byte[] asWritten = Files.readAllBytes(profile);
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(corpus.toString(), working.toString(), "", "", "", "", "", ""));

        Shown shown;
        String outcome;
        try (WindowForTests window = WindowForTests.shown(state)) {
            shown = window.awaiting(root -> HeadlessToolkit.nodeWithId(root, THE_CONTROL).map(control -> {
                Node holder = WindowForTests.node(root, ITS_HOLDER, Node.class);
                Object value = control instanceof ComboBox<?> box ? box.getValue() : null;
                Optional<String> tooltip = holder.getProperties().get(TOOLTIP_KEY) instanceof Tooltip said
                        ? Optional.ofNullable(said.getText())
                        : Optional.empty();
                return new Shown(control.isDisabled(), value, holder.isDisabled(), tooltip);
            }).orElse(null), drawn -> drawn != null);
            window.act(root -> WindowForTests.node(root, "profile-save", Button.class).fire());
            outcome = window.read(root -> WindowForTests.node(root, "profile-outcome", TextInputControl.class).getText());
        }

        claim("the Profile tab draws the row's control", () -> assertThat(shown).isNotNull());
        claim("the control shows " + LOCAL + ", the value in use where the file states none, and cannot be changed",
                () -> {
                    assertThat(shown.value()).isEqualTo(LOCAL);
                    assertThat(shown.disabled()).isTrue();
                });
        claim("the pane holding it is not disabled, so that its tooltip shows, and the tooltip says \""
                        + NOT_SUPPORTED_YET + "\"",
                () -> {
                    assertThat(shown.holderDisabled()).isFalse();
                    assertThat(shown.tooltip()).contains(NOT_SUPPORTED_YET);
                });
        claim("a Save of the tab said something, so it ran", () -> assertThat(outcome).isNotBlank());
        claim("and the file is byte for byte as it was, the key still commented out",
                () -> assertThat(Files.readAllBytes(profile)).isEqualTo(asWritten));
    }
}
