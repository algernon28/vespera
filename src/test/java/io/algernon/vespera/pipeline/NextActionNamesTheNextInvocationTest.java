package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.pipeline.NextInvocationFile.Contents;
import io.algernon.vespera.pipeline.NextInvocationFile.Invocation;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What {@link NextAction} gives beside each closing line for the file the control panel reads (ADR-294 §2): the
 * command the next act ends on, the profile keys the line asks for in the order it names them, whether the questions
 * are to be answered, and the arrangement to approve. One branch of {@code NextAction} words the line and gives the
 * contents, so each claim here compares the contents whole and holds them to the line they came with.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("565")
@Link(name = "ADR-294", url = Adr.THE_CONTROL_PANEL_LEARNS_WHAT_THE_NEXT_INVOCATION_WANTS_FROM_A_FILE, type = "adr")
@Link(name = "ADR-317", url = Adr.AN_APPROVAL_OF_AUTO_OPENS_THE_GATE_ON_EVERY_ARRANGEMENT, type = "adr")
class NextActionNamesTheNextInvocationTest {

    /** The short name of the arrangement this invocation arrived at, as the closing line quotes it. */
    private static final String THIS_INVOCATIONS_ARRANGEMENT = "9f2c41ab77de";

    /** An approval naming an arrangement this invocation did not arrive at. */
    private static final String ANOTHER_ARRANGEMENT = "0123456789ab";

    private static final String NOTHING_ARRANGED = null;

    private static final int NOTHING_ANSWERED = 0;

    /** ADR-088's sample of sixty questions, answered in full. */
    private static final int SIXTY_ANSWERED = 60;

    private static final boolean QUESTIONS_WRITTEN = true;

    private static final boolean NO_QUESTIONS_YET = false;

    private static final String THE_GENERATION_MODEL = "a-generation-model:8b";

    private static final boolean NO_APPROVAL_MATCHES_THIS_INVOCATION = false;

    private static final boolean APPROVAL_MATCHES_THIS_INVOCATION = true;

    private static final String RECORDED_BY_THE_OPERATOR = "recorded by the operator";

    private static final String THE_SEED_FOLDER = "/corpus/exemplars";

    private static final String THE_BOILERPLATE_FLOOR = "0.4";

    private static final String THE_EMBEDDING_MODEL = "embeddinggemma";

    private static final String THE_RELEVANCE_FLOOR = "0.62";

    /** A number written with a decimal comma, which no numeric key can read. */
    private static final String A_DECIMAL_COMMA = "0,62";

    /** A deliverable tree as the closing line names it once nothing is left to set. */
    private static final Path THE_TREE = Path.of("/work/deliverable/7c1e");

    @Test
    @Story("The control panel learns which values the next run wants")
    @DisplayName("With nothing set, the next command is a run wanting the seed folder and the boilerplate floor, in that order")
    void withNothingSetTheNextRunWantsTheTwoRunValuesInOrder() {
        NextAction.Closing closing = NextAction.closing(ProfileFixture.profile().build(), NOTHING_ANSWERED,
                NO_QUESTIONS_YET, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim("the next command is a run wanting seedFolder and boilerplateDocumentFrequencyFloor, in that order,"
                        + " with no questions to answer and no arrangement to approve",
                () -> assertThat(closing.next()).isEqualTo(run("seedFolder", "boilerplateDocumentFrequencyFloor")));
        namedByTheLineInItsOrder(closing);
    }

    @Test
    @Story("The control panel learns which values the next run wants")
    @DisplayName("With the seed folder named first, the next run wants the boilerplate floor")
    void withTheSeedFolderSetTheNextRunWantsTheFloor() {
        Profile profile = ProfileFixture.profile().seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR).build();

        NextAction.Closing closing = NextAction.closing(profile, NOTHING_ANSWERED, NO_QUESTIONS_YET, NOTHING_ARRANGED,
                THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim("the next command is a run wanting boilerplateDocumentFrequencyFloor, and not the seed folder already set",
                () -> assertThat(closing.next()).isEqualTo(run("boilerplateDocumentFrequencyFloor")));
        namedByTheLineInItsOrder(closing);
    }

    @Test
    @Story("The control panel learns which values the next run wants")
    @DisplayName("A run value that is not a number is wanted again")
    void anUnreadableRunValueIsWanted() {
        Profile profile = ProfileFixture.profile()
                .seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor(A_DECIMAL_COMMA, RECORDED_BY_THE_OPERATOR)
                .embeddingModel(THE_EMBEDDING_MODEL, RECORDED_BY_THE_OPERATOR)
                .build();

        NextAction.Closing closing = NextAction.closing(profile, NOTHING_ANSWERED, NO_QUESTIONS_YET, NOTHING_ARRANGED,
                THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim("the next command is a run wanting boilerplateDocumentFrequencyFloor alone",
                () -> assertThat(closing.next()).isEqualTo(run("boilerplateDocumentFrequencyFloor")));
        namedByTheLineInItsOrder(closing);
    }

    @Test
    @Story("The control panel learns that the questions are to be answered")
    @DisplayName("With the run values set and the questions written, the next command is vespera label, with answers wanted and no key")
    void withTheQuestionsWrittenTheNextCommandIsLabel() {
        NextAction.Closing closing = NextAction.closing(theRunValuesSet(), NOTHING_ANSWERED, QUESTIONS_WRITTEN,
                NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim("the next command is label, the questions are to be answered, and no key and no arrangement is wanted",
                () -> assertThat(closing.next()).isEqualTo(
                        new Contents(Invocation.LABEL, List.of(), true, Optional.empty())));
    }

    @Test
    @Story("The control panel learns which values the next run wants")
    @DisplayName("With the run values set and no questions written, the next run wants no key: the line above names what to fix")
    void withNoQuestionsWrittenTheNextRunWantsNoKey() {
        NextAction.Closing closing = NextAction.closing(theRunValuesSet(), NOTHING_ANSWERED, NO_QUESTIONS_YET,
                NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim("the next command is a run wanting no key, no answers and no arrangement",
                () -> assertThat(closing.next()).isEqualTo(run()));
    }

    @Test
    @Story("The control panel learns which values the next run wants")
    @DisplayName("With no embedding model named anywhere, the next run wants no key, the embedding model included: the line above names it")
    @Link(name = "ADR-298", url = Adr.THE_EMBEDDING_MODEL_SHIPS_WITH_A_DEFAULT, type = "adr")
    void withNoEmbeddingModelNamedTheNextRunWantsNoKey() {
        Profile noEmbeddingModel = ProfileFixture.profile()
                .seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor(THE_BOILERPLATE_FLOOR, RECORDED_BY_THE_OPERATOR)
                .build();

        NextAction.Closing closing = NextAction.closing(noEmbeddingModel, NOTHING_ANSWERED, NO_QUESTIONS_YET,
                NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim("scoring being stopped for want of a model, writing no questions, the next command is a run wanting no"
                        + " key, no answers and no arrangement, so embeddingModel is not among the keys wanted",
                () -> assertThat(closing.next()).isEqualTo(run()));
    }

    @Test
    @Story("The control panel learns which values the next run wants")
    @DisplayName("With the answers recorded, the next run wants the relevance threshold")
    void withTheAnswersRecordedTheNextRunWantsTheThreshold() {
        NextAction.Closing closing = NextAction.closing(theRunValuesSet(), SIXTY_ANSWERED, QUESTIONS_WRITTEN,
                NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim("the next command is a run wanting relevanceScoreFloor alone, and none of the keys that ship unset and"
                        + " are no stop",
                () -> assertThat(closing.next()).isEqualTo(run("relevanceScoreFloor")));
        namedByTheLineInItsOrder(closing);
    }

    @Test
    @Story("The control panel learns which values the next run wants")
    @DisplayName("A relevance threshold that is not a number is wanted again")
    void anUnreadableThresholdIsWanted() {
        Profile profile = ProfileFixture.profileFrom(theRunValuesSet())
                .relevanceScoreFloor(A_DECIMAL_COMMA, RECORDED_BY_THE_OPERATOR)
                .build();

        NextAction.Closing closing = NextAction.closing(profile, SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED,
                THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim("the next command is a run wanting relevanceScoreFloor alone",
                () -> assertThat(closing.next()).isEqualTo(run("relevanceScoreFloor")));
        namedByTheLineInItsOrder(closing);
    }

    /** Each key the closing line names only when it holds something nothing can be read from, with such a value. */
    static Stream<Arguments> keysNamedOnlyWhenUnreadable() {
        return Stream.of(
                unreadable("degenerateOutputConfidenceFloor",
                        p -> p.degenerateOutputConfidenceFloor(A_DECIMAL_COMMA, RECORDED_BY_THE_OPERATOR)),
                unreadable("degenerateOutputWordFloor",
                        p -> p.degenerateOutputWordFloor("two", RECORDED_BY_THE_OPERATOR)),
                unreadable("logTimestampShareFloor",
                        p -> p.logTimestampShareFloor(A_DECIMAL_COMMA, RECORDED_BY_THE_OPERATOR)),
                unreadable("outOfScopeNameEndings",
                        p -> p.outOfScopeNameEndings("P3A", RECORDED_BY_THE_OPERATOR)),
                unreadable("codeNameEndings",
                        p -> p.codeNameEndings("java", RECORDED_BY_THE_OPERATOR)),
                unreadable("configurationSettingShareFloor",
                        p -> p.configurationSettingShareFloor(A_DECIMAL_COMMA, RECORDED_BY_THE_OPERATOR)),
                unreadable("translationSimilarityFloor",
                        p -> p.translationSimilarityFloor(A_DECIMAL_COMMA, RECORDED_BY_THE_OPERATOR)),
                unreadable("preferredLanguages",
                        p -> p.preferredLanguages("italian, english", RECORDED_BY_THE_OPERATOR)),
                unreadable("extractionAttempt",
                        p -> p.extractionAttempt("two", RECORDED_BY_THE_OPERATOR)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("keysNamedOnlyWhenUnreadable")
    @Story("A value that ships unset is wanted only when it holds something nothing can be read from")
    @DisplayName("A key that is no stop and holds something unreadable is the one key the next run wants")
    void aKeyNamedOnlyWhenUnreadableIsWantedAlone(String key, UnaryOperator<ProfileFixture> writtenUnreadable) {
        Profile profile = writtenUnreadable.apply(ProfileFixture.profileFrom(theRunValuesSet())).build();

        NextAction.Closing closing = NextAction.closing(profile, SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED,
                THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim("the next command is a run wanting " + key + " alone",
                () -> assertThat(closing.next()).isEqualTo(run(key)));
        namedByTheLineInItsOrder(closing);
    }

    @Test
    @Story("A value that ships unset is wanted only when it holds something nothing can be read from")
    @DisplayName("An ending both lists hold makes the next run want both lists, out of scope first")
    void anEndingBothListsHoldWantsBothLists() {
        Profile profile = ProfileFixture.profileFrom(theRunValuesSet())
                .outOfScopeNameEndings(".P3A .INC", RECORDED_BY_THE_OPERATOR)
                .codeNameEndings(".java .inc", RECORDED_BY_THE_OPERATOR)
                .build();

        NextAction.Closing closing = NextAction.closing(profile, SIXTY_ANSWERED, QUESTIONS_WRITTEN, NOTHING_ARRANGED,
                THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim("the next command is a run wanting outOfScopeNameEndings and then codeNameEndings",
                () -> assertThat(closing.next()).isEqualTo(run("outOfScopeNameEndings", "codeNameEndings")));
        namedByTheLineInItsOrder(closing);
    }

    @Test
    @Story("The control panel learns which arrangement to approve")
    @DisplayName("With the threshold set and nothing arranged, the next run wants no key: the line above names what to fix")
    void withNothingArrangedTheNextRunWantsNoKey() {
        NextAction.Closing closing = NextAction.closing(everythingSet(), SIXTY_ANSWERED, QUESTIONS_WRITTEN,
                NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim("the next command is a run wanting no key and naming no arrangement",
                () -> assertThat(closing.next()).isEqualTo(run()));
    }

    @Test
    @Story("The control panel learns which arrangement to approve")
    @DisplayName("With an arrangement written and nothing approved, the next run wants the approval of that arrangement")
    void withAnArrangementWrittenTheNextRunWantsItsApproval() {
        NextAction.Closing closing = NextAction.closing(everythingSet(), SIXTY_ANSWERED, QUESTIONS_WRITTEN,
                THIS_INVOCATIONS_ARRANGEMENT, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim("the next command is a run wanting arrangementApproved, naming the arrangement this invocation arrived at",
                () -> assertThat(closing.next()).isEqualTo(new Contents(Invocation.RUN, List.of("arrangementApproved"),
                        false, Optional.of(THIS_INVOCATIONS_ARRANGEMENT))));
        namedByTheLineInItsOrder(closing);
    }

    @Test
    @Story("The control panel learns which arrangement to approve")
    @DisplayName("With an approval naming another arrangement, the arrangement named is this invocation's, not the approval's")
    void anApprovalNamingAnotherArrangementWantsThisInvocations() {
        Profile profile = ProfileFixture.profileFrom(everythingSet())
                .arrangementApproved(ANOTHER_ARRANGEMENT, RECORDED_BY_THE_OPERATOR)
                .build();

        NextAction.Closing closing = NextAction.closing(profile, SIXTY_ANSWERED, QUESTIONS_WRITTEN,
                THIS_INVOCATIONS_ARRANGEMENT, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION);

        claim("the next command is a run wanting arrangementApproved, naming the arrangement this invocation arrived at"
                        + " and not the one the profile holds",
                () -> assertThat(closing.next()).isEqualTo(new Contents(Invocation.RUN, List.of("arrangementApproved"),
                        false, Optional.of(THIS_INVOCATIONS_ARRANGEMENT))));
    }

    @Test
    @Story("The control panel learns that nothing is left to set")
    @DisplayName("With the arrangement approved and no deliverable named, nothing is left to set, and the two floors left unset add no step")
    void withTheArrangementApprovedNothingIsLeft() {
        NextAction.Closing closing = NextAction.closing(everythingApproved(), SIXTY_ANSWERED, QUESTIONS_WRITTEN,
                THIS_INVOCATIONS_ARRANGEMENT, THE_GENERATION_MODEL, APPROVAL_MATCHES_THIS_INVOCATION);

        claim("the next command is none, wanting nothing, though the word floor and the share-of-settings floor are"
                        + " unset",
                () -> assertThat(closing.next()).isEqualTo(new Contents(Invocation.NONE, List.of(), false,
                        Optional.empty())));
    }

    @Test
    @Story("The control panel learns that nothing is left to set")
    @DisplayName("With a deliverable drawn, nothing is left to set, and the tree the line names is not handed on")
    void withADeliverableDrawnNothingIsLeft() {
        NextAction.DeliverableSummary deliverable =
                new NextAction.DeliverableSummary(THE_TREE, 0, OptionalInt.of(0), false, 0, 0);

        NextAction.Closing closing = NextAction.closing(everythingApproved(), SIXTY_ANSWERED, QUESTIONS_WRITTEN,
                THIS_INVOCATIONS_ARRANGEMENT, THE_GENERATION_MODEL, deliverable, APPROVAL_MATCHES_THIS_INVOCATION);

        claim("the line names the deliverable's tree, so the state is the one with a deliverable",
                () -> assertThat(closing.line()).contains(THE_TREE.toString()));
        claim("and the next command is none, wanting nothing",
                () -> assertThat(closing.next()).isEqualTo(new Contents(Invocation.NONE, List.of(), false,
                        Optional.empty())));
    }

    @Test
    @Story("The control panel learns which values the next run wants")
    @DisplayName("Recording answers with the threshold set sends the next command to run, wanting no key, approval or none")
    void labelWithTheThresholdSetSendsTheNextCommandToRun() {
        NextAction.Closing unapproved = NextAction.closingAfterRecordingAnswers(
                everythingSet(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, THE_GENERATION_MODEL);
        NextAction.Closing approved = NextAction.closingAfterRecordingAnswers(
                ProfileFixture.profileFrom(everythingSet())
                        .arrangementApproved(ANOTHER_ARRANGEMENT, RECORDED_BY_THE_OPERATOR)
                        .build(),
                SIXTY_ANSWERED, QUESTIONS_WRITTEN, THE_GENERATION_MODEL);

        claim("with nothing approved, the next command is a run wanting no key and naming no arrangement",
                () -> assertThat(unapproved.next()).isEqualTo(run()));
        claim("and with an approval written, the same: the next run checks it, and recording answers checked none",
                () -> assertThat(approved.next()).isEqualTo(run()));
    }

    @Test
    @Story("The control panel learns which values the next run wants")
    @DisplayName("Recording answers with the approval set to auto sends the next command to run, wanting nothing, and says it writes unread")
    void labelWithTheApprovalSetToAutoSendsTheNextCommandToRun() {
        NextAction.Closing closing = NextAction.closingAfterRecordingAnswers(
                ProfileFixture.profileFrom(everythingSet())
                        .arrangementApproved(ArrangementGate.AUTOMATIC, RECORDED_BY_THE_OPERATOR)
                        .build(),
                SIXTY_ANSWERED, QUESTIONS_WRITTEN, THE_GENERATION_MODEL);

        claim("the next command is a run wanting no key and naming no arrangement",
                () -> assertThat(closing.next()).isEqualTo(run()));
        claim("and the line says the next run writes over whatever arrangement it arrives at, rather than that it"
                        + " checks the value",
                () -> assertThat(closing.line())
                        .isEqualTo("Every value the profile asks for is answered, and arrangementApproved holds"
                                + " \"auto\". Next: run vespera run, which arranges the documents and writes over"
                                + " whatever arrangement they are in then, unread."));
    }

    @Test
    @Story("The control panel learns which values the next run wants")
    @DisplayName("Recording answers with no threshold set sends the next command to run, wanting the threshold")
    void labelWithNoThresholdWantsTheThreshold() {
        NextAction.Closing closing = NextAction.closingAfterRecordingAnswers(
                theRunValuesSet(), SIXTY_ANSWERED, QUESTIONS_WRITTEN, THE_GENERATION_MODEL);

        claim("the next command is a run wanting relevanceScoreFloor alone",
                () -> assertThat(closing.next()).isEqualTo(run("relevanceScoreFloor")));
        claim("and the line is the one vespera run prints in the same state",
                () -> assertThat(closing.line()).isEqualTo(NextAction.line(theRunValuesSet(), SIXTY_ANSWERED,
                        QUESTIONS_WRITTEN, NOTHING_ARRANGED, THE_GENERATION_MODEL, NO_APPROVAL_MATCHES_THIS_INVOCATION)));
    }

    /** Every key the file wants is a word of the line, and the line names them in the same order. */
    private static void namedByTheLineInItsOrder(NextAction.Closing closing) {
        List<Integer> where = closing.next().wants().stream().map(closing.line()::indexOf).toList();
        claim("every key the file wants is named by the closing line, in the order the file lists them",
                () -> assertThat(where).doesNotContain(-1).isSorted());
    }

    private static Contents run(String... wanted) {
        return new Contents(Invocation.RUN, List.of(wanted), false, Optional.empty());
    }

    private static Arguments unreadable(String key, UnaryOperator<ProfileFixture> written) {
        return Arguments.of(key, written);
    }

    private static Profile theRunValuesSet() {
        return ProfileFixture.profile()
                .seedFolder(THE_SEED_FOLDER, RECORDED_BY_THE_OPERATOR)
                .boilerplateDocumentFrequencyFloor(THE_BOILERPLATE_FLOOR, RECORDED_BY_THE_OPERATOR)
                .embeddingModel(THE_EMBEDDING_MODEL, RECORDED_BY_THE_OPERATOR)
                .build();
    }

    private static Profile everythingSet() {
        return ProfileFixture.profileFrom(theRunValuesSet())
                .relevanceScoreFloor(THE_RELEVANCE_FLOOR, RECORDED_BY_THE_OPERATOR)
                .build();
    }

    private static Profile everythingApproved() {
        return ProfileFixture.profileFrom(everythingSet())
                .arrangementApproved(THIS_INVOCATIONS_ARRANGEMENT, RECORDED_BY_THE_OPERATOR)
                .build();
    }
}
