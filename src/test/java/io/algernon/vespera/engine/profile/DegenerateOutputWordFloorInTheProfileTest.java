package io.algernon.vespera.engine.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The profile's {@code degenerateOutputWordFloor} key (ADR-282 section 3), whose pointer stage 3 brings up to date
 * without touching the answer. Where the key is declared is {@link ProfileTest}'s; that census drafts it unset and
 * keeps an operator's answer as written, {@link ProfileStoreTest}'s, with every other key (#682).
 */
@Epic("Census")
@Feature("Profile")
@Issue("576")
@Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
class DegenerateOutputWordFloorInTheProfileTest {

    /** A floor as an operator writes it, and why. */
    private static final String TWO_WORDS = "2";

    private static final String WHY = "read off word-count-distribution.html: a floor of 2 removes 40 images";

    @Test
    @Story("The profile points at the page the value is read off")
    @DisplayName("Pointing the word floor at its page keeps the operator's answer and touches no other key's pointer")
    void thePointerKeepsTheAnswer() {
        Measurement page = new Measurement("word-count-distribution.html", Instant.parse("2026-10-19T12:00:00Z"));
        Profile answered = ProfileFixture.profile().degenerateOutputWordFloor(TWO_WORDS, WHY).build();

        Profile pointed = answered.withDegenerateOutputWordFloorMeasurement(page);

        claim(
                "the word floor points at the page, with its value and provenance as the operator wrote them",
                () -> assertThat(pointed.degenerateOutputWordFloor()).isEqualTo(new NumericValue(TWO_WORDS, WHY, page)));
        claim(
                "and the confidence floor's pointer is not touched, so each floor points at its own page",
                () -> assertThat(pointed.degenerateOutputConfidenceFloor().measurement()).isNull());
    }
}
