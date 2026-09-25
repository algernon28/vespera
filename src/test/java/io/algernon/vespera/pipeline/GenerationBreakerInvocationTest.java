package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.pipeline.GenerationScriptedBeans.ScriptedAnswer;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import io.algernon.vespera.synthesis.ClusterFaults;
import io.algernon.vespera.synthesis.RecordedClusterFault;
import io.algernon.vespera.synthesis.RecordedSynthesisDoc;
import io.algernon.vespera.synthesis.SynthesisDocs;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 6b when answer after answer is turned down (ADR-111, #184): a cluster whose answer nobody
 * believes costs that cluster alone, right up until the fifth in a row, at which point the step stops
 * and the invocation says so.
 *
 * <p><b>Why five in a row is read differently from five scattered.</b> Four hundred clusters do not
 * fail one after another by bad luck. They fail because the generation model, the word budget or the
 * shape imposed on the answer is wrong for this archive, and every call after the fifth buys another
 * copy of the same wrong answer. ADR-071 already took this reading one stage back, where a streak of
 * set-aside documents reads as the instrument being broken rather than the documents being bad.
 *
 * <p><b>The invocation ends unsuccessfully, and the reasons it recorded are still there.</b> Those
 * two together are the whole of what this class claims that {@link GenerationFaultInvocationTest}
 * does not: an invocation that stopped and left nothing behind saying why would send the archive's
 * owner back to the model with no account of what it answered.
 *
 * <p><b>Only an answer that came back counts, in either direction</b> (ADR-111's consequences, as
 * #184 settled them). A cluster an earlier invocation already wrote (ADR-115, ADR-116) and a cluster no
 * call could be made for (ADR-121) are walked past without a call, so neither adds to the streak and
 * neither clears it — and the streak belongs to the invocation that made the calls, so an invocation
 * that stopped on five leaves nothing behind that could stop the next one before it has asked
 * anything.
 *
 * <p><b>The clusters are written straight into the arrangement.</b> Every document this fixture
 * converts comes back alike, so no corpus can be written that clusters into nine clusters of its own.
 * The arranged clusters are replaced with one cluster per document instead, in a stated order, which is
 * what lets a test say which answer came fifth.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em>, and that is settled rather
 * than sloppy</b> (ADR-122). Everything a reader of the report sees -- the feature, the stories, the
 * display names, every claim -- renders the term as <em>group</em>, because the everyday sense of
 * "cluster" is a set of interchangeable things, which is what {@code CONTEXT.md} says a cluster is
 * not, and that reader cannot open {@code CONTEXT.md} to find out. Everything this project names for
 * itself -- the constants, the fixture methods, these comments -- says cluster. Do not reconcile the
 * two by changing either side.
 */
@CascadeSliceTest
@Import({ClusterFaults.class, SeedScriptedExtractionBeans.class})
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("184")
@Link(name = "ADR-111", url = Adr.A_CLUSTER_FAULT_IS_A_ROW_IN_SYNTHESIS, type = "adr")
class GenerationBreakerInvocationTest {

    /** Where every line this invocation wrote for the archive's owner is read from. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /** A floor of 1.0 opens stage 4's gate the way every other invocation test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model this fixture names, so gate 3 and everything behind it open. */
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    /**
     * How much every call in this class may read, in tokens, written into the profile rather than
     * left at whatever the shipped default is: one answer here is scripted against this number, and a
     * window nobody stated would be whichever the last test in the class happened to leave behind.
     */
    private static final String THE_READING_WINDOW = "4096";

    /** The same number as an integer, which is the ceiling the first of the four checks reads against. */
    private static final int THE_CEILING = 4096;

    /** A count past the ceiling: more question read than the window holds, so it was cut down to fit. */
    private static final int A_COUNT_PAST_THE_CEILING = THE_CEILING + 904;

    /** The whole of what an answer is allowed, which is what running out of room looks like. */
    private static final int THE_WHOLE_ANSWER_ALLOWANCE = 1024;

    /** An answer nothing can read back into a heading and its writing: it stops partway through. */
    private static final String AN_ANSWER_NOTHING_CAN_READ = "{\"title\":\"Site Safety Audits\",\"prose\":";

    /** The title scripted alongside prose whose numbers are under test. */
    private static final String A_TITLE = "What The Stubbed Document Says";

    /** A call here carries one document, under the number 1, so 7 is a number it never minted. */
    private static final int A_NUMBER_NO_DOCUMENT_WAS_SENT_UNDER = 7;

    private static final String PROSE_POINTING_AT_NOTHING =
            "The audit [" + A_NUMBER_NO_DOCUMENT_WAS_SENT_UNDER + "] states every finding.";

    /**
     * The names this fixture gives the clusters it writes into the arrangement, in the order they are
     * written over. Each is scripted for by name, so no name may read as part of another.
     */
    private static final List<String> CLUSTER_NAMES = List.of(
            "Group one",
            "Group two",
            "Group three",
            "Group four",
            "Group five",
            "Group six",
            "Group seven",
            "Group eight",
            "Group nine");

    /** How many turned-down answers in a row stop the step (ADR-111, matching ADR-071's count). */
    private static final int THE_STREAK_THAT_STOPS_THE_STEP = 5;

    /**
     * How many clusters the stopping tests arrange: one more than the streak, so there is a cluster left
     * for the step to have walked past. Five alone would be met by a step that stopped because it had
     * run out of clusters rather than because five answers in a row were turned down.
     */
    private static final int ONE_MORE_CLUSTER_THAN_THE_STREAK = THE_STREAK_THAT_STOPS_THE_STEP + 1;

    /** How many of the four ways an answer can be turned down the stopping tests' five cover. */
    private static final int ALL_FOUR_WAYS_AN_ANSWER_IS_TURNED_DOWN = 4;

    /**
     * How many clusters the tests that put something in front of the streak arrange: the five, one cluster
     * ahead of them, and one after them the step is never expected to reach.
     */
    private static final int A_CLUSTER_EITHER_SIDE_OF_THE_STREAK = THE_STREAK_THAT_STOPS_THE_STEP + 2;

    /**
     * What one believed answer and then the streak cost: a call each. One more than the streak, because
     * the cluster ahead of it was asked about too, and one short of the arrangement, because the cluster
     * after the fifth never was.
     */
    private static final int A_CALL_FOR_THE_BELIEVED_ANSWER_AND_ONE_EACH_FOR_THE_STREAK =
            THE_STREAK_THAT_STOPS_THE_STEP + 1;

    /**
     * Where the cluster nothing could be sent for sits among the seven, counting from zero as the order
     * of the arrangement itself does: the third cluster, which puts it inside the streak rather than
     * before or after it.
     */
    private static final int THE_CLUSTER_NOTHING_COULD_BE_SENT_FOR_IS_THIRD = 2;

    /** Given to the fixture by a test whose clusters all hold a document of their own. */
    private static final int EVERY_CLUSTER_HOLDS_A_DOCUMENT = -1;

    /** What the streak costs when a cluster nothing could be sent for sits in the middle of it. */
    private static final int A_CALL_FOR_EACH_ANSWER_IN_THE_STREAK = THE_STREAK_THAT_STOPS_THE_STEP;

    /**
     * The six of the seven clusters whose answers are turned down when the third one's is believed,
     * counting from zero as the order of the arrangement does.
     *
     * <p>The third is the one left out because it has to sit <em>inside</em> the streak: two answers
     * turned down, then the cluster, then three more, is what says whether the cluster in the middle
     * cleared the count or was walked past without touching it.
     */
    private static final List<Integer> EVERY_CLUSTER_BUT_THE_THIRD = List.of(0, 1, 3, 4, 5, 6);

    /**
     * How many clusters the carrying-on test arranges: four turned down, one believed, four turned
     * down. Four either side is the most a run can have without the streak reaching five, so this is
     * the arrangement that fails if a believed answer does not drop the count to nothing.
     */
    private static final int FOUR_EITHER_SIDE_OF_A_BELIEVED_ANSWER = 9;

    /** Where the believed answer sits among those nine, counting from one. */
    private static final int THE_BELIEVED_ANSWER_IS_THE_FIFTH = 5;

    /** What eight turned-down answers leave behind: a reason each. */
    private static final int EIGHT_REASONS_KEPT = 8;

    /** What the one believed answer leaves behind. */
    private static final int ONE_PIECE_OF_WRITING = 1;

    /** Nothing is written over a cluster whose answer was turned down. */
    private static final int NO_WRITING_AT_ALL = 0;

    /** One line, and no second one: what "this and nothing else was said" comes to as a count. */
    private static final int A_SINGLE_LINE = 1;

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SynthesisDocs synthesisDocs;

    @Autowired
    private ClusterFaults clusterFaults;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    /**
     * Drops what was scripted and what was counted <em>before</em> each test as well as after it.
     *
     * <p>The count of calls made lives on {@link GenerationScriptedBeans}, which is static because the
     * context builds the bean and no test can reach the instance. Eighteen classes in this package
     * name that fixture in their own contexts and every one of them shares that count, while only the
     * three generation classes drop it. So a class that runs immediately before this one can leave a
     * count standing, and the first test here then reads its own calls plus that residue.
     *
     * <p>Which class runs first is not fixed, so the residue is a lottery held per machine: this class
     * passed on NTFS and in a Linux container, and failed on the build's Linux runner, where two calls
     * left behind turned five into seven.
     */
    @BeforeEach
    void captureOperatorLines() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLinesAndForgetWhatWasScripted() {
        applicationLogger.detachAppender(logged);
        logged.stop();
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @Test
    @Story("Answers nobody believes, one after another, stop the step")
    @DisplayName("Five answers turned down in a row stop the step and the invocation reports failure")
    void stopsTheStepWhenFiveAnswersInARowAreTurnedDown(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedArrangementOf(ONE_MORE_CLUSTER_THAN_THE_STREAK, root, seeds);
        fiveAnswersTurnedDownForFourDifferentReasons();

        cli.run("run", root.toString());

        claim(
                "the invocation reports failure rather than success: " + THE_STREAK_THAT_STOPS_THE_STEP
                        + " answers turned down one after another are not " + THE_STREAK_THAT_STOPS_THE_STEP
                        + " unlucky groups -- they are the generation model, the word budget or the shape"
                        + " imposed on the answer being wrong for this archive -- and an invocation that"
                        + " reported success here would hand over a deliverable with nothing written in it,"
                        + " under an exit code nothing can tell from a finished run",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "exactly " + THE_STREAK_THAT_STOPS_THE_STEP + " calls were made, though "
                        + ONE_MORE_CLUSTER_THAN_THE_STREAK + " groups were arranged: the group after the"
                        + " fifth was never asked about, which is what makes this cost five calls rather"
                        + " than one for every group in the archive",
                () -> assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(THE_STREAK_THAT_STOPS_THE_STEP));
        claim(
                "all " + THE_STREAK_THAT_STOPS_THE_STEP + " reasons are still kept afterwards, the fifth"
                        + " one included: stopping is what the fifth answer earned, and dropping the record"
                        + " of the five on the way out would send the archive's owner back to the model with"
                        + " no account of what it actually answered",
                () -> assertThat(reasonsKept(root)).hasSize(THE_STREAK_THAT_STOPS_THE_STEP));
        claim(
                "the reasons cover all " + ALL_FOUR_WAYS_AN_ANSWER_IS_TURNED_DOWN + " ways an answer can"
                        + " be turned down, so what stopped the step counted a mix of them rather than five"
                        + " of one: a model alternating between two ways of being wrong is exactly as wrong"
                        + " as one repeating a single way, and a count kept per way would let it slip past"
                        + " both",
                () -> assertThat(reasonsKept(root).stream()
                                .map(kept -> kept.fault().kind())
                                .distinct()
                                .toList())
                        .hasSize(ALL_FOUR_WAYS_AN_ANSWER_IS_TURNED_DOWN));
        claim(
                "and nothing at all was written over the groups",
                () -> assertThat(writingKept(root)).hasSize(NO_WRITING_AT_ALL));
    }

    @Test
    @Story("Answers nobody believes, one after another, stop the step")
    @DisplayName("The line written when it stops says how many answers were turned down, and what for")
    void saysWhatHappenedWhenItStops(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedArrangementOf(ONE_MORE_CLUSTER_THAN_THE_STREAK, root, seeds);
        fiveAnswersTurnedDownForFourDifferentReasons();

        cli.run("run", root.toString());

        claim(
                "exactly one line is written at the level this run keeps for something having stopped,"
                        + " and every group left unwritten was reported a line of its own one level below"
                        + " it: the archive's owner scanning the output for what went wrong has to land on"
                        + " the line saying the work stopped, not on the fifth of five lines that each"
                        + " read as one group's bad luck",
                () -> assertThat(linesSayingSomethingStopped()).hasSize(A_SINGLE_LINE));
        claim(
                "and that line says " + THE_STREAK_THAT_STOPS_THE_STEP + " answers in a row were turned"
                        + " down, with the count standing next to those words rather than merely somewhere"
                        + " in the line: the reasons carried along with it hold numbers of their own --"
                        + " how much question was read, how long the answer ran -- so a line that only had"
                        + " the digit in it somewhere would say nothing about how many answers it took",
                () -> assertThat(linesSayingSomethingStopped())
                        .singleElement()
                        .satisfies(line -> assertThat(line)
                                .contains(THE_STREAK_THAT_STOPS_THE_STEP + " answers in a row were turned down")));
        claim(
                "and that same line names every one of the " + ALL_FOUR_WAYS_AN_ANSWER_IS_TURNED_DOWN
                        + " ways those answers were turned down rather than the last one only: widening"
                        + " the reading window, raising the answer allowance and naming a different"
                        + " generating model are three different things to do, and which of them to do is"
                        + " what the reasons say -- a line naming only how many leaves that unsaid and"
                        + " sends the archive's owner to the database to find it",
                () -> assertThat(linesSayingSomethingStopped())
                        .singleElement()
                        .satisfies(line -> assertThat(line)
                                .contains(ClusterFaultKind.PROMPT_EVALUATION_CEILING.name())
                                .contains(ClusterFaultKind.ANSWER_RAN_OUT_OF_ROOM.name())
                                .contains(ClusterFaultKind.SCHEMA_VIOLATION.name())
                                .contains(ClusterFaultKind.CITATION_NOT_IN_RANGE.name())));
    }

    @Test
    @Story("An answer that is believed clears what came before it")
    @DisplayName("Four answers turned down either side of a believed one do not stop the step")
    void carriesOnWhenAnAnswerBetweenThemIsBelieved(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedArrangementOf(FOUR_EITHER_SIDE_OF_A_BELIEVED_ANSWER, root, seeds);
        everyAnswerTurnedDownExceptTheFifth();

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: " + EIGHT_REASONS_KEPT + " answers were turned down, and"
                        + " none of that is a run of " + THE_STREAK_THAT_STOPS_THE_STEP + " because an"
                        + " answer nobody doubted came back in the middle of it -- a model that answers"
                        + " some groups well is one working on an awkward archive, not a broken one",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "every one of the " + FOUR_EITHER_SIDE_OF_A_BELIEVED_ANSWER + " groups was asked about,"
                        + " the four after the believed answer included",
                () -> assertThat(GenerationScriptedBeans.callsMade())
                        .isEqualTo(FOUR_EITHER_SIDE_OF_A_BELIEVED_ANSWER));
        claim(
                EIGHT_REASONS_KEPT + " reasons are kept, one for each answer turned down",
                () -> assertThat(reasonsKept(root)).hasSize(EIGHT_REASONS_KEPT));
        claim(
                "and the one believed answer is written over its group, " + ONE_PIECE_OF_WRITING
                        + " piece of writing",
                () -> assertThat(writingKept(root)).hasSize(ONE_PIECE_OF_WRITING));
    }

    @Test
    @Story("Answers nobody believes, one after another, stop the step")
    @DisplayName("Writing an earlier group earned is still there after the step stops")
    void keepsTheWritingAnEarlierClusterEarnedWhenItStops(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedArrangementOf(A_CLUSTER_EITHER_SIDE_OF_THE_STREAK, root, seeds);
        answersTurnedDownFor(List.of(1, 2, 3, 4, 5));

        cli.run("run", root.toString());

        claim(
                "the invocation reports failure, though the first answer of the "
                        + A_CLUSTER_EITHER_SIDE_OF_THE_STREAK + " was believed: " + THE_STREAK_THAT_STOPS_THE_STEP
                        + " turned down one after another are counted from wherever the last believed answer"
                        + " left off, not from the start of the archive",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "the one piece of writing the first group earned is still kept after the step stopped: it"
                        + " is the most expensive thing this system buys, it was paid for before anything"
                        + " went wrong, and a stop that took it back down with it would have the archive's"
                        + " owner buy it a second time on the next run",
                () -> assertThat(writingKept(root)).hasSize(ONE_PIECE_OF_WRITING));
        claim(
                "all " + THE_STREAK_THAT_STOPS_THE_STEP + " reasons are kept beside it, one for each answer"
                        + " turned down",
                () -> assertThat(reasonsKept(root)).hasSize(THE_STREAK_THAT_STOPS_THE_STEP));
        claim(
                A_CALL_FOR_THE_BELIEVED_ANSWER_AND_ONE_EACH_FOR_THE_STREAK + " calls were made out of "
                        + A_CLUSTER_EITHER_SIDE_OF_THE_STREAK + " groups arranged: one for the group whose"
                        + " answer was believed and one for each of the " + THE_STREAK_THAT_STOPS_THE_STEP
                        + " turned down, and the group after them was never asked about",
                () -> assertThat(GenerationScriptedBeans.callsMade())
                        .isEqualTo(A_CALL_FOR_THE_BELIEVED_ANSWER_AND_ONE_EACH_FOR_THE_STREAK));
    }

    @Test
    @Story("Answers nobody believes, one after another, stop the step")
    @DisplayName("A group nothing could be sent for neither adds to the count nor clears it")
    @Link(name = "ADR-121", url = Adr.A_WINDOW_WITH_NO_ROOM_IS_REFUSED, type = "adr")
    void doesNotClearTheCountForAClusterNothingCouldBeSentFor(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedArrangementOf(
                A_CLUSTER_EITHER_SIDE_OF_THE_STREAK, THE_CLUSTER_NOTHING_COULD_BE_SENT_FOR_IS_THIRD, root, seeds);
        answersTurnedDownFor(List.of(0, 1, 3, 4, 5));

        cli.run("run", root.toString());

        claim(
                "the invocation reports failure, so the third group -- the one nothing could be sent for --"
                        + " did not clear the count on its way past: nothing was asked there and nothing came"
                        + " back, so it is no evidence that the generating model, the word budget and the"
                        + " shape imposed on the answer are right, and treating it as a fresh start would"
                        + " let an archive with a scattering of unsendable groups in it never stop at all",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "exactly " + A_CALL_FOR_EACH_ANSWER_IN_THE_STREAK + " calls were made: one for each answer"
                        + " turned down and none at all for the group nothing could be sent for -- which is"
                        + " also what says that group did not count towards the stop, since counting it"
                        + " would have stopped the step one call earlier",
                () -> assertThat(GenerationScriptedBeans.callsMade())
                        .isEqualTo(A_CALL_FOR_EACH_ANSWER_IN_THE_STREAK));
        claim(
                THE_STREAK_THAT_STOPS_THE_STEP + " reasons are kept, and none of them is for the group"
                        + " nothing could be sent for: the ways an answer can be turned down are things a"
                        + " returned answer did, and no answer was returned there",
                () -> assertThat(reasonsKept(root)).hasSize(THE_STREAK_THAT_STOPS_THE_STEP));
        claim(
                "and nothing at all was written over the groups",
                () -> assertThat(writingKept(root)).hasSize(NO_WRITING_AT_ALL));
    }

    @Test
    @Story("Answers nobody believes, one after another, stop the step")
    @DisplayName("A group an earlier invocation already wrote neither adds to the count nor clears it")
    @Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
    void doesNotClearTheCountForAClusterAnEarlierInvocationAlreadyWrote(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedArrangementOf(A_CLUSTER_EITHER_SIDE_OF_THE_STREAK, root, seeds);
        answersTurnedDownFor(EVERY_CLUSTER_BUT_THE_THIRD);

        cli.run("run", root.toString());
        GenerationScriptedBeans.forgetScriptedAnswers();
        answersTurnedDownFor(EVERY_CLUSTER_BUT_THE_THIRD);

        cli.run("run", root.toString());

        claim(
                "the second invocation reports failure: the third group was walked past without a call,"
                        + " because the first invocation had already written it, and walking past it did not"
                        + " clear the count -- nothing was asked there, so it says nothing about whether the"
                        + " generating model, the word budget and the shape imposed on the answer are right"
                        + " now",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "it made exactly " + A_CALL_FOR_EACH_ANSWER_IN_THE_STREAK + " calls of its own: one for"
                        + " each answer turned down, none for the group already written, and none for the"
                        + " group after the fifth -- where treating the one it walked past as a fresh start"
                        + " would have carried it on to a sixth call",
                () -> assertThat(GenerationScriptedBeans.callsMade())
                        .isEqualTo(A_CALL_FOR_EACH_ANSWER_IN_THE_STREAK));
        claim(
                "and the " + ONE_PIECE_OF_WRITING + " piece of writing the first invocation earned is"
                        + " still the only one kept: it was not asked for again, and stopping did not take"
                        + " it away",
                () -> assertThat(writingKept(root)).hasSize(ONE_PIECE_OF_WRITING));
    }

    @Test
    @Story("Running again after it stopped starts the count over")
    @DisplayName("After it stops, nothing is recorded as done and the next run asks about every group")
    @Link(name = "ADR-116", url = Adr.A_RUNS_COMPLETION_IS_RECORDED_PER_STEP, type = "adr")
    void asksAboutEveryClusterAgainOnTheInvocationAfterItStopped(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedArrangementOf(A_CLUSTER_EITHER_SIDE_OF_THE_STREAK, root, seeds);
        answersTurnedDownFor(List.of(0, 1, 2, 3, 4));

        cli.run("run", root.toString());
        boolean recordedAsDoneWhenItStopped = theWorkIsRecordedAsDone(root);
        GenerationScriptedBeans.forgetScriptedAnswers();

        cli.run("run", root.toString());

        claim(
                "the work was not recorded as done when the step stopped: the record of it being done is"
                        + " exactly what a later invocation reads to decide whether to walk past this step,"
                        + " so writing it here would make the two groups nobody ever asked about permanently"
                        + " unwritten while every run after it reported success",
                () -> assertThat(recordedAsDoneWhenItStopped).isFalse());
        claim(
                "the next invocation asks about all " + A_CLUSTER_EITHER_SIDE_OF_THE_STREAK + " groups,"
                        + " including the two the stopped run never reached: stopping is what the archive's"
                        + " owner acts on, and a run that then walked past the groups it had never got to"
                        + " would leave them unwritten with nothing left saying they were missing",
                () -> assertThat(GenerationScriptedBeans.callsMade())
                        .isEqualTo(A_CLUSTER_EITHER_SIDE_OF_THE_STREAK));
        claim(
                "every one of those " + A_CLUSTER_EITHER_SIDE_OF_THE_STREAK + " groups is written over this"
                        + " time, the five that were turned down before included: the same question is put"
                        + " again with nothing said about what came back last time, and what comes back now"
                        + " is what stands",
                () -> assertThat(writingKept(root)).hasSize(A_CLUSTER_EITHER_SIDE_OF_THE_STREAK));
        claim(
                "and that invocation reports success, because nothing in it was turned down",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    /**
     * Scripts the first five clusters to be turned down, one for each of the four ways an answer can be,
     * and one of them twice. The sixth cluster is left unscripted, so an answer nobody doubted is what it
     * would get — which is what makes the call count say the step stopped before reaching it.
     */
    private void fiveAnswersTurnedDownForFourDifferentReasons() {
        answersTurnedDownFor(List.of(0, 1, 2, 3, 4));
    }

    /**
     * Scripts each named cluster to be turned down, walking the four ways an answer can be and starting
     * over when it runs out of them — so any five of them cover all four, which is what makes a streak
     * scripted here a streak of a mix rather than of one kind repeated.
     *
     * @param clusters where each turned-down answer sits in {@link #CLUSTER_NAMES}
     */
    private void answersTurnedDownFor(List<Integer> clusters) {
        for (int position = 0; position < clusters.size(); position++) {
            GenerationScriptedBeans.answerFor(
                    CLUSTER_NAMES.get(clusters.get(position)), oneOfTheFourWaysAnAnswerIsTurnedDown(position));
        }
    }

    /** The way an answer is turned down at {@code position} of the walk through the four of them. */
    private static ScriptedAnswer oneOfTheFourWaysAnAnswerIsTurnedDown(int position) {
        return switch (position % ALL_FOUR_WAYS_AN_ANSWER_IS_TURNED_DOWN) {
            case 0 -> anOrdinaryAnswer().havingRead(A_COUNT_PAST_THE_CEILING);
            case 1 -> anOrdinaryAnswer().stoppedForRoomAfter(THE_WHOLE_ANSWER_ALLOWANCE);
            case 2 -> ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ);
            default -> ScriptedAnswer.saying(A_TITLE, PROSE_POINTING_AT_NOTHING);
        };
    }

    /**
     * Scripts every cluster but the fifth to be turned down, so four land, one is believed, and four
     * more land after it.
     */
    private void everyAnswerTurnedDownExceptTheFifth() {
        for (int cluster = 1; cluster <= FOUR_EITHER_SIDE_OF_A_BELIEVED_ANSWER; cluster++) {
            if (cluster == THE_BELIEVED_ANSWER_IS_THE_FIFTH) {
                continue;
            }
            GenerationScriptedBeans.answerFor(
                    CLUSTER_NAMES.get(cluster - 1), ScriptedAnswer.arrivingAs(AN_ANSWER_NOTHING_CAN_READ));
        }
    }

    /** An answer that passes every check, which a scripted one is exactly one property away from. */
    private static ScriptedAnswer anOrdinaryAnswer() {
        return ScriptedAnswer.saying(
                GenerationScriptedBeans.GENERATED_TITLE, GenerationScriptedBeans.GENERATED_PROSE);
    }

    /**
     * A corpus of {@code clusters} documents, walked once, arranged into {@code clusters} clusters of one
     * document each, with that arrangement approved.
     */
    private void anApprovedArrangementOf(int clusters, Path root, Path seeds) throws IOException {
        anApprovedArrangementOf(clusters, EVERY_CLUSTER_HOLDS_A_DOCUMENT, root, seeds);
    }

    /**
     * The same, with the cluster at {@code theClusterHoldingNothing} holding a document nothing of can be
     * sent — which costs no call and returns no answer (ADR-121).
     *
     * @param theClusterHoldingNothing where that cluster sits in the order, or {@link
     *     #EVERY_CLUSTER_HOLDS_A_DOCUMENT} when there is no such cluster
     */
    private void anApprovedArrangementOf(int clusters, int theClusterHoldingNothing, Path root, Path seeds)
            throws IOException {
        for (int document = 1; document <= clusters; document++) {
            Files.writeString(root.resolve("corpus-" + document + ".txt"), "corpus document " + document);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every call here reads in the same window")
                .build());
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));
        oneClusterPerDocument(theApprovedArrangement(root), clusters, theClusterHoldingNothing, root);
    }

    /** Writes the approval, leaving every other key as the fixture left it. */
    private void approve(String approval) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(approval, "read by this test")
                .build());
    }

    /**
     * Replaces the arranged clusters with {@code clusters} clusters of one document each, named in the order
     * they will be written over.
     *
     * <p>Written straight into the tables for the reason this package's other fault test writes its
     * second cluster in: every document this fixture converts comes back carrying the same text, so
     * nothing that can be put in the corpus will arrange itself into nine clusters. Identity stays the
     * cluster ordinal and order stays the cluster order, which is what the step reads them in.
     *
     * <p><b>It states the whole arrangement rather than adjusting what it finds</b>, and that is not
     * tidiness. One working directory serves this whole class, and a run's name is derived from what it
     * reads (ADR-048), so a method whose corpus holds the same documents as an earlier one works under
     * the <em>same</em> run — and that run's membership then carries both walks' rows, keyed as they
     * are by occurrence and run. Every document under the run is therefore first moved to an ordinal
     * no cluster is arranged at, and only then are this walk's documents placed one per cluster. Left where
     * they were, one of the other walk's documents lands in the cluster this method meant to leave empty,
     * which is a cluster that quietly becomes sendable — and whether it does depends on the order a
     * filesystem hands its entries back, so it passed on NTFS and failed on Linux.
     *
     * <p><b>Every placed document is given the cluster rows' winning seed</b>, so membership and
     * arrangement agree on the key the step reads them by, and the order is the cluster order alone.
     *
     * <p>The cluster at {@code theClusterHoldingNothing} is given its document like every other, so the
     * arrangement stays total and its page can be drawn from it (ADR-112, ADR-154 §2). What leaves it
     * with nothing to send is that its document is then rewritten in place, its length and timestamps
     * unchanged, so the walk still sees the same archive and the approval still stands, while the step
     * finds nothing cached under the text it now reads ({@link UnseenEditFixture}). It meets the
     * cluster, finds nothing to send, and makes no call.
     */
    private void oneClusterPerDocument(RunId arrangement, int clusters, int theClusterHoldingNothing, Path root)
            throws IOException {
        String scoring = jdbcTemplate.queryForObject(
                "SELECT upstream_run_id FROM run_upstream WHERE run_id = ?", String.class, arrangement.value());
        // Scoped to this method's own walk, for the reason the javadoc above gives: the run may carry
        // another walk's membership as well, and those documents are not this method's to arrange.
        List<Long> documents = jdbcTemplate.queryForList(
                "SELECT dc.occurrence_id FROM document_cluster dc"
                        + " JOIN file_occurrence fo ON fo.id = dc.occurrence_id"
                        + " JOIN walk w ON w.id = fo.walk_id"
                        + " WHERE dc.run_id = ? AND w.root = ? ORDER BY dc.occurrence_id",
                Long.class,
                scoring,
                Walk.canonicalRoot(root).toString());
        if (documents.size() < clusters) {
            throw new IllegalStateException("this fixture needs " + clusters + " documents in the"
                    + " arrangement to make " + clusters + " groups, and this walk produced " + documents.size());
        }
        Long winningSeed = jdbcTemplate.queryForObject(
                "SELECT winning_seed_occurrence_id FROM document_cluster WHERE run_id = ? AND occurrence_id = ?",
                Long.class,
                scoring,
                documents.getFirst());
        // Out of the arrangement entirely: no cluster is arranged at this ordinal, so anything left here
        // is unreachable rather than quietly part of a cluster.
        //
        // This changes no outcome today and no test notices its removal -- another walk's documents are
        // already invisible to the step, because the two lines above key this arrangement to a winning
        // seed only this walk's documents carry. It is kept because that invisibility is a property of
        // the seed folders happening to differ per test, where this is the fixture saying outright that
        // it arranges these documents and no others -- which is the thing that was not true before.
        jdbcTemplate.update(
                "UPDATE document_cluster SET cluster_ordinal = ? WHERE run_id = ?", clusters, scoring);
        for (int cluster = 0; cluster < clusters; cluster++) {
            jdbcTemplate.update(
                    "UPDATE document_cluster SET cluster_ordinal = ?, winning_seed_occurrence_id = ?"
                            + " WHERE run_id = ? AND occurrence_id = ?",
                    cluster,
                    winningSeed,
                    scoring,
                    documents.get(cluster));
        }
        jdbcTemplate.update("DELETE FROM cluster WHERE run_id = ?", arrangement.value());
        for (int cluster = 0; cluster < clusters; cluster++) {
            jdbcTemplate.update(
                    "INSERT INTO cluster (run_id, winning_seed_occurrence_id, cluster_ordinal, label,"
                            + " document_count, partition_order, cluster_order) VALUES (?, ?, ?, ?, ?, 0, ?)",
                    arrangement.value(),
                    winningSeed,
                    cluster,
                    CLUSTER_NAMES.get(cluster),
                    1,
                    cluster);
        }
        if (theClusterHoldingNothing != EVERY_CLUSTER_HOLDS_A_DOCUMENT) {
            UnseenEditFixture.editedWithoutTheWalkNoticing(root.resolve(jdbcTemplate.queryForObject(
                    "SELECT path FROM file_occurrence WHERE id = ?",
                    String.class,
                    documents.get(theClusterHoldingNothing))));
        }
    }

    /**
     * The lines written at the level this run keeps for work having stopped, which is one louder than
     * the level a single cluster left unwritten is reported at.
     *
     * <p>The level is the whole of the distinction being read here: every turned-down answer already
     * writes a line, and those lines name a count and a reason too, so a claim that looked at all of
     * them together would be met by the per-cluster lines alone and would say nothing about whether the
     * step reported stopping at all.
     */
    private List<String> linesSayingSomethingStopped() {
        return logged.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.ERROR))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    /** Everything stage 6b wrote over the clusters of {@code root}, under whichever run it wrote them. */
    private List<RecordedSynthesisDoc> writingKept(Path root) {
        return generationRuns(root).stream()
                .map(RunId::new)
                .flatMap(run -> synthesisDocs.forRun(run).stream())
                .toList();
    }

    /** Every reason stage 6b kept for a cluster of {@code root} it left unwritten. */
    private List<RecordedClusterFault> reasonsKept(Path root) {
        return generationRuns(root).stream()
                .map(RunId::new)
                .flatMap(run -> clusterFaults.forRun(run).stream())
                .toList();
    }

    /**
     * Whether this step's own work over {@code root} is recorded as complete under the record it wrote,
     * which is what every later invocation reads to decide whether to walk past this step.
     */
    private boolean theWorkIsRecordedAsDone(Path root) {
        return generationRuns(root).stream()
                .anyMatch(run -> jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?",
                                Integer.class,
                                run,
                                GenerationRun.STAGE)
                        > 0);
    }

    /** The arrangement the most recent invocation over {@code root} recorded. */
    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                ArrangementRun.STAGE,
                Walk.canonicalRoot(root).toString()));
    }

    /** The arrangement of {@code root} the profile's approval names. */
    private RunId theApprovedArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? AND r.id LIKE ? ORDER BY r.rowid LIMIT 1",
                String.class,
                ArrangementRun.STAGE,
                Walk.canonicalRoot(root).toString(),
                profileStore.load().arrangementApproved().value() + "%"));
    }

    /**
     * Every record of this step having run over {@code root}, oldest first, scoped to this corpus's
     * walk: one working directory serves the whole class and the database outlives each method, so an
     * unscoped count would be a claim about every corpus any method here ever walked.
     */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                GenerationRun.STAGE,
                Walk.canonicalRoot(root).toString());
    }
}
