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
 * The closing line's two clauses of code (ADR-296 §6): where the tree has a code set, after the count of documents not
 * copied, *", and K files of code not copied"*, K stated at 0 and {@code file} singular for 1; and where entries of
 * {@code code-set-names.yaml} name no code set of the run, *"; D names in code-set-names.yaml name no code set of this
 * run"*, singular for 1, before the clause on what an earlier drawing left. Neither where this invocation drew no tree.
 *
 * <p>Asserted as a pure function of what the line is handed, as {@code TheClosingLineSaysWhatAnEarlierDrawingLeftTest}
 * asserts its clause.
 */
@Epic("The deliverable")
@Feature("Code in the deliverable")
@Issue("573")
@Link(name = "ADR-296", url = Adr.EACH_CODE_SET_IS_COPIED_WHOLE_UNDER_CODE, type = "adr")
class TheClosingLineCountsTheFilesOfCodeNotCopiedTest {

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

    /** Every document's copy of the tree this invocation drew stands. */
    private static final OptionalInt NO_DOCUMENT_LEFT_UNCOPIED = OptionalInt.of(0);

    /** How the line reads up to the count of documents not copied. */
    private static final String THE_COUNTS = " groups left unwritten and 0 documents not copied";

    /** A tree with no code set: the line is handed no count of code. */
    private static final OptionalInt NO_CODE_SET = OptionalInt.empty();

    /** Files of code whose copies do not stand: none, one, and two, in a tree with a code set. */
    private static final OptionalInt NO_FILE_OF_CODE = OptionalInt.of(0);

    private static final OptionalInt ONE_FILE_OF_CODE = OptionalInt.of(1);

    private static final OptionalInt TWO_FILES_OF_CODE = OptionalInt.of(2);

    /** Entries of the file of renames that name no code set of the run: none, one, and two. */
    private static final int NO_DORMANT_NAME = 0;

    private static final int ONE_DORMANT_NAME = 1;

    private static final int TWO_DORMANT_NAMES = 2;

    /** One other folder an earlier drawing left, counted by the clause after the code's. */
    private static final int ONE_FOLDER = 1;

    private static final int NO_FOLDER = 0;

    @Test
    @Story("The last thing an invocation says is what it produced and what is missing from it")
    @DisplayName("Where the tree has a code set, the closing line counts the files of code not copied after the documents, even when none")
    void countsTheFilesOfCodeNotCopiedWhereTheTreeHasACodeSetEvenAtNone() {
        claim(
                "none is stated as 0 files",
                () -> assertThat(lineFor(NO_DOCUMENT_LEFT_UNCOPIED, NO_FILE_OF_CODE, NO_DORMANT_NAME, NO_FOLDER))
                        .endsWith(THE_COUNTS + ", and 0 files of code not copied."));
        claim(
                "one in the singular",
                () -> assertThat(lineFor(NO_DOCUMENT_LEFT_UNCOPIED, ONE_FILE_OF_CODE, NO_DORMANT_NAME, NO_FOLDER))
                        .endsWith(THE_COUNTS + ", and 1 file of code not copied."));
        claim(
                "and two in the plural",
                () -> assertThat(lineFor(NO_DOCUMENT_LEFT_UNCOPIED, TWO_FILES_OF_CODE, NO_DORMANT_NAME, NO_FOLDER))
                        .endsWith(THE_COUNTS + ", and 2 files of code not copied."));
    }

    @Test
    @Story("The last thing an invocation says is what it produced and what is missing from it")
    @DisplayName("Where the tree has no code set, the closing line says nothing of code")
    void saysNothingOfCodeWhereTheTreeHasNoCodeSet() {
        claim(
                "the line ends at the count of documents not copied",
                () -> assertThat(lineFor(NO_DOCUMENT_LEFT_UNCOPIED, NO_CODE_SET, NO_DORMANT_NAME, NO_FOLDER))
                        .endsWith(THE_COUNTS + "."));
    }

    @Test
    @Story("The last thing an invocation says is what it produced and what is missing from it")
    @DisplayName("Where renames name no code set of this run, the closing line counts them, before what an earlier drawing left")
    void countsTheDormantNamesBeforeWhatAnEarlierDrawingLeft() {
        claim(
                "one in the singular, after the files of code",
                () -> assertThat(lineFor(NO_DOCUMENT_LEFT_UNCOPIED, NO_FILE_OF_CODE, ONE_DORMANT_NAME, NO_FOLDER))
                        .endsWith(THE_COUNTS + ", and 0 files of code not copied"
                                + "; 1 name in code-set-names.yaml names no code set of this run."));
        claim(
                "two in the plural, in a tree with no code set, where every rename is one",
                () -> assertThat(lineFor(NO_DOCUMENT_LEFT_UNCOPIED, NO_CODE_SET, TWO_DORMANT_NAMES, NO_FOLDER))
                        .endsWith(THE_COUNTS + "; 2 names in code-set-names.yaml name no code set of this run."));
        claim(
                "and before the clause on a folder an earlier drawing left",
                () -> assertThat(lineFor(NO_DOCUMENT_LEFT_UNCOPIED, NO_FILE_OF_CODE, ONE_DORMANT_NAME, ONE_FOLDER))
                        .endsWith(THE_COUNTS + ", and 0 files of code not copied"
                                + "; 1 name in code-set-names.yaml names no code set of this run"
                                + "; 1 folder left by an earlier drawing can be deleted by hand."));
    }

    @Test
    @Story("The last thing an invocation says is what it produced and what is missing from it")
    @DisplayName("Where this invocation drew no tree, the closing line says nothing of code or of renames")
    void saysNothingOfCodeWhereThisInvocationDrewNoTree() {
        claim(
                "handed two files of code and two dormant names, the line ends at the groups left unwritten",
                () -> assertThat(lineFor(OptionalInt.empty(), TWO_FILES_OF_CODE, TWO_DORMANT_NAMES, NO_FOLDER))
                        .endsWith(", with " + NO_GROUP_LEFT_UNWRITTEN + " groups left unwritten.")
                        .doesNotContain("code"));
    }

    /** The closing line of an invocation at the end of the path, handed what the drawing told of code. */
    private static String lineFor(
            OptionalInt documentsNotCopied, OptionalInt codeFilesNotCopied, int dormantCodeSetNames, int foldersLeftBehind) {
        return NextAction.line(
                everythingApproved(),
                SIXTY_ANSWERED,
                QUESTIONS_WRITTEN,
                AN_ARRANGEMENT,
                THE_GENERATION_MODEL,
                new NextAction.DeliverableSummary(
                        A_TREE,
                        NO_GROUP_LEFT_UNWRITTEN,
                        documentsNotCopied,
                        false,
                        foldersLeftBehind,
                        0,
                        codeFilesNotCopied,
                        dormantCodeSetNames),
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
