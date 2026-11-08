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

    /** The kind ADR-277 adds, named by its stored name so that this compiles before the value exists. */
    private static final String CODE = "CODE";

    @Test
    @Story("The vocabulary is closed")
    @DisplayName("Every verdict but one removes a document from what survives")
    @Link(name = "ADR-271", url = Adr.ONE_COPY_OF_A_TRANSLATION_SET_SURVIVES, type = "adr")
    @Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
    void onlyOneVerdictIsNonBlocking() {
        claim(
                "passing is the single verdict that does not remove a document, so survival is the absence"
                        + " of a blocking verdict rather than the presence of an approving one: translation-of"
                        + " removes the copy it is written against, and code is not a survivor either",
                () -> assertThat(Arrays.stream(VerdictKind.values())
                                .filter(kind -> !kind.blocking())
                                .toList())
                        .containsExactly(VerdictKind.PASSED));
    }
}
