package io.algernon.vespera.engine.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Arrays;
import java.util.List;
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

    /** The kind ADR-277 adds, named by its stored name so that this compiles before the value exists. */
    private static final String CODE = "CODE";

    /**
     * The ten kinds ADR-392 leaves, by their stored names in the order {@link VerdictKind} declares them, so that this
     * compiles while {@code TRANSLATION_OF} is still declared and fails until it goes.
     */
    private static final List<String> THE_TEN_KINDS = List.of("BROKEN", "DUPLICATE_OF", "SUPERSEDED_BY",
            "OUT_OF_SCOPE", CODE, "EXTRACTION_FAILED", "DEGENERATE_OUTPUT", "REDUNDANT_WITH", "BELOW_THRESHOLD",
            "PASSED");

    @Test
    @Story("The vocabulary is closed")
    @DisplayName("Every verdict but one removes a document from what survives")
    @Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
    @Link(name = "ADR-392", url = Adr.VESPERA_FINDS_NO_TRANSLATION_SETS, type = "adr")
    void onlyOneVerdictIsNonBlocking() {
        claim(
                "passing is the single verdict that does not remove a document, so survival is the absence"
                        + " of a blocking verdict rather than the presence of an approving one: code is not a"
                        + " survivor either",
                () -> assertThat(Arrays.stream(VerdictKind.values())
                                .filter(kind -> !kind.blocking())
                                .toList())
                        .containsExactly(VerdictKind.PASSED));
    }

    @Test
    @Story("The vocabulary is closed")
    @DisplayName("The verdicts are ten, in their order, and none says a document is a copy of another in another language")
    @Link(name = "ADR-392", url = Adr.VESPERA_FINDS_NO_TRANSLATION_SETS, type = "adr")
    void theVocabularyIsTenValuesAndNoneSaysACopyIsInAnotherLanguage() {
        claim(
                "the vocabulary is the ten kinds, from broken to passed, in the order they are declared, and no"
                        + " kind removes a copy for being in another language, since every such copy is kept",
                () -> assertThat(Arrays.stream(VerdictKind.values()).map(Enum::name).toList())
                        .containsExactlyElementsOf(THE_TEN_KINDS));
    }
}
