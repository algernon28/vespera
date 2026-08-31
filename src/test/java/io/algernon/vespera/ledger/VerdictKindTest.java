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

    /** The size of the closed vocabulary: seven ways to be ruled out, one way to be let through. */
    private static final int RECORDED_VERDICT_KINDS = 8;

    @Test
    @Story("The vocabulary is closed")
    @DisplayName("There are exactly eight verdicts, and adding one is a decision rather than an edit")
    void isExactlyTheRecordedVocabulary() {
        claim(
                "there are " + RECORDED_VERDICT_KINDS + " verdict kinds; a value here without a decision"
                        + " behind it is what this count exists to catch",
                () -> assertThat(VerdictKind.values()).hasSize(RECORDED_VERDICT_KINDS));
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
