package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The closing line's clause on the copies (ADR-274 §7): it counts the documents whose copy does not stand in the tree
 * this invocation wrote, at 0 too and in the singular for 1, and is left out where this invocation wrote no tree.
 *
 * <p>Asserted as a pure function of what the line is handed, as {@code NextActionTest} asserts the rest of it. Where
 * the generation step hands the count over is {@code TheTreeInHtmlInvocationTest}'s.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
class TheClosingLineCountsTheCopiesThatDoNotStandTest {

    /** The short name of the arrangement this invocation arrived at, and the one the profile approves. */
    private static final String AN_ARRANGEMENT = "9f2c41ab77de";

    private static final int SIXTY_ANSWERED = 60;

    private static final boolean QUESTIONS_WRITTEN = true;

    private static final String THE_GENERATION_MODEL = "a-generation-model:8b";

    private static final boolean APPROVAL_MATCHES_THIS_INVOCATION = true;

    private static final String RECORDED_BY_THE_OPERATOR = "recorded by the operator";

    /** A tree's folder, named for a generation run, as the line is handed it. */
    private static final Path A_TREE = Path.of("work", "deliverable", "a".repeat(64)).toAbsolutePath();

    /** No group of the tree is left unwritten, so the line's end is about the copies alone. */
    private static final int NO_GROUP_LEFT_UNWRITTEN = 0;

    @Test
    @Story("The last thing an invocation says is what it produced and what is missing from it")
    @DisplayName("Where this invocation wrote no tree, the closing line names the index and says nothing of copies")
    void leavesTheCopiesOutWhereThisInvocationWroteNoTree() {
        String line = lineFor(OptionalInt.empty());

        claim(
                "the line still names the tree's index and the groups left unwritten",
                () -> assertThat(line)
                        .contains(A_TREE.resolve("index.html").toString())
                        .endsWith(", with " + NO_GROUP_LEFT_UNWRITTEN + " groups left unwritten."));
        claim(
                "and it says nothing of copies: a count of a tree this invocation did not write would be a count it"
                        + " did not make",
                () -> assertThat(line).doesNotContain("copied"));
    }

    @Test
    @Story("The last thing an invocation says is what it produced and what is missing from it")
    @DisplayName("Where this invocation wrote the tree, the closing line counts its documents not copied, at 0 too")
    void countsTheDocumentsNotCopiedEvenAtNone() {
        claim(
                "with every copy standing, the line says so in a count of 0, in the plural",
                () -> assertThat(lineFor(OptionalInt.of(0)))
                        .endsWith(" groups left unwritten and 0 documents not copied."));
        claim(
                "one document not copied is counted in the singular",
                () -> assertThat(lineFor(OptionalInt.of(1)))
                        .endsWith(" groups left unwritten and 1 document not copied."));
        claim(
                "and two in the plural",
                () -> assertThat(lineFor(OptionalInt.of(2)))
                        .endsWith(" groups left unwritten and 2 documents not copied."));
    }

    /** The closing line of an invocation at the end of the path, handed {@code documentsNotCopied}. */
    private static String lineFor(OptionalInt documentsNotCopied) {
        return NextAction.line(
                everythingApproved(),
                SIXTY_ANSWERED,
                QUESTIONS_WRITTEN,
                AN_ARRANGEMENT,
                THE_GENERATION_MODEL,
                new NextAction.DeliverableSummary(A_TREE, NO_GROUP_LEFT_UNWRITTEN, documentsNotCopied),
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
