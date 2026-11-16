package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two questions a topic's overview is asked (ADR-402 §1, §3, §4): each written cluster under its own number among
 * the topic's clusters, titled, its writing's own citations taken out; at most 150 words; ADR-159's example drawn from
 * the numbers sent; the answer's one field and the language last; and the second question the first with one fixed
 * paragraph before its last.
 *
 * <p>Paragraphs are compared with their blanks collapsed, so a line broken inside one is not a difference; a paragraph
 * ends at a blank line.
 */
@Epic("Synthesis")
@Feature("Writing over a topic")
@Issue("830")
@Link(name = "ADR-402", url = Adr.A_TOPICS_OVERVIEW_CITES_ITS_WRITTEN_CLUSTERS, type = "adr")
class OverviewQuestionTest {

    private static final String TOPIC_NAME = "Irrigation and water rights";

    /** The words the record asks an overview to keep to (ADR-402 §3). */
    private static final int ONE_HUNDRED_AND_FIFTY_WORDS = 150;

    /**
     * The paragraph the second question adds, as the record gives it (ADR-402 §4): the code's constant is held to it
     * word for word.
     */
    private static final String SECOND_ASKING_ON_RECORD = "An earlier answer to this was turned down because a sentence"
            + " cited no group, or cited a number not shown above: every sentence must end with the bracketed number of"
            + " at least one group shown above.";

    /** The last paragraph of either question where the deliverable is Italian (ADR-402 §4, ADR-321 §3). */
    private static final String THE_LAST_PARAGRAPH_IN_ITALIAN = "Answer with one JSON object of one field: \"prose\","
            + " the overview, which ends with its last sentence. Write it in Italian.";

    /**
     * The most words that may follow the last cluster's writing: 256 tokens of {@code INSTRUCTION_RESERVE} at 2.0
     * tokens a word (ADR-402 §4).
     */
    private static final int WORDS_THE_RESERVE_HOLDS = 128;

    /** Clusters 1, 3 and 4 of a topic of four, the second unwritten; each writing cites documents of its own. */
    private static final List<OverviewSource> GROUPS_ONE_THREE_AND_FOUR = List.of(
            new OverviewSource(1, "Filters and their schedule", "The consortium cleans its filters each quarter [1][2]."),
            new OverviewSource(3, "What repairs cost", "Repairs cost more than inspections [4], as two reports say [1]."),
            new OverviewSource(4, "Turns of abstraction", "Each farm draws water in its turn [3]."));

    @Test
    @Story("A topic's overview reads what was written on its groups")
    @DisplayName("Each written group is sent under its own number and title, its own citations taken out")
    void sendsEachWrittenGroupUnderItsOwnNumberWithItsCitationsTakenOut() {
        List<String> paragraphs =
                paragraphsOf(OverviewQuestion.first(TOPIC_NAME, GROUPS_ONE_THREE_AND_FOUR, DeliverableLanguage.ENGLISH));

        claim(
                "groups 1, 3 and 4 are sent in that order, each as its number in brackets, its title, then its"
                        + " writing with every bracketed number of its own taken out with the blank before it",
                () -> assertThat(paragraphs)
                        .containsSubsequence(
                                "[1] Filters and their schedule The consortium cleans its filters each quarter.",
                                "[3] What repairs cost Repairs cost more than inspections, as two reports say.",
                                "[4] Turns of abstraction Each farm draws water in its turn."));
        claim(
                "the unwritten group 2 is shown nowhere in the question",
                () -> assertThat(String.join("\n", paragraphs)).doesNotContain("[2]"));
        claim(
                "the question opens naming the three groups sent and the topic",
                () -> assertThat(paragraphs)
                        .startsWith(
                                "Write a short overview of a topic from an archive, over what was written on 3 of its"
                                        + " groups.",
                                "The topic is called \"" + TOPIC_NAME + "\".",
                                "What was written on each group follows below, under the number to cite it by."));
    }

    @Test
    @Story("A topic's overview turned down for its citations is asked once more")
    @DisplayName("The second question is the first with one fixed paragraph before its last")
    void theSecondQuestionIsTheFirstWithOneParagraphBeforeTheLast() {
        List<String> first =
                paragraphsOf(OverviewQuestion.first(TOPIC_NAME, GROUPS_ONE_THREE_AND_FOUR, DeliverableLanguage.ITALIAN));
        List<String> second =
                paragraphsOf(OverviewQuestion.second(TOPIC_NAME, GROUPS_ONE_THREE_AND_FOUR, DeliverableLanguage.ITALIAN));
        List<String> firstWithTheParagraph = new ArrayList<>(first);
        firstWithTheParagraph.add(first.size() - 1, SECOND_ASKING_ON_RECORD);

        claim(
                "the paragraph the second question adds is the record's, word for word",
                () -> assertThat(collapsed(OverviewQuestion.SECOND_ASKING)).isEqualTo(SECOND_ASKING_ON_RECORD));
        claim(
                "the second question is the first, paragraph for paragraph, with that paragraph inserted before the"
                        + " last, and nothing else changed",
                () -> assertThat(second).containsExactlyElementsOf(firstWithTheParagraph));
    }

    @Test
    @Story("A topic's overview is short")
    @DisplayName("The question asks for at most 150 words, and names the answer's one field and the language last")
    void asksForAtMostTheWordLimitAndNamesTheLanguageLast() {
        List<String> paragraphs =
                paragraphsOf(OverviewQuestion.first(TOPIC_NAME, GROUPS_ONE_THREE_AND_FOUR, DeliverableLanguage.ITALIAN));

        claim(
                "the word limit is the record's 150",
                () -> assertThat(OverviewQuestion.WORD_LIMIT).isEqualTo(ONE_HUNDRED_AND_FIFTY_WORDS));
        claim(
                "the paragraph after the groups is the record's instruction, for three groups, at most 150 words,"
                        + " with the example drawn from groups 1, 3 and 4",
                () -> assertThat(paragraphs.get(paragraphs.size() - 2))
                        .isEqualTo("That is all 3 group(s). Write the overview in at most " + ONE_HUNDRED_AND_FIFTY_WORDS
                                + " words: say what the groups cover together, how they relate and where they differ."
                                + " Do not describe them one by one. End every sentence with the bracketed number of"
                                + " each group it draws on, written in square brackets exactly as the numbers appear"
                                + " above, such as [1] or [3][4], and use no other citation of any kind."));
        claim(
                "the last paragraph names the one field and asks for the writing in Italian",
                () -> assertThat(paragraphs.getLast()).isEqualTo(THE_LAST_PARAGRAPH_IN_ITALIAN));
    }

    @Test
    @Story("A topic's overview cites only groups it was given, in every sentence")
    @DisplayName("The example of a citation shows only numbers of groups sent")
    void theExampleShowsOnlyNumbersSent() {
        claim(
                "with groups 2, 5 and 7 sent the example is [2] or [5][7]",
                () -> assertThat(collapsed(OverviewQuestion.first(TOPIC_NAME, sources(2, 5, 7), DeliverableLanguage.ENGLISH)))
                        .contains("such as [2] or [5][7], and"));
        claim(
                "with groups 3 and 6 sent it is [3] or [3][6]",
                () -> assertThat(collapsed(OverviewQuestion.first(TOPIC_NAME, sources(3, 6), DeliverableLanguage.ENGLISH)))
                        .contains("such as [3] or [3][6], and"));
        claim(
                "with group 4 alone sent it is [4]",
                () -> assertThat(collapsed(OverviewQuestion.first(TOPIC_NAME, sources(4), DeliverableLanguage.ENGLISH)))
                        .contains("such as [4], and"));
    }

    @Test
    @Story("A topic's overview is short")
    @DisplayName("What the longer question says after the last group fits the reserve kept for instructions")
    void whatFollowsTheLastGroupStaysInsideTheReserve() {
        List<String> paragraphs =
                paragraphsOf(OverviewQuestion.second(TOPIC_NAME, GROUPS_ONE_THREE_AND_FOUR, DeliverableLanguage.ITALIAN));
        int lastGroup = paragraphs.indexOf("[4] Turns of abstraction Each farm draws water in its turn.");
        int wordsAfter = paragraphs.subList(lastGroup + 1, paragraphs.size()).stream()
                .mapToInt(paragraph -> paragraph.split(" ").length)
                .sum();

        claim(
                "the second question says at most " + WORDS_THE_RESERVE_HOLDS + " words after the last group's"
                        + " writing, the 256 tokens kept for instructions at two tokens a word",
                () -> assertThat(wordsAfter).isPositive().isLessThanOrEqualTo(WORDS_THE_RESERVE_HOLDS));
    }

    /** One short written cluster under each of {@code numbers}. */
    private static List<OverviewSource> sources(int... numbers) {
        return Arrays.stream(numbers)
                .mapToObj(number -> new OverviewSource(number, "Group " + number, "It holds documents."))
                .toList();
    }

    /** The question's paragraphs, each with its blanks collapsed to one space. */
    private static List<String> paragraphsOf(String question) {
        return Arrays.stream(question.strip().split("\\n\\s*\\n"))
                .map(OverviewQuestionTest::collapsed)
                .toList();
    }

    private static String collapsed(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }
}
