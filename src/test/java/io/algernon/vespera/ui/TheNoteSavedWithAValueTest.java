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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    /** A key whose value is a share, whose checks pass for every value these tests save. */
    private static final ProfileKey A_FLOOR = ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR;

    /** A provenance an operator wrote for the floor. */
    private static final String THE_OPERATORS_WORDS = "read off document-frequency: the cut below the disclaimers";

    /** What the file states for the floor: a value and the operator's words. */
    private static final Optional<Stated> STATED = Optional.of(new Stated("0.4", THE_OPERATORS_WORDS));

    /** The words {@code vespera label --auto} opens the relevance floor's provenance with (ADR-197 §4). */
    private static final String THE_AUTO_RULES_WORDS = "set by the rule that loses no documentation";

    @ParameterizedTest(name = "{0}")
    @MethodSource("whatTheFileStates")
    @Story("Each value's note is filled in for the operator")
    @DisplayName("The note shown beside a value is the provenance the file states, or else the filled-in one")
    void theNoteShownIsTheProvenanceTheFileStatesOrTheFilledInOne(Optional<Stated> inFile, String shown) {
        claim("the note shown is the provenance the file states where it states one that is not blank, and otherwise"
                        + " the words \"" + FILLED_IN + "\"",
                () -> assertThat(ProfileEdit.noteShown(inFile)).isEqualTo(shown));
    }

    static Stream<Arguments> whatTheFileStates() {
        return Stream.of(
                Arguments.of(Named.of("a provenance stated", STATED), THE_OPERATORS_WORDS),
                Arguments.of(Named.of("the key not in the file", Optional.<Stated>empty()), FILLED_IN),
                Arguments.of(Named.of("the key with no provenance", Optional.of(new Stated("0.4", null))), FILLED_IN),
                Arguments.of(Named.of("a blank provenance", Optional.of(new Stated("0.4", "   "))), FILLED_IN));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("valuesAndNotes")
    @Story("Each value's note is filled in for the operator")
    @DisplayName("A save writes each value with the note the operator left, or the filled-in one, and leaves alone a key not changed")
    void eachValueAndNoteIsWrittenAsTheTableSays(Optional<Stated> inFile, String value, String note,
            Optional<String> written) {
        claim("the key is written, and with which note, as the table of the filled-in note says: not at all where"
                        + " neither the value nor the note changed, the filled-in words where the note is blank or was"
                        + " left as shown beside a changed value, and the operator's own words otherwise",
                () -> assertThat(ProfileEdit.noteToSave(A_FLOOR, value, note, inFile)).isEqualTo(written));
    }

    static Stream<Arguments> valuesAndNotes() {
        return Stream.of(
                Arguments.of(Named.of("value and note as they were: not written", STATED),
                        "0.4", THE_OPERATORS_WORDS, Optional.empty()),
                Arguments.of(Named.of("the same value with white space around it: not written", STATED),
                        " 0.4 ", THE_OPERATORS_WORDS, Optional.empty()),
                Arguments.of(Named.of("value as it was, note emptied: the filled-in words", STATED),
                        "0.4", "", Optional.of(FILLED_IN)),
                Arguments.of(Named.of("value changed, note left as shown: the filled-in words", STATED),
                        "0.5", THE_OPERATORS_WORDS, Optional.of(FILLED_IN)),
                Arguments.of(Named.of("value emptied, note left as shown: the filled-in words", STATED),
                        "", THE_OPERATORS_WORDS, Optional.of(FILLED_IN)),
                Arguments.of(Named.of("value changed, note rewritten: the operator's words", STATED),
                        "0.5", "  the cut that keeps every manual  ", Optional.of("the cut that keeps every manual")),
                Arguments.of(Named.of("value as it was, note rewritten: the operator's words", STATED),
                        "0.4", "checked again against the new seeds", Optional.of("checked again against the new seeds")),
                Arguments.of(Named.of("a key the file states nothing for, left alone: not written", Optional.<Stated>empty()),
                        "", FILLED_IN, Optional.empty()),
                Arguments.of(Named.of("a key the file states nothing for, given a value: the filled-in words",
                                Optional.<Stated>empty()),
                        "0.4", FILLED_IN, Optional.of(FILLED_IN)));
    }

    @Test
    @Story("Each value's note is filled in for the operator")
    @DisplayName("A value changed again under the filled-in note the file already holds is saved with it, not refused")
    void aValueChangedUnderTheProvenanceTheFileStatesIsNotRefused() {
        Optional<Stated> setHere = Optional.of(new Stated("0.4", FILLED_IN));
        Optional<String> note = ProfileEdit.noteToSave(A_FLOOR, "0.5", FILLED_IN, setHere);

        claim("a value set in the control panel and changed there again, its note left, is saved with the filled-in"
                        + " words again",
                () -> assertThat(note).contains(FILLED_IN));
        claim("and the save is not refused, though the provenance it sends is the one the file states",
                () -> assertThat(ProfileEdit.refusalOfSave(A_FLOOR, "0.5", note.orElseThrow(), setHere, Optional.empty()))
                        .isEmpty());
    }

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
