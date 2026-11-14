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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The provenance saved with a profile value (ADR-343, its names ADR-377 §11's): no field asks why a value was chosen;
 * each value's provenance is filled in, with the one the file states or {@code set in the control panel}, and is saved
 * as the operator leaves it, except that a blank one, or one left as shown beside a value changed, is saved as {@code
 * set in the control panel}. A key whose value and provenance are both as they were is not written.
 *
 * <p>The rule is {@code ProfileEdit}'s, which every write of the profile passes through (ADR-295 §4); the window that
 * shows the provenances calls it. Each expected provenance is written out: {@code set in the control panel} is the
 * words ADR-343 §1 fixes.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Issue("801")
@Link(name = "ADR-343", url = Adr.THE_NOTE_SAVED_WITH_A_PROFILE_VALUE_IS_FILLED_IN, type = "adr")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class TheProvenanceSavedWithAValueTest {

    /** The words a provenance is filled in with, as ADR-343 §1 fixes them. */
    private static final String FILLED_IN = "set in the control panel";

    /** The words {@code vespera label --auto} opens the relevance floor's provenance with (ADR-197 §4). */
    private static final String THE_AUTO_RULES_WORDS = "set by the rule that loses no documentation";

    /** A provenance the operator wrote by hand in the file. */
    private static final String THE_OPERATORS = "read off the word-count report";

    /** The floor the file states, and another the operator types in its place. */
    private static final String STATED_VALUE = "20";
    private static final String CHANGED_VALUE = "30";

    /** The key every row of the table is held on: a number, compared as the jar reads it. */
    private static final ProfileKey KEY = ProfileKey.DEGENERATE_OUTPUT_WORD_FLOOR;

    @Test
    @Story("Each value's provenance is filled in for the operator")
    @DisplayName("The provenance shown is the one the file states, or the filled-in words where it states none or a blank one")
    void theProvenanceShownIsTheOneTheFileStatesOrTheFilledInOne() {
        claim("a provenance the file states is the one shown",
                () -> assertThat(ProfileEdit.provenanceShown(Optional.of(new Stated(STATED_VALUE, THE_OPERATORS))))
                        .isEqualTo(THE_OPERATORS));
        claim("a key the file states nothing for shows the filled-in words",
                () -> assertThat(ProfileEdit.provenanceShown(Optional.empty())).isEqualTo(FILLED_IN));
        claim("a key the file states with no provenance shows the filled-in words",
                () -> assertThat(ProfileEdit.provenanceShown(Optional.of(new Stated(STATED_VALUE, null))))
                        .isEqualTo(FILLED_IN));
        claim("a key the file states with a blank provenance shows the filled-in words",
                () -> assertThat(ProfileEdit.provenanceShown(Optional.of(new Stated(STATED_VALUE, "  "))))
                        .isEqualTo(FILLED_IN));
    }

    @Test
    @Story("Each value's provenance is filled in for the operator")
    @DisplayName("Each value and its provenance are written as the table of four rows says")
    void eachValueIsWrittenAsTheTableSays() {
        Optional<Stated> stated = Optional.of(new Stated(STATED_VALUE, THE_OPERATORS));

        claim("a value as the file states it, its provenance left as shown, is not written",
                () -> assertThat(ProfileEdit.provenanceToSave(KEY, STATED_VALUE, THE_OPERATORS, stated)).isEmpty());
        claim("a key the file states nothing for, left alone with the filled-in words shown, is not written",
                () -> assertThat(ProfileEdit.provenanceToSave(KEY, "", FILLED_IN, Optional.empty())).isEmpty());
        claim("a provenance emptied is written as the filled-in words, the value as it was",
                () -> assertThat(ProfileEdit.provenanceToSave(KEY, STATED_VALUE, " ", stated)).contains(FILLED_IN));
        claim("a value changed, its provenance left as shown, is written with the filled-in words",
                () -> assertThat(ProfileEdit.provenanceToSave(KEY, CHANGED_VALUE, THE_OPERATORS, stated))
                        .contains(FILLED_IN));
        claim("a value emptied, its provenance left as shown, is a value changed and is written with the filled-in"
                        + " words",
                () -> assertThat(ProfileEdit.provenanceToSave(KEY, "", THE_OPERATORS, stated)).contains(FILLED_IN));
        claim("a provenance changed is written as typed, its white space around removed, beside a value as it was",
                () -> assertThat(ProfileEdit.provenanceToSave(KEY, STATED_VALUE, "  my own reason ", stated))
                        .contains("my own reason"));
    }

    @Test
    @Story("Each value's provenance is filled in for the operator")
    @DisplayName("A value changed under the provenance the file states is not refused")
    void aValueChangedUnderTheProvenanceTheFileStatesIsNotRefused() {
        Optional<Stated> stated = Optional.of(new Stated(STATED_VALUE, THE_OPERATORS));

        claim("a value changed under the provenance the file states is no refusal, the drawn value the file's",
                () -> assertThat(ProfileEdit.refusalOfSave(KEY, CHANGED_VALUE, THE_OPERATORS, stated,
                        Optional.of(ProfileEdit.drawnOf(KEY, STATED_VALUE, THE_OPERATORS)))).isEmpty());
    }

    @Test
    @Story("Each value's provenance is filled in for the operator")
    @DisplayName("A relevance floor the local model set becomes the operator's once its value is changed under its provenance")
    void aFloorTheLocalModelSetChangedUnderItsProvenanceBecomesTheOperators() {
        String modelsProvenance = THE_AUTO_RULES_WORDS + ": 60 answers, 12 of them by the local model";
        Optional<Stated> setByTheModel = Optional.of(new Stated("0.61", modelsProvenance));

        claim("the floor changed under the provenance the local model's rule wrote is saved with the filled-in words,"
                        + " which do not begin as the rule's do, so the rule no longer writes over it",
                () -> assertThat(ProfileEdit.provenanceToSave(ProfileKey.RELEVANCE_SCORE_FLOOR, "0.65",
                        modelsProvenance, setByTheModel)).contains(FILLED_IN));
        claim("and every write lets the filled-in words through for the floor",
                () -> assertThat(ProfileEdit.refusalOf(ProfileKey.RELEVANCE_SCORE_FLOOR, "0.65", FILLED_IN)).isEmpty());
    }
}
