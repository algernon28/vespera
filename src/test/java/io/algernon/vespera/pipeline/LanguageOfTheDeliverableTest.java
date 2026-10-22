package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Where the deliverable's language comes from (ADR-321 §1): the profile's {@code deliverableLanguage}, English where it
 * names none, and a stop where it names one the deliverable cannot be drawn in.
 *
 * <p>The sibling of {@link GenerationContextWindowTest}: not a gate, so an unanswered key resolves to a value and the
 * run proceeds; and, like the window, a value that is not one stops rather than being read past, since drawing the
 * deliverable in a language nobody chose would hand its readers what nobody asked for.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({LanguageOfTheDeliverable.class, ProfileStore.class})
@Epic("Synthesis")
@Feature("Generation")
@Link(name = "ADR-321", url = Adr.THE_DELIVERABLE_IS_IN_THE_LANGUAGE_THE_PROFILE_NAMES, type = "adr")
class LanguageOfTheDeliverableTest {

    /** The key as the profile spells it, which a refusal names. */
    private static final String THE_KEY = "deliverableLanguage";

    /** A language code the deliverable has no fixed words for. */
    private static final String A_LANGUAGE_IT_CANNOT_BE_DRAWN_IN = "fr";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private LanguageOfTheDeliverable languageOfTheDeliverable;

    @Autowired
    private ProfileStore profileStore;

    @Test
    @Story("The deliverable is in the language the profile names")
    @DisplayName("With no language written in the profile, or a blank one, the deliverable is in English")
    void isEnglishWhereTheProfileNamesNone() {
        profile(null);
        DeliverableLanguage unanswered = languageOfTheDeliverable.language();
        profile("");
        DeliverableLanguage blank = languageOfTheDeliverable.language();

        claim(
                "an unanswered key is English rather than a stop: the pages are drawn as they were before it existed",
                () -> assertThat(unanswered).isEqualTo(DeliverableLanguage.ENGLISH));
        claim(
                "and a key written and left empty is an unanswered one",
                () -> assertThat(blank).isEqualTo(DeliverableLanguage.ENGLISH));
    }

    @Test
    @Story("The deliverable is in the language the profile names")
    @DisplayName("Italian written in the profile, in any case and with blanks around it, is Italian")
    void isItalianWhereTheProfileNamesIt() {
        profile("it");
        DeliverableLanguage lower = languageOfTheDeliverable.language();
        profile(" IT ");
        DeliverableLanguage upper = languageOfTheDeliverable.language();

        claim(
                "it is Italian, and so is IT with blanks around it: the operator wrote the same language either way",
                () -> {
                    assertThat(lower).isEqualTo(DeliverableLanguage.ITALIAN);
                    assertThat(upper).isEqualTo(DeliverableLanguage.ITALIAN);
                });
    }

    @Test
    @Story("The deliverable is in the language the profile names")
    @DisplayName("A language the deliverable cannot be drawn in stops, naming the key, what was written and the two it can be")
    void stopsOnALanguageItCannotBeDrawnIn() {
        profile(A_LANGUAGE_IT_CANNOT_BE_DRAWN_IN);

        claim(
                "fr stops rather than falling back to English: somebody who wrote a language meant the readers to have"
                        + " it, and the refusal names the key, quotes what was written and names en and it",
                () -> assertThatThrownBy(languageOfTheDeliverable::language)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(THE_KEY)
                        .hasMessageContaining("\"" + A_LANGUAGE_IT_CANNOT_BE_DRAWN_IN + "\"")
                        .hasMessageContaining(DeliverableLanguage.ENGLISH.code())
                        .hasMessageContaining(DeliverableLanguage.ITALIAN.code()));
    }

    /** Writes {@code deliverableLanguage}, leaving every other key as census created it. */
    private void profile(String language) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .deliverableLanguage(language, language == null ? null : "set by this test")
                .build());
    }
}
