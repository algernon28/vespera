package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.ui.ProfileEdit.Stated;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tooltip;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The provenance saved with a profile value (ADR-343, as ADR-377 §2 leaves it): the operator writes none in the window;
 * a value changed there is saved with {@code set in the control panel}, and a value left as the file states it is not
 * written, so a provenance written by hand in {@code profile.yaml} stays until its value is changed in the window. The
 * provenance the file states is shown in the title's tooltip alone (ADR-377 §1).
 *
 * <p>The rule is {@code ProfileEdit}'s, which every write of the profile passes through (ADR-295 §4); the window's rows
 * call it. Each expected provenance is written out: {@code set in the control panel} is the words ADR-343 §1 fixes.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Issue("801")
@Issue("803")
@Link(name = "ADR-343", url = Adr.THE_NOTE_SAVED_WITH_A_PROFILE_VALUE_IS_FILLED_IN, type = "adr")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class TheProvenanceSavedWithAValueTest {

    /** The words a value changed in the window is saved with, as ADR-343 §1 fixes them. */
    private static final String FILLED_IN = "set in the control panel";

    /** The words that open the provenance's part of a title's tooltip. */
    private static final String CAME_FROM = "Where this value came from: ";

    /** A key whose value is a share, whose checks pass for every value these tests save. */
    private static final ProfileKey A_FLOOR = ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR;

    /** A key whose value the window empties. */
    private static final ProfileKey EMPTIED = ProfileKey.DEGENERATE_OUTPUT_WORD_FLOOR;

    /** A key the window leaves as the file states it. */
    private static final ProfileKey LEFT_ALONE = ProfileKey.RELEVANCE_SCORE_FLOOR;

    /** A provenance an operator wrote for the floor. */
    private static final String THE_OPERATORS_WORDS = "read off document-frequency: the cut below the disclaimers";

    /** What the file states for the floor: a value and the operator's words. */
    private static final Optional<Stated> STATED = Optional.of(new Stated("0.4", THE_OPERATORS_WORDS));

    /** The words {@code vespera label --auto} opens the relevance floor's provenance with (ADR-197 §4). */
    private static final String THE_AUTO_RULES_WORDS = "set by the rule that loses no documentation";

    @ParameterizedTest(name = "{0}")
    @MethodSource("whatTheFileStates")
    @Story("A value's provenance is in its title's tooltip")
    @DisplayName("The provenance stated is the one the file states where it is not blank, and none otherwise")
    void theProvenanceStatedIsTheFilesWhereNotBlank(Optional<Stated> inFile, Optional<String> stated) {
        claim("the provenance stated is the one the file states where it states one that is not blank, and none"
                        + " otherwise",
                () -> assertThat(ProfileEdit.provenanceStated(inFile)).isEqualTo(stated));
    }

    static Stream<Arguments> whatTheFileStates() {
        return Stream.of(
                Arguments.of(Named.of("a provenance stated", STATED), Optional.of(THE_OPERATORS_WORDS)),
                Arguments.of(Named.of("the key not in the file", Optional.<Stated>empty()), Optional.empty()),
                Arguments.of(Named.of("the key with no provenance", Optional.of(new Stated("0.4", null))),
                        Optional.empty()),
                Arguments.of(Named.of("a blank provenance", Optional.of(new Stated("0.4", "   "))), Optional.empty()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("values")
    @Story("A value changed in the window is saved with set in the control panel")
    @DisplayName("A save writes a value changed with the filled-in words, and leaves alone a value as the file states it")
    void eachValueIsWrittenAsTheTableSays(Optional<Stated> inFile, String value, Optional<String> written) {
        claim("the key is written, and with which provenance, as the table of the two rows says: not at all where the"
                        + " value is as the file states it, and with the filled-in words where it changed",
                () -> assertThat(ProfileEdit.provenanceToSave(A_FLOOR, value, inFile)).isEqualTo(written));
    }

    static Stream<Arguments> values() {
        return Stream.of(
                Arguments.of(Named.of("the value as the file states it: not written", STATED),
                        "0.4", Optional.empty()),
                Arguments.of(Named.of("the same value with white space around it: not written", STATED),
                        " 0.4 ", Optional.empty()),
                Arguments.of(Named.of("the value changed: the filled-in words", STATED),
                        "0.5", Optional.of(FILLED_IN)),
                Arguments.of(Named.of("the value emptied: the filled-in words", STATED),
                        "", Optional.of(FILLED_IN)),
                Arguments.of(Named.of("a key the file states nothing for, left alone: not written",
                                Optional.<Stated>empty()),
                        "", Optional.empty()),
                Arguments.of(Named.of("a key the file states nothing for, given a value: the filled-in words",
                                Optional.<Stated>empty()),
                        "0.4", Optional.of(FILLED_IN)));
    }

    @Test
    @Story("A value changed in the window is saved with set in the control panel")
    @DisplayName("A relevance floor the local model set becomes the operator's once its value is changed")
    void aFloorTheLocalModelSetChangedUnderItsProvenanceBecomesTheOperators() {
        String modelsProvenance = THE_AUTO_RULES_WORDS + ": 60 answers, 12 of them by the local model";
        Optional<Stated> setByTheModel = Optional.of(new Stated("0.61", modelsProvenance));

        claim("the floor changed under the provenance the local model's rule wrote is saved with the filled-in words,"
                        + " which do not begin as the rule's do, so the rule no longer writes over it",
                () -> assertThat(ProfileEdit.provenanceToSave(ProfileKey.RELEVANCE_SCORE_FLOOR, "0.65", setByTheModel))
                        .contains(FILLED_IN));
        claim("and every write lets the filled-in words through for the floor",
                () -> assertThat(ProfileEdit.refusalOf(ProfileKey.RELEVANCE_SCORE_FLOOR, "0.65", FILLED_IN)).isEmpty());
    }

    @Test
    @Story("A value changed in the window is saved with set in the control panel")
    @DisplayName("A value changed in the window, or emptied, is saved with set in the control panel, and one left alone is not written")
    void aValueChangedInTheWindowIsSavedWithSetInTheControlPanel(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        String profile = ProfileEdit.rewrite(Optional.of(ProfileEdit.rewrite(Optional.of(ProfileEdit.rewrite(
                Optional.empty(), A_FLOOR, "0.4", THE_OPERATORS_WORDS)), EMPTIED, "20", THE_OPERATORS_WORDS)),
                LEFT_ALONE, "0.62", THE_OPERATORS_WORDS);
        Files.writeString(working.resolve(ProfileEdit.FILE_NAME), profile, StandardCharsets.UTF_8);
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(corpus.toString(), working.toString(), "", "", "", "", "", ""));
        String[] tooltips = new String[2];
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.act(root -> {
                WindowForTests.node(root, "profile-" + A_FLOOR.key(), Spinner.class).getEditor().setText("0.5");
                WindowForTests.node(root, "profile-" + EMPTIED.key() + ".set", CheckBox.class).setSelected(false);
                WindowForTests.node(root, "profile-save", Button.class).fire();
            });
            window.act(root -> {
                tooltips[0] = tooltipOf(root, "profile-" + A_FLOOR.key() + ".title");
                tooltips[1] = tooltipOf(root, "profile-" + EMPTIED.key() + ".title");
            });
        }
        Map<ProfileKey, Stated> saved = ProfileEdit.read(
                Files.readString(working.resolve(ProfileEdit.FILE_NAME), StandardCharsets.UTF_8));

        claim("the value changed is written with the filled-in words in place of the operator's",
                () -> assertThat(saved.get(A_FLOOR)).isEqualTo(new Stated("0.5", FILLED_IN)));
        claim("the value emptied is written unset, with the filled-in words",
                () -> assertThat(saved.get(EMPTIED)).isEqualTo(new Stated(null, FILLED_IN)));
        claim("the value left as the file states it is not written: it keeps the operator's words",
                () -> assertThat(saved.get(LEFT_ALONE)).isEqualTo(new Stated("0.62", THE_OPERATORS_WORDS)));
        claim("each title's tooltip, the rows loaded again, ends by saying the value was set in the control panel",
                () -> assertThat(tooltips).allSatisfy(tip -> assertThat(tip).endsWith("\n\n" + CAME_FROM + FILLED_IN)));
    }

    private static String tooltipOf(javafx.scene.Parent root, String id) {
        Tooltip tip = WindowForTests.node(root, id, Control.class).getTooltip();
        return tip == null ? null : tip.getText();
    }
}
