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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The question a topic is named by (ADR-403 §5): in English whatever the deliverable language, the passages after its
 * opening, the instruction after the passages, and the language's English name its last word.
 */
@Epic("Synthesis")
@Feature("Naming the topics")
@Issue("829")
@Link(name = "ADR-403", url = Adr.THE_GENERATION_MODEL_NAMES_EACH_TOPIC, type = "adr")
class TopicNameQuestionTest {

    private static final NamingPassage ON_FILTERS =
            new NamingPassage("The consortium cleans the canal filters each quarter.", 8);

    private static final NamingPassage ON_WATER_RIGHTS =
            new NamingPassage("Water rights are allotted by the hour of the day.", 10);

    private static final List<NamingPassage> TWO_PASSAGES = List.of(ON_FILTERS, ON_WATER_RIGHTS);

    @Test
    @Story("A topic is named by one fixed question")
    @DisplayName("The question for two passages, in Italian, is the one the decision gives, word for word")
    void isTheRecordsWordingWordForWord() {
        String expected = "Name a topic of an archive from 2 passage(s) of the documents it was found in.\n"
                + "\n"
                + "The passages follow below, the most typical of the topic first, a blank line apart.\n"
                + "\n"
                + ON_FILTERS.text() + "\n"
                + "\n"
                + ON_WATER_RIGHTS.text() + "\n"
                + "\n"
                + "That is all 2 passage(s). Name what they have in common as a heading of at most 8 words for the"
                + " topic: no sentence, no quotation marks and no full stop.\n"
                + "\n"
                + "Answer with one JSON object of one field: \"name\", the topic's name, on one line. Write it in"
                + " Italian.";

        claim(
                "the question opens with the count of passages, sends them in the order given a blank line apart,"
                        + " asks for at most 8 words, and ends naming the one field and Italian, with nothing after",
                () -> assertThat(TopicNameQuestion.of(TWO_PASSAGES, DeliverableLanguage.ITALIAN)).isEqualTo(expected));
    }

    @Test
    @Story("A topic is named by one fixed question")
    @DisplayName("The English and the Italian questions differ in the language's name alone, which is the last word")
    void namesTheLanguageLastAndDiffersInItAlone() {
        String english = TopicNameQuestion.of(TWO_PASSAGES, DeliverableLanguage.ENGLISH);
        String italian = TopicNameQuestion.of(TWO_PASSAGES, DeliverableLanguage.ITALIAN);

        claim(
                "the English question ends asking for the name in English",
                () -> assertThat(english).endsWith("Write it in English."));
        claim(
                "and the Italian one is the English one with that last word in Italian, and nothing else changed",
                () -> assertThat(italian).isEqualTo(english.replaceFirst("English\\.$", "Italian.")));
    }
}
