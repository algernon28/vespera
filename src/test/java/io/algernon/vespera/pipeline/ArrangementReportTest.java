package io.algernon.vespera.pipeline;

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
 * The words the page carries for the thing it is showing (ADR-122).
 *
 * <p>Everywhere this project names a cluster it says <em>cluster</em>, and the types behind this page
 * do. The page itself does not: the operator reading it has never opened {@code CONTEXT.md} and never
 * should have to, and our term would not stop them as an unfamiliar word — it would read in its
 * everyday sense, which is the one that entry spends a sentence refusing (ADR-052).
 *
 * <p>So the rendering and the name are deliberately two different words in one file, which is exactly
 * the shape a rename carried out with a replace-all destroys. These claims are what a rename has to
 * survive.
 */
@Epic("Arrangement")
@Feature("Arranging the documents")
@Issue("218")
@Link(name = "ADR-122", url = Adr.THE_VOCABULARY_BINDS_OUR_NAMES_NOT_RENDERED_PROSE, type = "adr")
@Link(name = "ADR-107", url = Adr.THE_ARRANGEMENT_GATE_APPROVES_A_NAMED_RUN, type = "adr")
class ArrangementReportTest {

    /** The short name the operator copies into {@code profile.yaml} to approve what they read. */
    private static final String APPROVAL_NAME = "arrangement-quiet-harbour";

    private static final String CORPUS_ROOT = "/archive";

    /** The larger of the two clusters on the page: nine documents against the one below it. */
    private static final int NINE_DOCUMENTS = 9;

    /** One exemplar holding two clusters, so the page has both a table and a count to write. */
    private static final ArrangementReport.Partition ONE_EXEMPLAR = new ArrangementReport.Partition(
            "seeds/safety.docx",
            List.of(
                    new ArrangementReport.Cluster(
                            "Fire doors", NINE_DOCUMENTS, "Fire doors in use", "file:///archive/fire-doors.docx"),
                    new ArrangementReport.Cluster("A note on stairwells", 1, "Stairwells", "file:///archive/note.txt")));

    @Test
    @Story("The page the operator approves is written in the operator's words")
    @DisplayName("The page calls each set of documents a group, in its table and in its explanation")
    void rendersEachClusterAsAGroup() {
        String page = ArrangementReport.render(APPROVAL_NAME, CORPUS_ROOT, List.of(ONE_EXEMPLAR));

        claim(
                "the column heading over the names reads Group, which is the word the reader is left"
                        + " with when they look at the table rather than read the page",
                () -> assertThat(page).contains("<th>Group</th>"));
        claim(
                "the count under the exemplar is given in groups, so the heading and the sentence above"
                        + " it agree on what has been counted",
                () -> assertThat(page).contains("group(s)"));
        claim(
                "and the closing paragraph explaining that a set of one document is a real outcome calls"
                        + " that a group too, since a reader meeting a second word there would take it"
                        + " for a second thing",
                () -> assertThat(page).contains("A group holding one document"));
    }

    @Test
    @Story("The page the operator approves is written in the operator's words")
    @DisplayName("Nothing on the page uses a word only this project's own glossary explains")
    void usesNoWordTheReaderWouldHaveToLookUp() {
        String page = ArrangementReport.render(APPROVAL_NAME, CORPUS_ROOT, List.of(ONE_EXEMPLAR));

        claim(
                "no heading, sentence or table cell on the page carries the term this project uses among"
                        + " itself for these sets of documents -- a reader who cannot look it up would"
                        + " not stop at it, they would read it in its everyday sense and be wrong",
                () -> assertThat(page).doesNotContainIgnoringCase("cluster"));
    }

    @Test
    @Story("The page the operator approves is written in the operator's words")
    @DisplayName("A run that arranged nothing says so in the same words as a run that arranged something")
    void saysSoInTheSameWordsWhenThereIsNothingToShow() {
        String page = ArrangementReport.render(APPROVAL_NAME, CORPUS_ROOT, List.of());

        claim(
                "the page still asks for the approval it exists to ask for, naming the arrangement the"
                        + " operator would be approving",
                () -> assertThat(page).contains(APPROVAL_NAME));
        claim(
                "and says there is nothing to arrange without reaching for a word of ours to say it,"
                        + " because the emptiest page is the one most likely to be rewritten carelessly",
                () -> assertThat(page).doesNotContainIgnoringCase("cluster"));
    }
}
