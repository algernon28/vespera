package io.algernon.vespera.profile;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code preferredLanguages}, the profile's languages whose copy survives, most preferred first, read as one of
 * ADR-120's three states (ADR-271 §6); and the two keys ADR-271 adds, in the record's order.
 */
@Epic("Census")
@Feature("Profile")
@Link(name = "ADR-271", url = Adr.ONE_COPY_OF_A_TRANSLATION_SET_SURVIVES, type = "adr")
@Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
class LanguagesValueTest {

    private static final String SET_BY_THIS_TEST = "set by this test";

    @Test
    @Story("The languages whose copy survives")
    @DisplayName("The languages are read in the order they are written")
    void theLanguagesAreReadInTheOrderWritten() {
        claim(
                "it, en reads as Italian first, then English",
                () -> assertThat(readingOf("it, en")).isEqualTo(new LanguagesValue.Answered(List.of("it", "en"))));
    }

    @Test
    @Story("The languages whose copy survives")
    @DisplayName("Spaces, commas and capitals are read alike")
    void spacesCommasAndCapitalsAreReadAlike() {
        claim(
                "IT en,De reads as it, en, de: a space, a comma or both separate two codes, and a code in"
                        + " capitals is the same code",
                () -> assertThat(readingOf("IT en,De"))
                        .isEqualTo(new LanguagesValue.Answered(List.of("it", "en", "de"))));
    }

    @Test
    @Story("The languages whose copy survives")
    @DisplayName("A language written twice keeps its first place")
    void aLanguageWrittenTwiceKeepsItsFirstPlace() {
        claim(
                "en, it, en reads as en, it: the second en says nothing the first did not",
                () -> assertThat(readingOf("en, it, en")).isEqualTo(new LanguagesValue.Answered(List.of("en", "it"))));
    }

    @Test
    @Story("The languages whose copy survives")
    @DisplayName("A piece that is not two letters makes the whole value unreadable")
    void aPieceThatIsNotTwoLettersMakesTheValueUnreadable() {
        Map<String, LanguagesValue.Reading> readings = new LinkedHashMap<>();
        Map<String, LanguagesValue.Reading> quotedBack = new LinkedHashMap<>();
        for (String written : List.of("italian, en", "it;en", "e1")) {
            readings.put(written, readingOf(written));
            quotedBack.put(written, new LanguagesValue.Unreadable(written));
        }

        claim(
                "a language written in full, two codes joined by a semicolon, and a digit are no list of"
                        + " two-letter codes, so each value is unreadable and quoted back as written, and none of"
                        + " it is read",
                () -> assertThat(readings).containsExactlyEntriesOf(quotedBack));
    }

    @Test
    @Story("The languages whose copy survives")
    @DisplayName("A key nobody answered is unset")
    void anUnansweredKeyIsUnset() {
        claim(
                "the key as census drafts it reads as unset, which removes no copy",
                () -> assertThat(LanguagesValue.unset().reading()).isEqualTo(new LanguagesValue.Unset()));
    }

    @Test
    @Story("The languages whose copy survives")
    @DisplayName("The profile carries the similarity floor and the preferred languages, after the name endings")
    void theProfileCarriesBothKeysAfterTheNameEndings() {
        List<String> keys = ProfileKeys.everyKey();

        claim(
                "the record's last three keys are outOfScopeNameEndings, then translationSimilarityFloor, then"
                        + " preferredLanguages, so census drafts both new keys and the index states them",
                () -> assertThat(keys.subList(Math.max(keys.size() - 3, 0), keys.size()))
                        .containsExactly("outOfScopeNameEndings", "translationSimilarityFloor", "preferredLanguages"));
    }

    private static LanguagesValue.Reading readingOf(String written) {
        return new LanguagesValue(written, SET_BY_THIS_TEST, null).reading();
    }
}
