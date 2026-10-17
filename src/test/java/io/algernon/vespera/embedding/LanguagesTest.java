package io.algernon.vespera.embedding;

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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which segment of a path is a language folder, and which language it names (ADR-271 §2); and a language as
 * stage 2 stored it, Lingua's name, read as the same ISO 639-1 code (ADR-271 §9).
 */
@Epic("Relevance")
@Feature("Copies in another language")
@Link(name = "ADR-271", url = Adr.ONE_COPY_OF_A_TRANSLATION_SET_SURVIVES, type = "adr")
class LanguagesTest {

    @Test
    @Story("A folder named for a language")
    @DisplayName("Two lowercase letters naming a language are a folder of that language")
    void aLowercaseCodeOfALanguageIsALanguageFolder() {
        Map<String, Optional<String>> read = readAsFolders(List.of("en", "zh", "it", "de", "ja"));

        claim(
                "each of en, zh, it, de and ja is the two-letter code of a language the detector knows, written"
                        + " in lowercase, so each is a folder of that language",
                () -> assertThat(read).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "en", Optional.of("en"),
                        "zh", Optional.of("zh"),
                        "it", Optional.of("it"),
                        "de", Optional.of("de"),
                        "ja", Optional.of("ja"))));
    }

    @Test
    @Story("A folder named for a language")
    @DisplayName("A region or a script after the code keeps the code's language")
    void aRegionOrAScriptAfterTheCodeKeepsItsLanguage() {
        Map<String, Optional<String>> read = readAsFolders(List.of("en_US", "pt-BR", "zh-cn", "zh-Hans"));

        claim(
                "a two-letter region or a four-letter script after a hyphen or an underscore, in either case,"
                        + " leaves the folder one of the language its first two letters name",
                () -> assertThat(read).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "en_US", Optional.of("en"),
                        "pt-BR", Optional.of("pt"),
                        "zh-cn", Optional.of("zh"),
                        "zh-Hans", Optional.of("zh"))));
    }

    @Test
    @Story("A folder named for a language")
    @DisplayName("Capitals, names in full, three-letter codes, unknown codes and odd suffixes are no language folder")
    void noOtherSegmentIsALanguageFolder() {
        List<String> segments = List.of("IT", "HR", "En", "english", "eng", "xx", "en-", "en_USA", "e", "");

        Map<String, Optional<String>> read = readAsFolders(segments);

        claim(
                "IT and HR are what departments are called, not Italian and Croatian; En is not written as a"
                        + " language folder is, in lowercase; english and eng are not two-letter codes; xx names no language;"
                        + " en- and en_USA carry a suffix that is neither a region nor a script; e and the empty"
                        + " segment are too short -- so none of the " + segments.size() + " is a folder of a"
                        + " language",
                () -> assertThat(read.values())
                        .withFailMessage("the segments were read as %s", read)
                        .containsOnly(Optional.empty()));
    }

    @Test
    @Story("A folder named for a language")
    @DisplayName("A language as the detector named it reads as its two-letter code, and no language as none")
    void aStoredLanguageReadsAsItsCode() {
        Map<String, Optional<String>> read = new LinkedHashMap<>();
        for (String stored : List.of("ENGLISH", "CHINESE", "BOKMAL")) {
            read.put(stored, Languages.ofDetected(stored));
        }

        claim(
                "the names the detector stores, ENGLISH, CHINESE and BOKMAL, read as en, zh and nb, the codes a"
                        + " folder is named by, so a folder's language and a detected one can be compared",
                () -> assertThat(read).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "ENGLISH", Optional.of("en"),
                        "CHINESE", Optional.of("zh"),
                        "BOKMAL", Optional.of("nb"))));
        claim(
                "and a text whose language was not detected has no code",
                () -> assertThat(Languages.ofDetected(null)).isEmpty());
    }

    /** Each segment and what {@link Languages#ofFolder} reads it as, in the order given. */
    private static Map<String, Optional<String>> readAsFolders(List<String> segments) {
        Map<String, Optional<String>> read = new LinkedHashMap<>();
        for (String segment : segments) {
            read.put(segment, Languages.ofFolder(segment));
        }
        return read;
    }
}
