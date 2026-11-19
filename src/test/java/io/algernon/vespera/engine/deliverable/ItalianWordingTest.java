package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.synthesis.ClusterFaultKind;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.Unwritten;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Italian of an Italian deliverable's fixed words, held to the record word for word and to the rules every English
 * sentence keeps (ADR-321 §4, §5): the eight sentences of ADR-174 and ADR-319, a copy's outcome, the line on pictures not
 * shown, the index's code sets and the index's paragraph, whose sentence on the {@code topic} and {@code subtopic}
 * columns ADR-415 §6 words, and the words of a topic's and a subtopic's page (ADR-415 §10).
 * The pages these land on are held by {@link AnItalianTreeIsDrawnInItalianTest}; a split cluster's Confluence title moved with the Confluence rendering into {@code confluence} (ADR-333 §1), where
 * {@code TheConfluenceRenderingIsDrawnInItalianTest} holds it.
 *
 * <p>The report says <em>group</em> where the code says cluster (ADR-122); the Italian says <em>gruppo</em>.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-321", url = Adr.THE_DELIVERABLE_IS_IN_THE_LANGUAGE_THE_PROFILE_NAMES, type = "adr")
@Link(name = "ADR-174", url = Adr.A_PAGE_NOTHING_WAS_WRITTEN_OVER_SAYS_WHY, type = "adr")
@Link(name = "ADR-319", url = Adr.A_CLUSTER_TURNED_DOWN_THE_LAST_TWO_TIMES_STANDS_WITHOUT_WRITING, type = "adr")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
class ItalianWordingTest {

    private static final DeliverableLanguage ITALIAN = DeliverableLanguage.ITALIAN;

    @Test
    @Story("An Italian deliverable says in Italian why nothing was written over a group")
    @DisplayName("The code gives each case and the index's paragraph the Italian the record gives them, and has no Italian sentence for a case the record does not")
    void theCodesItalianForEveryCaseIsTheRecords() {
        claim(
                "for every way an answer can be turned down, the Italian sentence is the record's, word for word",
                () -> assertThat(ClusterFaultKind.values()).allSatisfy(kind -> assertThat(
                                UnwrittenSentences.of(Unwritten.of(kind), ITALIAN))
                        .isEqualTo(ItalianWordingOnRecord.forKind(kind))));
        claim(
                "and so is the Italian for each of the three cases no reason is recorded for",
                () -> {
                    assertThat(UnwrittenSentences.of(Unwritten.NO_SENDABLE_DOCUMENT, ITALIAN))
                            .isEqualTo(ItalianWordingOnRecord.NO_DOCUMENT_COULD_BE_SENT);
                    assertThat(UnwrittenSentences.of(Unwritten.NOTHING_FITS_THE_WINDOW, ITALIAN))
                            .isEqualTo(ItalianWordingOnRecord.NO_DOCUMENT_FITS_THE_ROOM);
                    assertThat(UnwrittenSentences.of(Unwritten.NOT_REACHED, ITALIAN))
                            .isEqualTo(ItalianWordingOnRecord.WRITING_STOPPED_BEFORE_IT);
                });
        claim(
                "and the code has no Italian sentence the record does not",
                () -> assertThat(Arrays.stream(Unwritten.values()).map(why -> UnwrittenSentences.of(why, ITALIAN)))
                        .containsExactlyInAnyOrderElementsOf(ItalianWordingOnRecord.EVERY_SENTENCE));
        claim(
                "and the index's paragraph is the record's Italian, word for word, saying that the topic column holds"
                        + " the name of a topic, the subtopic column that of a subtopic, and that each numbered folder"
                        + " beside the index is a topic's, holding its page",
                () -> assertThat(FixedWords.indexParagraph(ITALIAN))
                        .isEqualTo(ItalianWordingOnRecord.THE_INDEXS_PARAGRAPH));
    }

    @Test
    @Story("An Italian deliverable draws a topic's and a subtopic's page in Italian")
    @DisplayName("The heads of a topic's tables, a subtopic's line, and the See also heading and sentence are the record's Italian")
    @Issue("833")
    @Link(name = "ADR-415", url = Adr.A_TOPIC_IS_A_PAGE_CARRYING_ITS_OVERVIEW_AND_ITS_SUBTOPICS_ARE_PAGES_BENEATH_IT, type = "adr")
    void theTopicsAndSubtopicsPagesWordsAreTheRecordsItalian() {
        claim(
                "the heads of a topic's or a subtopic's table of groups put the group's number first, in Italian",
                () -> assertThat(FixedWords.topicTableHeads(ITALIAN))
                        .containsExactlyElementsOf(ItalianWordingOnRecord.THE_TOPICS_TABLE_OF_CLUSTERS));
        claim(
                "the heads of a divided topic's table of subtopics are the record's Italian",
                () -> assertThat(FixedWords.subtopicTableHeads(ITALIAN))
                        .containsExactlyElementsOf(ItalianWordingOnRecord.THE_TABLE_OF_SUBTOPICS));
        claim(
                "a subtopic's page names its topic after the record's Italian words",
                () -> assertThat(FixedWords.inTheTopic(ITALIAN)).isEqualTo(ItalianWordingOnRecord.IN_THE_TOPIC));
        claim(
                "and the See also heading and its sentence are the record's Italian, word for word",
                () -> {
                    assertThat(FixedWords.seeAlso(ITALIAN)).isEqualTo(ItalianWordingOnRecord.SEE_ALSO);
                    assertThat(FixedWords.seeAlsoSentence(ITALIAN)).isEqualTo(ItalianWordingOnRecord.THE_SEE_ALSO_SENTENCE);
                });
    }

    /** {@code html} as a reader reads it: the five entities read back, so an escaped apostrophe reads as one. */
    static String asRead(String html) {
        return html.replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }
}
