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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The closing line names a {@code clusterPageByteCeiling} it ignored (ADR-303 §2.1): on the sentence that names the
 * tree, after every clause and before its last full stop, with what was written quoted back, in ADR-282 §3's words; the
 * key is never a value the next invocation wants, so the file beside the line wants nothing for it; and unset, set and
 * read, or with no tree drawn, the line says nothing of it.
 *
 * <p>Asserted as a pure function of what the line is handed, as {@code TheClosingLineSaysWhatAnEarlierDrawingLeftTest}
 * asserts its clause.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Issue("571")
@Link(name = "ADR-303", url = Adr.A_CLUSTERS_PAGE_PAST_THE_CEILING_IS_SPLIT_INTO_PAGES, type = "adr")
class TheClosingLineNamesAnIgnoredPageCeilingTest {

    /** The short name of the arrangement this invocation arrived at, and the one the profile approves. */
    private static final String AN_ARRANGEMENT = "9f2c41ab77de";

    private static final int SIXTY_ANSWERED = 60;

    private static final boolean QUESTIONS_WRITTEN = true;

    private static final String THE_GENERATION_MODEL = "a-generation-model:8b";

    private static final boolean APPROVAL_MATCHES_THIS_INVOCATION = true;

    private static final String RECORDED_BY_THE_OPERATOR = "recorded by the operator";

    /** A tree's folder, named for a generation run, as the line is handed it. */
    private static final Path A_TREE = Path.of("work", "deliverable", "a".repeat(64)).toAbsolutePath();

    /** How the line's sentence about the tree ends, before any clause about the ceiling. */
    private static final String THE_COUNTS = "with 0 groups left unwritten and 0 documents not copied";

    /** A tree that left nothing behind and copied everything. */
    private static final NextAction.DeliverableSummary NOTHING_LEFT =
            new NextAction.DeliverableSummary(A_TREE, 0, OptionalInt.of(0), false, 0, 0);

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"0", "1.5", "-2", "NaN", "big", "9223372036854775808"})
    @Story("A ceiling that is not a whole number of bytes splits nothing, and the line says so")
    @DisplayName("A page ceiling that is not a whole number of bytes of 1 or more is named on the line about the tree, with what was written, and nothing is wanted for it")
    void anIgnoredCeilingIsNamedOnTheTreesSentence(String written) {
        NextAction.Closing closing = closingFor(withTheCeiling(written), NOTHING_LEFT);

        claim(
                "the line ends, after its counts, with the key, what was written quoted back, and what came of it",
                () -> assertThat(closing.line()).endsWith(THE_COUNTS + "; clusterPageByteCeiling reads \"" + written
                        + "\", which is not a whole number of bytes of 1 or more, so it was ignored and no group's page"
                        + " was split."));
        claim(
                "it is still one line",
                () -> assertThat(closing.line().lines()).hasSize(1));
        claim(
                "and the file beside the line offers no next command and wants no value: the ceiling is never one the"
                        + " next invocation waits for",
                () -> assertThat(closing.next()).isEqualTo(new NextInvocationFile.Contents(
                        NextInvocationFile.Invocation.NONE, java.util.List.of(), false, java.util.Optional.empty())));
    }

    @Test
    @Story("A ceiling left unset, or read, is no stop")
    @DisplayName("An unset ceiling, a ceiling read as a number of bytes, and an ignored one with no tree drawn are not named on the line")
    void nothingIsSaidOfACeilingUnsetReadOrWithNoTree() {
        claim(
                "unset, the line about the tree ends with its counts",
                () -> assertThat(closingFor(everythingApproved(), NOTHING_LEFT).line()).endsWith(THE_COUNTS + "."));
        claim(
                "set and read, it ends the same",
                () -> assertThat(closingFor(withTheCeiling("300000"), NOTHING_LEFT).line()).endsWith(THE_COUNTS + "."));
        claim(
                "and with no tree drawn this invocation, an ignored ceiling is not named, the ceiling being read by a"
                        + " drawing alone",
                () -> assertThat(closingFor(withTheCeiling("big"), null).line()).doesNotContain("clusterPageByteCeiling"));
    }

    /** The closing of an invocation at the end of the path, handed {@code deliverable}, or none where it drew none. */
    private static NextAction.Closing closingFor(Profile profile, NextAction.DeliverableSummary deliverable) {
        return NextAction.closing(
                profile,
                SIXTY_ANSWERED,
                QUESTIONS_WRITTEN,
                AN_ARRANGEMENT,
                THE_GENERATION_MODEL,
                deliverable,
                APPROVAL_MATCHES_THIS_INVOCATION);
    }

    private static Profile withTheCeiling(String written) {
        return ProfileFixture.profileFrom(everythingApproved())
                .clusterPageByteCeiling(written, RECORDED_BY_THE_OPERATOR)
                .build();
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
