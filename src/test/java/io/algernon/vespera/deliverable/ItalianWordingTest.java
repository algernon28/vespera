package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.algernon.vespera.synthesis.Unwritten;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Italian of an Italian deliverable's fixed words, held to the record word for word and to the rules every English
 * sentence keeps (ADR-321 §4, §5): the eight sentences of ADR-174 and ADR-319, a copy's outcome, the line on pictures not
 * shown, the index's code sets and a split cluster's Confluence title. The pages these land on are held by {@link
 * AnItalianTreeIsDrawnInItalianTest}.
 *
 * <p>The report says <em>group</em> where the code says cluster (ADR-122); the Italian says <em>gruppo</em>.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-321", url = Adr.THE_DELIVERABLE_IS_IN_THE_LANGUAGE_THE_PROFILE_NAMES, type = "adr")
@Link(name = "ADR-174", url = Adr.A_PAGE_NOTHING_WAS_WRITTEN_OVER_SAYS_WHY, type = "adr")
@Link(name = "ADR-319", url = Adr.A_CLUSTER_TURNED_DOWN_THE_LAST_TWO_TIMES_STANDS_WITHOUT_WRITING, type = "adr")
class ItalianWordingTest {

    private static final DeliverableLanguage ITALIAN = DeliverableLanguage.ITALIAN;

    /** How many cases ADR-174 gives a sentence: four kinds of fault, and three cases with no row. */
    private static final int SEVEN_CASES = 7;

    /** The Italian of the three words a re-run can make false (ADR-174 §5), as whole words, in any form of the first. */
    private static final Pattern WORDS_A_RE_RUN_CAN_MAKE_FALSE =
            Pattern.compile("(?iU)\\b(permanent\\w*|mai|sempre)\\b");

    /** The word this project uses, which Italian also borrows, and the English rendering, neither on an Italian page. */
    private static final List<String> NOT_GRUPPO = List.of("cluster", "group");

    /** A seed partition's place and a cluster's, as a Confluence title writes them. */
    private static final String PLACE = "1.1";

    /** A cluster's title, as the model wrote it. */
    private static final String A_TITLE = "Porte tagliafuoco";

    /** The second page of a split cluster. */
    private static final int THE_SECOND_PAGE = 2;

    /** How many pictures a line says are not shown, past one, so the plural is the one written. */
    private static final int THREE_PICTURES = 3;

    @Test
    @Story("An Italian deliverable says in Italian why nothing was written over a group")
    @DisplayName("The code gives each case the Italian the record gives it, and has no Italian sentence the record does not")
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
    }

    @Test
    @Story("A group whose answer keeps being turned down stands without writing, and its page says so")
    @DisplayName("In Italian, the line saying a group stands without writing is the record's, under a turned-down answer in a finished run alone")
    void theStandingLineInItalianIsTheRecords() {
        List<Unwritten> turnedDown = List.of(
                Unwritten.FAULT_PROMPT_EVALUATION_CEILING,
                Unwritten.FAULT_ANSWER_RAN_OUT_OF_ROOM,
                Unwritten.FAULT_SCHEMA_VIOLATION,
                Unwritten.FAULT_CITATION_NOT_IN_RANGE);
        claim(
                "under each of the four reasons a fault on record gives, in a run whose writing is finished, the line is"
                        + " the record's Italian",
                () -> assertThat(turnedDown).allSatisfy(why -> assertThat(UnwrittenSentences.standing(why, true, ITALIAN))
                        .contains(ItalianWordingOnRecord.STANDS_WITHOUT_WRITING)));
        claim(
                "and there is none in a run whose writing is not finished, nor under a reason no fault gives",
                () -> {
                    assertThat(UnwrittenSentences.standing(Unwritten.FAULT_SCHEMA_VIOLATION, false, ITALIAN)).isEmpty();
                    assertThat(UnwrittenSentences.standing(Unwritten.NOT_REACHED, true, ITALIAN)).isEmpty();
                    assertThat(UnwrittenSentences.standing(Unwritten.NO_SENDABLE_DOCUMENT, true, ITALIAN)).isEmpty();
                });
    }

    @Test
    @Story("An Italian deliverable says in Italian why nothing was written over a group")
    @DisplayName("Every Italian sentence keeps the rules every English one keeps")
    void everyItalianSentenceKeepsTheRulesTheEnglishKeeps() {
        List<String> eight = new ArrayList<>(ItalianWordingOnRecord.EVERY_SENTENCE);
        eight.add(ItalianWordingOnRecord.STANDS_WITHOUT_WRITING);
        claim(
                "there are " + SEVEN_CASES + " reasons and the line beneath them, and no two are the same",
                () -> assertThat(eight).hasSize(SEVEN_CASES + 1).doesNotHaveDuplicates());
        claim(
                "each says gruppo, and neither the word this project uses, which Italian also borrows, nor the English"
                        + " rendering",
                () -> assertThat(eight).allSatisfy(sentence -> assertThat(sentence)
                        .contains("gruppo")
                        .doesNotContainIgnoringCase(NOT_GRUPPO.get(0))
                        .doesNotContainIgnoringCase(NOT_GRUPPO.get(1))));
        claim(
                "none says permanent, never or always in Italian: running again can make each of them false",
                () -> assertThat(eight).allSatisfy(sentence ->
                        assertThat(WORDS_A_RE_RUN_CAN_MAKE_FALSE.matcher(sentence).find()).isFalse()));
        claim(
                "none carries the name the code gives a reason",
                () -> assertThat(eight).allSatisfy(sentence -> assertThat(sentence)
                        .doesNotContain(Arrays.stream(ClusterFaultKind.values()).map(Enum::name).toList())));
        claim(
                "and each is one plain line ending in a full stop, with nothing markup could be made of",
                () -> assertThat(eight).allSatisfy(sentence -> assertThat(sentence)
                        .endsWith(".")
                        .doesNotContain("\n", "*", "[", "]", "(", ")", "<", ">", "`", "|", "&", "\\", "_")));
    }

    @Test
    @Story("An Italian deliverable's pages say in Italian what Vespera writes on them")
    @DisplayName("A copy that does not stand says why in Italian, and one that stands says nothing")
    void aCopysOutcomeIsSaidInItalian() {
        claim(
                "each outcome but copied has the record's Italian, and copied has no sentence",
                () -> {
                    assertThat(CopyOutcome.CHANGED.sentence(ITALIAN)).isEqualTo(ItalianWordingOnRecord.CHANGED);
                    assertThat(CopyOutcome.GONE.sentence(ITALIAN)).isEqualTo(ItalianWordingOnRecord.GONE);
                    assertThat(CopyOutcome.NO_ROOM.sentence(ITALIAN)).isEqualTo(ItalianWordingOnRecord.NO_ROOM);
                    assertThat(CopyOutcome.NOT_COPIED.sentence(ITALIAN)).isEqualTo(ItalianWordingOnRecord.NOT_COPIED);
                    assertThat(CopyOutcome.COPIED.sentence(ITALIAN)).isEmpty();
                });
        claim(
                "and the word documents.csv carries for each is the same in either language, a name and not prose",
                () -> assertThat(CopyOutcome.values()).extracting(CopyOutcome::word)
                        .containsExactly("copied", "changed", "gone", "no-room", "not-copied"));
    }

    @Test
    @Story("An Italian deliverable's pages say in Italian what Vespera writes on them")
    @DisplayName("The line on pictures not shown is in Italian, singular for one and plural for more")
    void theLineOnPicturesNotShownIsInItalian() {
        claim(
                "one picture not shown is said in the singular",
                () -> assertThat(EntryPictures.notShownLine(1, ITALIAN))
                        .isEqualTo("<p><em>1" + ItalianWordingOnRecord.ONE_MORE_PICTURE + "</em></p>\n"));
        claim(
                THREE_PICTURES + " are said in the plural",
                () -> assertThat(EntryPictures.notShownLine(THREE_PICTURES, ITALIAN))
                        .isEqualTo("<p><em>" + THREE_PICTURES + ItalianWordingOnRecord.MORE_PICTURES + "</em></p>\n"));
    }

    @Test
    @Story("An Italian deliverable's pages say in Italian what Vespera writes on them")
    @DisplayName("The index's code sets are headed and described in Italian, and a set at the archive's top named in Italian")
    void theIndexsCodeSetsAreInItalian() throws IOException {
        StringBuilder index = new StringBuilder();
        IndexPage.codeHeading(index, ITALIAN);
        IndexPage.codeRow(index, "a-set", "", 2, 1, ITALIAN);
        String read = asRead(index.toString());

        claim(
                "the heading, the sentence and the four heads of the table are the record's Italian",
                () -> {
                    assertThat(read).contains("<h2>" + ItalianWordingOnRecord.CODE + "</h2>");
                    assertThat(read).contains(ItalianWordingOnRecord.THE_CODE_SENTENCE);
                    assertThat(read).contains(ItalianWordingOnRecord.THE_TABLE_OF_CODE_SETS.stream()
                            .map(head -> "<th>" + head + "</th>")
                            .reduce("", String::concat));
                });
        claim(
                "and a set lying at the corpus root names its folder as the record's Italian does",
                () -> assertThat(read).contains("<td>" + ItalianWordingOnRecord.THE_ARCHIVES_TOP + "</td>"));
    }

    @Test
    @Story("An Italian deliverable's pages are titled in Italian where Vespera titles them")
    @DisplayName("A split group's later Confluence pages say pagina in their titles, and its first page no page at all")
    void aSplitClustersConfluenceTitleSaysPagina() {
        claim(
                "the second page's title is the text, then the place, pagina and its number",
                () -> assertThat(ConfluenceTitles.cluster(A_TITLE, PLACE, THE_SECOND_PAGE, ITALIAN))
                        .isEqualTo(A_TITLE + " (" + PLACE + ItalianWordingOnRecord.PAGE_IN_A_TITLE + THE_SECOND_PAGE
                                + ")"));
        claim(
                "the first page's carries no page, as in English",
                () -> assertThat(ConfluenceTitles.cluster(A_TITLE, PLACE, 1, ITALIAN))
                        .isEqualTo(A_TITLE + " (" + PLACE + ")"));
        claim(
                "and an English rendering's second page still says page",
                () -> assertThat(ConfluenceTitles.cluster(A_TITLE, PLACE, THE_SECOND_PAGE,
                                DeliverableLanguage.ENGLISH))
                        .isEqualTo(A_TITLE + " (" + PLACE + " page " + THE_SECOND_PAGE + ")"));
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
