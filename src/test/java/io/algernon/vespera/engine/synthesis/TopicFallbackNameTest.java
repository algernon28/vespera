package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The name a topic takes where the model's is turned down (ADR-403 §7): the Docling title of its strongest
 * contributing seed, then that seed's filename stem, then the topic's place among the topics in key order, in the
 * deliverable language. A seed's title and stem are its own words, and are never translated.
 */
@Epic("Arrangement")
@Feature("Topic names")
@Issue("829")
@Link(name = "ADR-403", url = Adr.TOPICS_ARE_NAMED_AT_THE_HEAD_OF_THE_ARRANGEMENT, type = "adr")
class TopicFallbackNameTest {

    /** The strongest contributing seed's path relative to the seed folder. */
    private static final String SEED_PATH = "manuali/Specifica flussi.docx";

    /** What the seed calls itself, in its own language. */
    private static final String SEED_TITLE = "Specifiche dei flussi di risposta";

    /** A seed whose filename is only an extension, so that its stem says nothing. */
    private static final String NAMELESS_SEED_PATH = "manuali/.docx";

    /** The topic's place among the topics in key order, counted from one. */
    private static final int PLACE = 3;

    @Test
    @Story("A name that fails falls back to the strongest contributing seed")
    @DisplayName("A topic falls back to its strongest seed's own title, untranslated")
    void theSeedsTitleComesFirstAndIsNotTranslated() {
        claim("with the seed's title at hand, the topic is named by it, word for word, in an English deliverable",
                () -> assertThat(TopicFallbackName.of(
                                Optional.of(SEED_TITLE), SEED_PATH, PLACE, DeliverableLanguage.ENGLISH))
                        .isEqualTo(SEED_TITLE));
    }

    @Test
    @Story("A name that fails falls back to the strongest contributing seed")
    @DisplayName("A seed with no title gives its filename without folders or extension")
    void aSeedWithNoTitleGivesItsFilenameStem() {
        claim("with no title, or a blank one, the topic is named by the seed's filename without its folders or its"
                        + " extension",
                () -> assertThat(List.of(
                                TopicFallbackName.of(Optional.empty(), SEED_PATH, PLACE, DeliverableLanguage.ITALIAN),
                                TopicFallbackName.of(Optional.of("  "), SEED_PATH, PLACE, DeliverableLanguage.ITALIAN)))
                        .containsExactly("Specifica flussi", "Specifica flussi"));
    }

    @Test
    @Story("A name that fails falls back to the strongest contributing seed")
    @DisplayName("A seed with neither a title nor a filename that says anything gives the topic's place, in the pages' language")
    void neitherGivesTheTopicsPlaceInTheDeliverableLanguage() {
        claim("with neither, the topic is named by its place, 3, as Topic 3 in English and Argomento 3 in Italian",
                () -> assertThat(List.of(
                                TopicFallbackName.of(
                                        Optional.empty(), NAMELESS_SEED_PATH, PLACE, DeliverableLanguage.ENGLISH),
                                TopicFallbackName.of(
                                        Optional.empty(), NAMELESS_SEED_PATH, PLACE, DeliverableLanguage.ITALIAN)))
                        .containsExactly("Topic 3", "Argomento 3"));
    }
}
