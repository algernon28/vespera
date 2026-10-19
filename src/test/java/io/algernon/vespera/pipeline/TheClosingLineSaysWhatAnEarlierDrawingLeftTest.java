package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The closing line's clause on what an earlier drawing left in the tree (ADR-280 §4, paying ADR-276 §6): an earlier
 * layout's {@code documents/} is named and said to be deletable by hand, any other folder left behind is counted and
 * not named, and with nothing left, or no tree drawn by this invocation, there is no clause.
 *
 * <p>Asserted as a pure function of what the line is handed, as {@code TheClosingLineCountsTheCopiesThatDoNotStandTest}
 * asserts the copies' clause. That a drawing over a flat layout hands it over is {@code DeliverableInvocationTest}'s,
 * and what the writer reports is {@code TheClusterFolderHoldsItsPageAndItsCopiesTest}'s.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Issue("570")
@Link(name = "ADR-280", url = Adr.A_CLUSTERS_FOLDER_HOLDS_ITS_PAGE_AND_ITS_SURVIVORS_COPIES, type = "adr")
@Link(name = "ADR-276", url = Adr.THE_DELIVERABLE_IS_DRAWN_AT_A_ROOT_THE_OPERATOR_MAY_NAME, type = "adr")
class TheClosingLineSaysWhatAnEarlierDrawingLeftTest {

    /** The short name of the arrangement this invocation arrived at, and the one the profile approves. */
    private static final String AN_ARRANGEMENT = "9f2c41ab77de";

    private static final int SIXTY_ANSWERED = 60;

    private static final boolean QUESTIONS_WRITTEN = true;

    private static final String THE_GENERATION_MODEL = "a-generation-model:8b";

    private static final boolean APPROVAL_MATCHES_THIS_INVOCATION = true;

    private static final String RECORDED_BY_THE_OPERATOR = "recorded by the operator";

    /** A tree's folder, named for a generation run, as the line is handed it. */
    private static final Path A_TREE = Path.of("work", "deliverable", "a".repeat(64)).toAbsolutePath();

    /** No group of the tree is left unwritten. */
    private static final int NO_GROUP_LEFT_UNWRITTEN = 0;

    /** Every copy of the tree this invocation drew stands. */
    private static final OptionalInt NO_DOCUMENT_LEFT_UNCOPIED = OptionalInt.of(0);

    /** How the line ends before any clause about what an earlier drawing left. */
    private static final String THE_COUNTS = " groups left unwritten and 0 documents not copied";

    /** What the line says of an earlier layout's folder of documents. */
    private static final String THE_DOCUMENTS_FOLDER =
            "documents/ holds an earlier layout's copies and can be deleted by hand";

    private static final boolean THE_DOCUMENTS_FOLDER_WAS_LEFT = true;

    private static final boolean NO_DOCUMENTS_FOLDER_WAS_LEFT = false;

    private static final int ONE_FOLDER = 1;

    private static final int TWO_FOLDERS = 2;

    private static final int NO_FOLDER = 0;

    @Test
    @Story("The last thing an invocation says is what it produced and what is missing from it")
    @DisplayName("Where an earlier layout's folder of documents was left in the tree, the closing line names it and says it can be deleted by hand")
    void namesTheDocumentsFolderAsAnEarlierLayoutsCopies() {
        claim(
                "after the count of documents not copied, the line names the documents folder, says it holds an"
                        + " earlier layout's copies and that it can be deleted by hand, and ends there",
                () -> assertThat(lineFor(NO_DOCUMENT_LEFT_UNCOPIED, THE_DOCUMENTS_FOLDER_WAS_LEFT, NO_FOLDER))
                        .endsWith(THE_COUNTS + "; " + THE_DOCUMENTS_FOLDER + "."));
    }

    @Test
    @Story("The last thing an invocation says is what it produced and what is missing from it")
    @DisplayName("Where other folders an earlier drawing wrote were left in the tree, the closing line counts them without naming them")
    void countsTheOtherFoldersLeftBehindWithoutNamingThem() {
        claim(
                "one folder is counted in the singular",
                () -> assertThat(lineFor(NO_DOCUMENT_LEFT_UNCOPIED, NO_DOCUMENTS_FOLDER_WAS_LEFT, ONE_FOLDER))
                        .endsWith(THE_COUNTS + "; 1 folder left by an earlier drawing can be deleted by hand."));
        claim(
                "and two in the plural, a count and no name, a group's folder being named from words a model wrote",
                () -> assertThat(lineFor(NO_DOCUMENT_LEFT_UNCOPIED, NO_DOCUMENTS_FOLDER_WAS_LEFT, TWO_FOLDERS))
                        .endsWith(THE_COUNTS + "; 2 folders left by an earlier drawing can be deleted by hand."));
    }

    @Test
    @Story("The last thing an invocation says is what it produced and what is missing from it")
    @DisplayName("Where both were left, the closing line names the folder of documents and then counts the others")
    void namesTheDocumentsFolderAndCountsTheOthersWhereBothWereLeft() {
        claim(
                "the documents folder first, then the count of the others after a comma and an and, in one clause",
                () -> assertThat(lineFor(NO_DOCUMENT_LEFT_UNCOPIED, THE_DOCUMENTS_FOLDER_WAS_LEFT, TWO_FOLDERS))
                        .endsWith(THE_COUNTS + "; " + THE_DOCUMENTS_FOLDER
                                + ", and 2 folders left by an earlier drawing can be deleted by hand."));
    }

    @Test
    @Story("The last thing an invocation says is what it produced and what is missing from it")
    @DisplayName("Where nothing was left, or this invocation drew no tree, the closing line says nothing of an earlier drawing")
    void saysNothingOfAnEarlierDrawingWhereNothingWasLeft() {
        claim(
                "with nothing left behind, the line ends at the count of documents not copied",
                () -> assertThat(lineFor(NO_DOCUMENT_LEFT_UNCOPIED, NO_DOCUMENTS_FOLDER_WAS_LEFT, NO_FOLDER))
                        .endsWith(THE_COUNTS + "."));
        claim(
                "and where this invocation drew no tree, it ends at the groups left unwritten and says nothing of a"
                        + " drawing it did not make, even handed a documents folder and two others as left behind",
                () -> assertThat(lineFor(OptionalInt.empty(), THE_DOCUMENTS_FOLDER_WAS_LEFT, TWO_FOLDERS))
                        .endsWith(", with " + NO_GROUP_LEFT_UNWRITTEN + " groups left unwritten.")
                        .doesNotContain("earlier"));
    }

    /** The closing line of an invocation at the end of the path, handed what the drawing found left behind. */
    private static String lineFor(OptionalInt documentsNotCopied, boolean documentsFolderLeftBehind, int foldersLeftBehind) {
        return NextAction.line(
                everythingApproved(),
                SIXTY_ANSWERED,
                QUESTIONS_WRITTEN,
                AN_ARRANGEMENT,
                THE_GENERATION_MODEL,
                new NextAction.DeliverableSummary(
                        A_TREE, NO_GROUP_LEFT_UNWRITTEN, documentsNotCopied, documentsFolderLeftBehind, foldersLeftBehind),
                APPROVAL_MATCHES_THIS_INVOCATION);
    }

    /** Every value answered, the arrangement among them. */
    private static Profile everythingApproved() {
        return ProfileFixture.profile()
                .seedFolder("/corpus/exemplars", RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor("0.4", RECORDED_BY_THE_OPERATOR)
                .embeddingModel("embeddinggemma", RECORDED_BY_THE_OPERATOR)
                .relevanceScoreFloor("0.62", RECORDED_BY_THE_OPERATOR)
                .arrangementApproved(AN_ARRANGEMENT, RECORDED_BY_THE_OPERATOR)
                .build();
    }
}
