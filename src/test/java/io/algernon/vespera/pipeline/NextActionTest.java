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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The line an invocation ends on (ADR-098, #136): what is set, what is not, and the single next
 * action.
 *
 * <p>It exists because of a property of the aggregate rather than of any one message. With nothing
 * set, an invocation emits nine locally-correct "gated" lines naming three different missing values,
 * and the one thing the operator has to do next is buried among them. Each of those nine stays
 * exactly as it is; this is the line that tells the reader which of them to act on.
 *
 * <p>Asserted here as a pure function of the profile and the answers recorded against the seed set,
 * which is every input the next action depends on. Whether the line is reached, and reached last, is
 * a different claim and belongs to an invocation-level test.
 */
@Epic("Pipeline")
@Feature("The operator is told the next value")
@Link(name = "ADR-098", url = Adr.FOUR_INVOCATIONS_AND_THE_NEXT_VALUE, type = "adr")
@Issue("136")
class NextActionTest {

    /** The short name of the arrangement this invocation wrote, as the gate page prints it. */
    private static final String AN_ARRANGEMENT = "9f2c41ab77de";

    /** No arrangement was written this invocation, so there is nothing to approve. */
    private static final String NOTHING_ARRANGED = null;

    /** No answers have been written into the label file yet. */
    private static final int NOTHING_ANSWERED = 0;

    /** The scoring run wrote its sample of questions, which is the ordinary case. */
    private static final boolean QUESTIONS_WRITTEN = true;

    /** No label file exists: the step that writes one was gated, or the run never reached it. */
    private static final boolean NO_QUESTIONS_YET = false;

    /** The model resolved for this invocation, as {@code GenerationModel} would hand it over. */
    private static final String THE_GENERATION_MODEL = "a-generation-model:8b";

    /** ADR-088's stratified sample, answered in full. */
    private static final int SIXTY_ANSWERED = 60;

    /**
     * Whether the approval in the profile names the arrangement this invocation arrived at (ADR-154
     * §2). Every state here but one is before any approval, or has nothing arranged, so none matches.
     */
    private static final boolean NO_APPROVAL_MATCHES_THIS_INVOCATION = false;

    @Test
    @Story("Step zero is the seed folder, because it is the only key available on day one")
    @DisplayName("With nothing set, the next action is to name the seed folder")
    void withNothingSetTheNextActionIsTheSeedFolder() {
        String line = NextAction.line(nothingSet(), NOTHING_ANSWERED, NO_QUESTIONS_YET, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the line names seedFolder as the value to write, which is the one key no measurement"
                        + " could inform: the exemplars are the operator's own knowledge, and they are"
                        + " available on day one",
                () -> assertThat(line).contains("seedFolder"));
        claim(
                "and it says where to write it, because a key named with no file to put it in is not an"
                        + " action anyone can take",
                () -> assertThat(line).contains("profile.yaml"));
        claim(
                "it is one line, so it cannot itself become the nine lines it exists to summarise",
                () -> assertThat(line.lines()).hasSize(1));
    }

    @Test
    @Story("A threshold nobody can parse is not a threshold, and the line says so")
    @DisplayName("A mistyped floor beside an answered seed folder is reported, and nothing else is asked for")
    @Issue("203")
    @Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
    @Link(name = "ADR-298", url = Adr.THE_EMBEDDING_MODEL_SHIPS_WITH_A_DEFAULT, type = "adr")
    void aMistypedFloorIsReportedAndNothingElseIsAskedFor() {
        Profile mistypedBesideASeedFolder = ProfileFixture.profile()
                .seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor(A_MISTYPED_FLOOR, RECORDED_BY_THE_OPERATOR)
                .build();

        String line = NextAction.line(
                mistypedBesideASeedFolder, NOTHING_ANSWERED, NO_QUESTIONS_YET, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the mistyped value is quoted back beside a key that is answered: the line has the seed"
                        + " folder to credit, and the value it cannot use has already been ignored once",
                () -> assertThat(line).contains(A_MISTYPED_FLOOR));
        claim(
                "and the key is not credited as answered anywhere in the sentence -- a line that said it"
                        + " was set while the run ignored it is the one wording that would leave someone"
                        + " believing a floor is in force when none is",
                () -> assertThat(line).doesNotContain("boilerplateDocumentFrequencyFloor is set")
                        .doesNotContain("and boilerplateDocumentFrequencyFloor are set"));
        claim(
                "and it is not called unset either, which is the other wrong answer: the operator wrote"
                        + " something, and sending them to look at a key they would find already filled in"
                        + " is how a person concludes the tool is broken rather than their value",
                () -> assertThat(line).doesNotContain("boilerplateDocumentFrequencyFloor is not"));
        claim(
                "and no other value is asked for: the embedding model is the only other key the next"
                        + " invocation reads before scoring, and the application ships with one",
                () -> assertThat(line).doesNotContain("embeddingModel"));
    }

    /** A profile census has created and nobody has answered — every key present and unset. */
    private static Profile nothingSet() {
        return ProfileFixture.profile().build();
    }

    @Test
    @Story("With the threshold answered, the next act is approving what was arranged")
    @DisplayName("With the threshold set and an arrangement written, the next action is to approve it")
    @Issue("175")
    @Link(name = "ADR-107", url = Adr.THE_ARRANGEMENT_GATE_APPROVES_A_NAMED_RUN, type = "adr")
    void withTheThresholdSetTheNextActionIsToApproveTheArrangement() {
        String line = NextAction.line(everythingSet(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, AN_ARRANGEMENT, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim(
                "the approval is named now, because the arrangement it is about exists now -- which is"
                        + " exactly why it was not named at the end of any invocation before this one",
                () -> assertThat(line).contains("arrangementApproved"));
        claim(
                "and the line hands over the short name itself, ready to paste: the operator must not"
                        + " have to go and look up which arrangement they just read about",
                () -> assertThat(line).contains(AN_ARRANGEMENT));
        claim(
                "and it names the page that arrangement is described on, so the value is not copied"
                        + " out of a message into a file without anybody having looked at what it names",
                () -> assertThat(line).contains(ArrangementTasklet.ARRANGEMENT_FILE_NAME));
    }

    /** What an operator writes when their keyboard or their locale disagrees with Double.parseDouble. */
    private static final String A_MISTYPED_FLOOR = "0,62";

    /** The end of the path: every value the four invocations ask for, answered. */
    private static Profile everythingSet() {
        return ProfileFixture.profile()
                .seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor(THE_BOILERPLATE_FLOOR, RECORDED_BY_THE_OPERATOR)
                .embeddingModel(THE_EMBEDDING_MODEL, RECORDED_BY_THE_OPERATOR)
                .relevanceScoreFloor(THE_RELEVANCE_FLOOR, RECORDED_BY_THE_OPERATOR)
                .build();
    }

    /** The provenance an operator is asked to record beside every answer they give. */
    private static final String RECORDED_BY_THE_OPERATOR = "recorded by the operator";

    /** The folder of known-relevant documents, as the operator would write it into the profile. */
    private static final String THE_SEED_FOLDER = "/corpus/exemplars";

    /** Stage 3's floor, answered: any value at all opens stage 4's gate, and the number is not read here. */
    private static final String THE_BOILERPLATE_FLOOR = "0.4";

    /** Gate 3's model, answered: the name opens the gate, and nothing here embeds anything. */
    private static final String THE_EMBEDDING_MODEL = "a-third-comparing-model";

    /** A threshold on the run's own scale, answered, and parseable -- the contrast to the mistyped one. */
    private static final String THE_RELEVANCE_FLOOR = "0.62";
}
