package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two languages a deliverable can be in, how each is written in the profile, and which one an unanswered key
 * means (ADR-321 §1). {@code synthesis} holds them, as it holds the default window, so that what reads the profile
 * holds no rule.
 */
@Epic("Synthesis")
@Feature("Writing over the groups")
@Link(name = "ADR-321", url = Adr.THE_DELIVERABLE_IS_IN_THE_LANGUAGE_THE_PROFILE_NAMES, type = "adr")
class DeliverableLanguageTest {

    /** Ways of writing Italian the record reads as Italian: the code in any case, blanks around it ignored. */
    private static final List<String> ITALIAN_AS_WRITTEN = List.of("it", "IT", "It", " it ", "\tit\n");

    /** Ways of writing English the record reads as English. */
    private static final List<String> ENGLISH_AS_WRITTEN = List.of("en", "EN", " En ");

    /** Answers that name no language a deliverable can be drawn in: another code, a name, a list, a blank. */
    private static final List<String> NO_LANGUAGE_OF_A_DELIVERABLE = List.of("fr", "italian", "Italiano", "it, en", "i t", "");

    @Test
    @Story("The deliverable is in the language the profile names")
    @DisplayName("Italian and English are read from their two-letter codes in any case, and nothing else is a language")
    void readsTheTwoCodesAndNothingElse() {
        claim(
                "each way of writing it is Italian",
                () -> assertThat(ITALIAN_AS_WRITTEN).allSatisfy(written ->
                        assertThat(DeliverableLanguage.ofCode(written)).contains(DeliverableLanguage.ITALIAN)));
        claim(
                "each way of writing en is English",
                () -> assertThat(ENGLISH_AS_WRITTEN).allSatisfy(written ->
                        assertThat(DeliverableLanguage.ofCode(written)).contains(DeliverableLanguage.ENGLISH)));
        claim(
                "and another code, a language's name, a list of two or a blank names none",
                () -> assertThat(NO_LANGUAGE_OF_A_DELIVERABLE).allSatisfy(written ->
                        assertThat(DeliverableLanguage.ofCode(written)).isEmpty()));
    }

    @Test
    @Story("The deliverable is in the language the profile names")
    @DisplayName("There are two languages, each with its code and the English name the question asks in, and English is the default")
    void eachLanguageHasItsCodeAndItsEnglishName() {
        claim(
                "two languages and no more: every fixed word needs a rendering a record fixes",
                () -> assertThat(DeliverableLanguage.values())
                        .containsExactly(DeliverableLanguage.ENGLISH, DeliverableLanguage.ITALIAN));
        claim(
                "each is written in a page's lang and in the run's settings by its code",
                () -> {
                    assertThat(DeliverableLanguage.ENGLISH.code()).isEqualTo("en");
                    assertThat(DeliverableLanguage.ITALIAN.code()).isEqualTo("it");
                });
        claim(
                "and named to the model in the English the instruction is written in",
                () -> {
                    assertThat(DeliverableLanguage.ENGLISH.englishName()).isEqualTo("English");
                    assertThat(DeliverableLanguage.ITALIAN.englishName()).isEqualTo("Italian");
                });
        claim(
                "an unanswered key means English, the language every page was drawn in before",
                () -> assertThat(DeliverableLanguage.DEFAULT).isEqualTo(DeliverableLanguage.ENGLISH));
    }
}
