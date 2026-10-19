package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The verdict vocabulary is closed, and these are the tests that make closing it mean something
 * (ADR-057).
 *
 * <p>A test that pins a count looks like a test that will need editing, and that is the intent: the
 * decision is that adding a verdict is a deliberate act carrying a decision record, so a value
 * appearing here without one should break a test rather than slip in. The alternative considered and
 * rejected was an extension seam, which would have reintroduced the opaque registry ADR-042 already
 * turned down.
 */
@Epic("Ledger")
@Feature("Verdict vocabulary")
@Issue("7")
@Link(name = "ADR-057", url = Adr.VERDICT_VOCABULARY_IS_EIGHT_VALUES, type = "adr")
class VerdictKindTest {

    /**
     * The size of the closed vocabulary: ten ways to be taken out of the survivors, one way to be let
     * through. Eleven since ADR-277 added code, ten since ADR-271 added translation-of, nine since
     * ADR-146 added out-of-scope, and eight before it (ADR-057).
     */
    private static final int RECORDED_VERDICT_KINDS = 11;

    /** The kind ADR-277 adds, named by its stored name so that this compiles before the value exists. */
    private static final String CODE = "CODE";

    @Test
    @Story("The vocabulary is closed")
    @DisplayName("There are exactly eleven verdicts, and adding one is a decision rather than an edit")
    @Link(name = "ADR-146", url = Adr.SPREADSHEETS_ARE_OUT_OF_SCOPE, type = "adr")
    @Link(name = "ADR-271", url = Adr.ONE_COPY_OF_A_TRANSLATION_SET_SURVIVES, type = "adr")
    @Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
    void isExactlyTheRecordedVocabulary() {
        claim(
                "there are " + RECORDED_VERDICT_KINDS + " verdict kinds; a value here without a decision"
                        + " behind it is what this count exists to catch",
                () -> assertThat(VerdictKind.values()).hasSize(RECORDED_VERDICT_KINDS));
    }

    @Test
    @Story("The vocabulary is closed")
    @DisplayName("A copy whose content another copy carries in another language is removed, as a byte-identical copy is")
    @Link(name = "ADR-271", url = Adr.ONE_COPY_OF_A_TRANSLATION_SET_SURVIVES, type = "adr")
    void aTranslationRemovesTheCopy() {
        claim(
                "translation-of removes the copy it is written against, so that one copy of a text held in"
                        + " several languages survives",
                () -> assertThat(VerdictKind.TRANSLATION_OF.blocking()).isTrue());
    }

    @Test
    @Story("The vocabulary is closed")
    @DisplayName("Code is a kind of its own, after out-of-scope, and takes the occurrence out of the survivors")
    @Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
    void codeTakesTheOccurrenceOutOfTheSurvivors() {
        claim(
                "code is a value of the vocabulary, stored under its own name",
                () -> assertThat(Arrays.stream(VerdictKind.values()).map(Enum::name)).contains(CODE));
        claim(
                "it comes straight after out-of-scope, the other kind stage 1's first pass writes for what a file is,"
                        + " so every count by kind in the vocabulary's order shows the two side by side",
                () -> assertThat(Arrays.stream(VerdictKind.values()).map(Enum::name).toList())
                        .containsSubsequence(VerdictKind.OUT_OF_SCOPE.name(), CODE, VerdictKind.EXTRACTION_FAILED.name())
                        .satisfies(names -> assertThat(names.indexOf(CODE))
                                .isEqualTo(names.indexOf(VerdictKind.OUT_OF_SCOPE.name()) + 1)));
        claim(
                "and it blocks: code is not a survivor, so no stage after the first reads it",
                () -> assertThat(VerdictKind.valueOf(CODE).blocking()).isTrue());
    }

    @Test
    @Story("The vocabulary is closed")
    @DisplayName("Every verdict but one removes a document from what survives")
    void onlyOneVerdictIsNonBlocking() {
        claim(
                "passing is the single verdict that does not remove a document, so survival is the absence"
                        + " of a blocking verdict rather than the presence of an approving one",
                () -> assertThat(Arrays.stream(VerdictKind.values())
                                .filter(kind -> !kind.blocking())
                                .toList())
                        .containsExactly(VerdictKind.PASSED));
    }

    @Test
    @Story("The vocabulary is closed")
    @DisplayName("A verdict read back from storage is the one that was written")
    void roundTripsThroughItsStoredName() {
        claim(
                "every verdict survives being written as text and read back, which is how a verdict row"
                        + " outlives the process that wrote it",
                () -> assertThat(Arrays.stream(VerdictKind.values())
                                .map(kind -> VerdictKind.valueOf(kind.name()))
                                .toList())
                        .containsExactly(VerdictKind.values()));
    }
}
