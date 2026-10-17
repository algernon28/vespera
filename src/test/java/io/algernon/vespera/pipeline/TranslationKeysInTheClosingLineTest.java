package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The closing line for the two keys ADR-271 adds (§8): each, where it is unreadable, is named with its text
 * quoted back, in the shape ADR-120 gives {@code logTimestampShareFloor}; unset, neither is named, since each
 * ships unset and unset removes nothing.
 */
@Epic("Pipeline")
@Feature("The operator is told the next value")
@Link(name = "ADR-271", url = Adr.ONE_COPY_OF_A_TRANSLATION_SET_SURVIVES, type = "adr")
@Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
class TranslationKeysInTheClosingLineTest {

    private static final String RECORDED_BY_THE_OPERATOR = "recorded by the operator";

    /** A similarity written with a decimal comma, which no number is read from. */
    private static final String A_MISTYPED_FLOOR = "0,85";

    /** Languages written in full, which are no two-letter codes. */
    private static final String LANGUAGES_IN_FULL = "italian, english";

    /** ADR-088's sample, answered in full, so the line is past the labelling. */
    private static final int SIXTY_ANSWERED = 60;

    @Test
    @Story("A value nobody can read changes nothing, and the line says so")
    @DisplayName("A similarity floor that is not a number is named, with what was written and the page it is read off")
    void anUnreadableSimilarityFloorIsReported() {
        String line = lineFor(theRunValuesSet().translationSimilarityFloor(A_MISTYPED_FLOOR, RECORDED_BY_THE_OPERATOR));

        claim(
                "the key is named with the word that says it is not a number, and the page it is read off",
                () -> assertThat(line)
                        .contains("translationSimilarityFloor")
                        .contains("not a number")
                        .contains("translations.html"));
        claim("the operator's own text is quoted back", () -> assertThat(line).contains('"' + A_MISTYPED_FLOOR + '"'));
        claim("and it is one line", () -> assertThat(line.lines()).hasSize(1));
    }

    @Test
    @Story("A value nobody can read changes nothing, and the line says so")
    @DisplayName("Preferred languages that are not two-letter codes are named, with what was written")
    void unreadablePreferredLanguagesAreReported() {
        String line = lineFor(theRunValuesSet().preferredLanguages(LANGUAGES_IN_FULL, RECORDED_BY_THE_OPERATOR));

        claim(
                "the key is named with the words that say what it holds, so an operator who meant one copy to be"
                        + " kept learns that none was removed",
                () -> assertThat(line).contains("preferredLanguages").contains("two-letter"));
        claim("the operator's own text is quoted back", () -> assertThat(line).contains('"' + LANGUAGES_IN_FULL + '"'));
        claim("and it is one line", () -> assertThat(line.lines()).hasSize(1));
    }

    @Test
    @Story("A value nobody can read changes nothing, and the line says so")
    @DisplayName("Left unset, neither key is named")
    void unsetNeitherKeyIsNamed() {
        String line = lineFor(theRunValuesSet());

        claim(
                "both keys ship unset and remove nothing unset, so the line asks for neither",
                () -> assertThat(line).doesNotContain("translationSimilarityFloor").doesNotContain("preferredLanguages"));
    }

    private static String lineFor(ProfileFixture fixture) {
        Profile profile = fixture.build();
        return NextAction.line(profile, SIXTY_ANSWERED, true, null, "a-generation-model:8b", false);
    }

    /** Every value the run asks for answered, so the line reaches the values that are not stops. */
    private static ProfileFixture theRunValuesSet() {
        return ProfileFixture.profile()
                .seedFolder("/corpus/exemplars", RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor("0.4", RECORDED_BY_THE_OPERATOR)
                .embeddingModel("embeddinggemma", RECORDED_BY_THE_OPERATOR);
    }
}
