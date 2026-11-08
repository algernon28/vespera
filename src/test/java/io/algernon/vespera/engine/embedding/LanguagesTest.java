package io.algernon.vespera.engine.embedding;

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

    /** Each segment and what {@link Languages#ofFolder} reads it as, in the order given. */
    private static Map<String, Optional<String>> readAsFolders(List<String> segments) {
        Map<String, Optional<String>> read = new LinkedHashMap<>();
        for (String segment : segments) {
            read.put(segment, Languages.ofFolder(segment));
        }
        return read;
    }
}
