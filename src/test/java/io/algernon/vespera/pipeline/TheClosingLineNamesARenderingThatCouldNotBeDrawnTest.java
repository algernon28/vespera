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
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The closing line's clause on a plugin whose drawing failed (ADR-333 §11): one per such plugin, in the order the
 * drawing tells them, each naming the plugin and the type of what it threw, and never the exception's message,
 * following every other clause of the sentence about the tree and preceding its last full stop; and nothing where no
 * plugin failed or this invocation drew no tree.
 *
 * <p>Asserted as a pure function of what the line is handed, as {@code NextActionTest} asserts the rest of it. That
 * the generation step hands the plugins over, and that the invocation exits as it would have, is {@code
 * EveryPluginDrawsOnTheRecordedPathInvocationTest}'s.
 */
@Epic("The deliverable")
@Feature("The plugins")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class TheClosingLineNamesARenderingThatCouldNotBeDrawnTest {

    private static final String AN_ARRANGEMENT = "9f2c41ab77de";

    private static final int SIXTY_ANSWERED = 60;

    private static final boolean QUESTIONS_WRITTEN = true;

    private static final String THE_GENERATION_MODEL = "a-generation-model:8b";

    private static final boolean APPROVAL_MATCHES_THIS_INVOCATION = true;

    private static final String RECORDED_BY_THE_OPERATOR = "recorded by the operator";

    /** A tree's folder, named for a generation run, as the line is handed it. */
    private static final Path A_TREE = Path.of("work", "deliverable", "a".repeat(64)).toAbsolutePath();

    /** What the line says of a rendering not drawn, its plugin's name and the type in its two places. */
    private static final String THE_CLAUSE =
            "; the %s rendering could not be drawn (%s), so a publish to it is refused until a drawing finishes";

    @Test
    @Story("A plugin's failed drawing stops that plugin alone")
    @DisplayName("Where a plugin's drawing failed, the closing line names it and the type it threw, after every other clause")
    void namesAPluginWhoseDrawingFailed() {
        String line = lineFor(OptionalInt.of(0), List.of(new NextAction.RenderingNotDrawn("confluence", "IOException")));

        claim(
                "the line ends on the clause, after the count of documents not copied, before its full stop",
                () -> assertThat(line).endsWith(" groups left unwritten and 0 documents not copied"
                        + THE_CLAUSE.formatted("confluence", "IOException") + "."));
    }

    @Test
    @Story("A plugin's failed drawing stops that plugin alone")
    @DisplayName("Two plugins whose drawings failed are named in two clauses, in the order the drawing told them")
    void namesEachPluginWhoseDrawingFailedInTurn() {
        String line = lineFor(OptionalInt.of(0), List.of(
                new NextAction.RenderingNotDrawn("alpha", "IllegalStateException"),
                new NextAction.RenderingNotDrawn("zulu", "AccessDeniedException")));

        claim(
                "one clause each, alpha's before zulu's",
                () -> assertThat(line).endsWith(" documents not copied"
                        + THE_CLAUSE.formatted("alpha", "IllegalStateException")
                        + THE_CLAUSE.formatted("zulu", "AccessDeniedException") + "."));
    }

    @Test
    @Story("A plugin's failed drawing stops that plugin alone")
    @DisplayName("Where every plugin drew, or this invocation drew no tree, the closing line says nothing of a rendering")
    void saysNothingWhereNoPluginFailedOrNoTreeWasDrawn() {
        claim(
                "with every plugin drawn the line ends on its count of documents not copied, as it did before plugins",
                () -> assertThat(lineFor(OptionalInt.of(0), List.of()))
                        .endsWith(" groups left unwritten and 0 documents not copied."));
        claim(
                "and where this invocation drew no tree, nothing is said of a rendering a drawing it did not make left",
                () -> assertThat(lineFor(OptionalInt.empty(),
                                List.of(new NextAction.RenderingNotDrawn("confluence", "IOException"))))
                        .doesNotContain("rendering could not be drawn"));
    }

    /** The closing line of an invocation at the end of the path, handed these counts and these renderings not drawn. */
    private static String lineFor(OptionalInt documentsNotCopied, List<NextAction.RenderingNotDrawn> notDrawn) {
        return NextAction.line(
                everythingApproved(),
                SIXTY_ANSWERED,
                QUESTIONS_WRITTEN,
                AN_ARRANGEMENT,
                THE_GENERATION_MODEL,
                new NextAction.DeliverableSummary(
                        A_TREE, 0, documentsNotCopied, false, 0, 0, OptionalInt.empty(), 0, notDrawn),
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
