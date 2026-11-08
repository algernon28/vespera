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
 * The note saved with a profile value (ADR-343): no field asks why a value was chosen; each value's note is filled in,
 * with the provenance the file states or {@code set in the control panel}, and is saved as the operator leaves it,
 * except that a blank note, or a note left as shown beside a value changed, is saved as {@code set in the control
 * panel}. A key whose value and note are both as they were is not written.
 *
 * <p>The rule is {@code ProfileEdit}'s, which every write of the profile passes through (ADR-295 §4); the window that
 * shows the notes calls it. Each expected note is written out: {@code set in the control panel} is the words ADR-343 §1
 * fixes.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-343", url = Adr.THE_NOTE_SAVED_WITH_A_PROFILE_VALUE_IS_FILLED_IN, type = "adr")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class TheNoteSavedWithAValueTest {

    /** The words a note is filled in with, as ADR-343 §1 fixes them. */
    private static final String FILLED_IN = "set in the control panel";

    /** The words {@code vespera label --auto} opens the relevance floor's provenance with (ADR-197 §4). */
    private static final String THE_AUTO_RULES_WORDS = "set by the rule that loses no documentation";

    @Test
    @Story("Each value's note is filled in for the operator")
    @DisplayName("A relevance floor the local model set becomes the operator's once its value is changed under its note")
    void aFloorTheLocalModelSetChangedUnderItsNoteBecomesTheOperators() {
        String modelsNote = THE_AUTO_RULES_WORDS + ": 60 answers, 12 of them by the local model";
        Optional<Stated> setByTheModel = Optional.of(new Stated("0.61", modelsNote));

        claim("the floor changed under the note the local model's rule wrote is saved with the filled-in words,"
                        + " which do not begin as the rule's do, so the rule no longer writes over it",
                () -> assertThat(ProfileEdit.noteToSave(ProfileKey.RELEVANCE_SCORE_FLOOR, "0.65", modelsNote, setByTheModel))
                        .contains(FILLED_IN));
        claim("and every write lets the filled-in words through for the floor",
                () -> assertThat(ProfileEdit.refusalOf(ProfileKey.RELEVANCE_SCORE_FLOOR, "0.65", FILLED_IN)).isEmpty());
    }
}
