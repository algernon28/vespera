package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The name a subtopic takes where the model's is turned down (ADR-406 §6, ADR-407 §7, ADR-409 §6): the Docling title
 * of its strongest survivor, the first its question sends, then that survivor's filename stem, then the subtopic's
 * place among its topic's subtopics, as {@code Subtopic n} in English and {@code Sottoargomento n} in Italian. A
 * survivor's title and stem are its own words, and are never translated.
 *
 * <p><b>By reflection</b>, {@code TopicFallbackName.ofSubtopic(Optional<String>, String, int, DeliverableLanguage)}
 * being looked up by its name and parameters, so that this class compiles against the tree before ADR-409 is built,
 * where every claim here fails for want of the method.
 */
@Epic("Arrangement")
@Feature("Topic names")
@Issue("832")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
@Link(name = "ADR-406", url = Adr.A_CROWDED_TOPIC_IS_DIVIDED_INTO_SUBTOPICS, type = "adr")
class SubtopicFallbackNameTest {

    /** The strongest survivor's path relative to the corpus root. */
    private static final String SURVIVOR_PATH = "impianti/Verifica pompe 2019.pdf";

    /** What the survivor calls itself, in its own language. */
    private static final String SURVIVOR_TITLE = "Verifica delle pompe di sollevamento";

    /** A survivor whose filename is only an extension, so that its stem says nothing. */
    private static final String NAMELESS_SURVIVOR_PATH = "impianti/.pdf";

    /** The subtopic's place among its topic's subtopics, counted from one. */
    private static final int PLACE = 2;

    @Test
    @Story("A name that fails falls back to the strongest contributing seed")
    @DisplayName("A subtopic falls back to its strongest document's own title, then its filename, then its place in the pages' language")
    void titleThenStemThenItsPlaceInTheDeliverableLanguage() throws ReflectiveOperationException {
        Method ofSubtopic = TopicFallbackName.class.getMethod(
                "ofSubtopic", Optional.class, String.class, int.class, DeliverableLanguage.class);

        claim("with the survivor's title at hand, the subtopic is named by it, word for word, in an English deliverable",
                () -> assertThat(ofSubtopic.invoke(
                                null, Optional.of(SURVIVOR_TITLE), SURVIVOR_PATH, PLACE, DeliverableLanguage.ENGLISH))
                        .isEqualTo(SURVIVOR_TITLE));
        claim("with no title, or a blank one, by the survivor's filename without its folders or its extension",
                () -> assertThat(List.of(
                                ofSubtopic.invoke(null, Optional.empty(), SURVIVOR_PATH, PLACE, DeliverableLanguage.ITALIAN),
                                ofSubtopic.invoke(null, Optional.of("  "), SURVIVOR_PATH, PLACE, DeliverableLanguage.ITALIAN)))
                        .containsExactly("Verifica pompe 2019", "Verifica pompe 2019"));
        claim("with neither, by its place, 2, as Subtopic 2 in English and Sottoargomento 2 in Italian",
                () -> assertThat(List.of(
                                ofSubtopic.invoke(
                                        null, Optional.empty(), NAMELESS_SURVIVOR_PATH, PLACE, DeliverableLanguage.ENGLISH),
                                ofSubtopic.invoke(
                                        null, Optional.empty(), NAMELESS_SURVIVOR_PATH, PLACE, DeliverableLanguage.ITALIAN)))
                        .containsExactly("Subtopic 2", "Sottoargomento 2"));
    }
}
