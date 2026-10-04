package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 6b's loop over the clusters of the approved arrangement, as {@code synthesis} runs it (ADR-190):
 * every cluster written, skipped, left unsendable or faulted, the stop after five answers turned down in
 * a row (ADR-111), the rule that decides whether the step may record completion (ADR-116), the repair
 * pass dropping a fault row once an answer is believed (ADR-111, #185), and the one fault that neither
 * adds to the five nor clears them (ADR-166 §4a).
 *
 * <p><b>These are the rules {@code GenerationBreakerInvocationTest}, {@code GenerationFaultInvocationTest}
 * and {@code GenerationPromptRefusedInvocationTest} pin through a whole invocation</b>, which stay in
 * {@code pipeline} and keep pinning the step: its exit code, its stop line, the deliverable and the
 * completion record. This class pins the same rules at the class that now holds them, with inputs a
 * whole invocation cannot build cheaply — a fault from an earlier invocation standing against a cluster
 * this one finds unsendable, an answer already written sitting inside a streak — and each built so that
 * the other reading of the rule gives a different answer.
 *
 * <p><b>The serving engine here is a double keyed on the cluster's label</b>, which the question carries:
 * each label is scripted to be believed, turned down one of two ways, counted past the room at every
 * length (so no answer is ever asked for), or refused for a reason that is not about length. Its
 * counting call reports a small count for every other script.
 *
 * <p>Report text says <em>group</em> where these names say cluster (ADR-122).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("408")
@Link(name = "ADR-190", url = Adr.STAGE_6_RULES_LIVE_IN_SYNTHESIS, type = "adr")
@Link(name = "ADR-111", url = Adr.A_CLUSTER_FAULT_IS_A_ROW_IN_SYNTHESIS, type = "adr")
class ClusterGenerationTest {

    private static final String MODEL_NAME = "qwen3:8b";

    /** The window every call here reads in, written out rather than read off the code under test. */
    private static final int THE_WINDOW = 8192;

    /** What the engine counts a question at when it had to cut it: the window less one. */
    private static final int COUNTED_PAST_THE_ROOM = THE_WINDOW - 1;

    /** What the engine counts every other question at: the chat template and a few words. */
    private static final int A_SMALL_COUNT = 40;

    /** Words in an ordinary document's opening chunk: far inside the room. */
    private static final int A_FEW_WORDS = 5;

    /** Words in a document longer than the whole room by the estimate, so nothing of it is ever sent. */
    private static final int MORE_WORDS_THAN_THE_ROOM_HOLDS = 50_000;

    /** How many answers turned down in a row stop the step, written out. */
    private static final int THE_STREAK_THAT_STOPS_THE_STEP = 5;

    /** Six clusters: five to stop on, and one after them the loop must never reach. */
    private static final int ONE_MORE_THAN_THE_STREAK = THE_STREAK_THAT_STOPS_THE_STEP + 1;

    /** Seven clusters: two turned down, one walked past, three turned down, one never reached. */
    private static final int ONE_EITHER_SIDE_OF_A_STREAK_WITH_ONE_WALKED_PAST = THE_STREAK_THAT_STOPS_THE_STEP + 2;

    /** Where the walked-past cluster sits among those seven, counting from zero: inside the streak. */
    private static final int THE_THIRD = 2;

    /** Nine clusters: four turned down, one believed, four turned down. */
    private static final int FOUR_EITHER_SIDE_OF_A_BELIEVED_ANSWER = 9;

    /** Where the believed answer sits among those nine, counting from zero. */
    private static final int THE_FIFTH = 4;

    /** What those nine leave standing: a reason for each answer turned down. */
    private static final int EIGHT_REASONS = 8;

    /** Three clusters: enough for the one a test is about to have a cluster either side of it. */
    private static final int THREE_CLUSTERS = 3;

    /** Two clusters: one a test is about, and one beside it. */
    private static final int TWO_CLUSTERS = 2;

    /** Five clusters: one for each way a cluster is left unwritten, and one written. */
    private static final int FIVE_CLUSTERS = 5;

    private static final int ONE = 1;

    private static final int NONE = 0;

    /** The seed every cluster here sits under, by path, as the material handed to the loop names it. */
    private static final String SEED_PATH = "seeds/safety.docx";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SynthesisDocs synthesisDocs;
    private ClusterFaults clusterFaults;
    private ScriptedEngine engine;
    private ClusterGeneration generation;
    private OccurrenceId seed;
    private RunId run;

    /** Which clusters the loop asked for documents, in the order it asked. */
    private final List<String> askedForDocuments = new ArrayList<>();

    /** Every line the loop handed back, as the kind of report and the cluster it was about. */
    private final List<String> reported = new ArrayList<>();

    /** Documents per cluster ordinal, built per test; a cluster missing here has one ordinary document. */
    private final Map<Integer, List<Exemplar>> documentsOf = new HashMap<>();

    @BeforeEach
    void aFreshRunAndEngine() {
        synthesisDocs = new SynthesisDocs(jdbcTemplate);
        clusterFaults = new ClusterFaults(jdbcTemplate);
        engine = new ScriptedEngine();
        generation = new ClusterGeneration(new ClusterSynthesis(engine), synthesisDocs, clusterFaults);
        seed = anOccurrence(SEED_PATH);
        run = new Ledger(jdbcTemplate).startRun("generation", "g" + System.nanoTime(), "{}", theWalkOf(seed), List.of());
        askedForDocuments.clear();
        reported.clear();
        documentsOf.clear();
    }

    @Test
    @Story("Answers nobody believes, one after another, stop the step")
    @DisplayName("Five answers turned down in a row stop the writing, and the group after them is never asked about")
    void stopsAtTheFifthAnswerTurnedDownInARow() {
        List<RecordedCluster> clusters = clusters(ONE_MORE_THAN_THE_STREAK);
        for (int ordinal = 0; ordinal < THE_STREAK_THAT_STOPS_THE_STEP; ordinal++) {
            engine.script(label(ordinal), ordinal % 2 == 0 ? Script.UNREADABLE : Script.CITES_A_DOCUMENT_NEVER_SENT);
        }

        GenerationOutcome outcome = write(clusters);

        claim(
                "the writing stops, and says so: " + THE_STREAK_THAT_STOPS_THE_STEP + " answers turned down one"
                        + " after another are the model, the word budget or the shape of the answer being wrong"
                        + " for this archive",
                () -> assertThat(outcome).isInstanceOf(GenerationOutcome.Stopped.class));
        claim(
                "it carries the " + THE_STREAK_THAT_STOPS_THE_STEP + " reasons, in the order the groups were"
                        + " asked about, so the line that says it stopped can name each of them",
                () -> assertThat(((GenerationOutcome.Stopped) outcome).turnedDownInARow())
                        .extracting(ClusterFault::kind)
                        .containsExactly(
                                ClusterFaultKind.SCHEMA_VIOLATION,
                                ClusterFaultKind.CITATION_NOT_IN_RANGE,
                                ClusterFaultKind.SCHEMA_VIOLATION,
                                ClusterFaultKind.CITATION_NOT_IN_RANGE,
                                ClusterFaultKind.SCHEMA_VIOLATION));
        claim(
                "exactly " + THE_STREAK_THAT_STOPS_THE_STEP + " answers were asked for though "
                        + ONE_MORE_THAN_THE_STREAK + " groups were arranged, and the documents of the group after"
                        + " the fifth were never even gathered: what a group is written from is asked for only"
                        + " when the writing reaches it",
                () -> {
                    assertThat(engine.answered()).hasSize(THE_STREAK_THAT_STOPS_THE_STEP);
                    assertThat(askedForDocuments).doesNotContain(label(THE_STREAK_THAT_STOPS_THE_STEP));
                });
        claim(
                "and a reason is kept against each of the " + THE_STREAK_THAT_STOPS_THE_STEP + ", the fifth"
                        + " included, with nothing written over any group",
                () -> {
                    assertThat(clusterFaults.forRun(run)).hasSize(THE_STREAK_THAT_STOPS_THE_STEP);
                    assertThat(synthesisDocs.forRun(run)).isEmpty();
                });
    }

    @Test
    @Story("Answers nobody believes, one after another, stop the step")
    @DisplayName("The number of answers turned down in a row that stops the writing is five")
    void theStreakThatStopsTheStepIsFive() {
        claim(
                "the writing stops at " + THE_STREAK_THAT_STOPS_THE_STEP + " answers turned down in a row, the"
                        + " count the earlier stage's own stop uses for a mix of failures",
                () -> assertThat(ClusterGeneration.CONSECUTIVE_TURNED_DOWN_ANSWERS)
                        .isEqualTo(THE_STREAK_THAT_STOPS_THE_STEP));
    }

    @Test
    @Story("An answer that is believed clears what came before it")
    @DisplayName("Four answers turned down either side of a believed one do not stop the writing")
    void aBelievedAnswerClearsTheCount() {
        List<RecordedCluster> clusters = clusters(FOUR_EITHER_SIDE_OF_A_BELIEVED_ANSWER);
        for (int ordinal = 0; ordinal < FOUR_EITHER_SIDE_OF_A_BELIEVED_ANSWER; ordinal++) {
            if (ordinal != THE_FIFTH) {
                engine.script(label(ordinal), Script.UNREADABLE);
            }
        }

        GenerationOutcome outcome = write(clusters);

        claim(
                "every one of the " + FOUR_EITHER_SIDE_OF_A_BELIEVED_ANSWER + " groups is asked about: the"
                        + " believed answer in the middle drops the count to nothing, so the four after it are"
                        + " four and not eight",
                () -> assertThat(engine.answered()).hasSize(FOUR_EITHER_SIDE_OF_A_BELIEVED_ANSWER));
        claim(
                "and the writing is left unfinished rather than stopped, with " + EIGHT_REASONS + " reasons"
                        + " standing, all " + EIGHT_REASONS + " of them kept this time",
                () -> assertThat(outcome).isInstanceOfSatisfying(GenerationOutcome.LeftUnfinished.class, left -> {
                    assertThat(left.standingFaults()).isEqualTo(EIGHT_REASONS);
                    assertThat(left.faultedThisInvocation()).isEqualTo(EIGHT_REASONS);
                }));
    }

    @Test
    @Story("Answers nobody believes, one after another, stop the step")
    @DisplayName("A group with no document that can be sent neither adds to the count nor clears it")
    @Link(name = "ADR-121", url = Adr.A_WINDOW_WITH_NO_ROOM_IS_REFUSED, type = "adr")
    void aClusterWithNothingToSendNeitherAddsToTheCountNorClearsIt() {
        List<RecordedCluster> clusters = clusters(ONE_EITHER_SIDE_OF_A_STREAK_WITH_ONE_WALKED_PAST);
        documentsOf.put(THE_THIRD, List.of());
        everyClusterTurnedDownBut(THE_THIRD, THE_STREAK_THAT_STOPS_THE_STEP + 1);

        GenerationOutcome outcome = write(clusters);

        claim(
                "the writing stops, so the third group did not clear the count on its way past: nothing was"
                        + " asked there and nothing came back",
                () -> assertThat(outcome).isInstanceOf(GenerationOutcome.Stopped.class));
        claim(
                "after exactly " + THE_STREAK_THAT_STOPS_THE_STEP + " answers, which also says the third group"
                        + " did not count towards the stop: counting it would have stopped the writing one answer"
                        + " earlier",
                () -> assertThat(engine.answered()).hasSize(THE_STREAK_THAT_STOPS_THE_STEP));
        claim(
                "no reason is kept for the third group, which no answer came back for, and the stop still"
                        + " carries why it went unwritten so its page can say so",
                () -> {
                    assertThat(clusterFaults.forRun(run)).hasSize(THE_STREAK_THAT_STOPS_THE_STEP);
                    assertThat(outcome.unsendable())
                            .containsExactly(Map.entry(slot(THE_THIRD), Unwritten.NO_SENDABLE_DOCUMENT));
                });
    }

    @Test
    @Story("Answers nobody believes, one after another, stop the step")
    @DisplayName("A group none of whose documents would fit by the estimate neither adds to the count nor clears it")
    @Link(name = "ADR-121", url = Adr.A_WINDOW_WITH_NO_ROOM_IS_REFUSED, type = "adr")
    void aClusterNothingFitsNeitherAddsToTheCountNorClearsIt() {
        List<RecordedCluster> clusters = clusters(ONE_EITHER_SIDE_OF_A_STREAK_WITH_ONE_WALKED_PAST);
        documentsOf.put(THE_THIRD, List.of(aDocument(THE_THIRD, MORE_WORDS_THAN_THE_ROOM_HOLDS)));
        everyClusterTurnedDownBut(THE_THIRD, THE_STREAK_THAT_STOPS_THE_STEP + 1);

        GenerationOutcome outcome = write(clusters);

        claim(
                "the writing stops after exactly " + THE_STREAK_THAT_STOPS_THE_STEP + " answers: the third group"
                        + " was walked past without a call, neither clearing the count nor adding to it",
                () -> {
                    assertThat(outcome).isInstanceOf(GenerationOutcome.Stopped.class);
                    assertThat(engine.answered()).hasSize(THE_STREAK_THAT_STOPS_THE_STEP);
                    assertThat(engine.counted()).noneMatch(question -> question.contains(label(THE_THIRD)));
                });
        claim(
                "and the stop carries why the third group went unwritten: none of its documents would fit",
                () -> assertThat(outcome.unsendable())
                        .containsExactly(Map.entry(slot(THE_THIRD), Unwritten.NOTHING_FITS_THE_WINDOW)));
    }

    @Test
    @Story("Answers nobody believes, one after another, stop the step")
    @DisplayName("A group none of whose documents fits the window with room left for the answer neither adds to the count nor clears it")
    @Link(name = "ADR-166", url = Adr.THE_SERVING_ENGINE_COUNTS_A_QUESTION_BEFORE_IT_IS_SENT, type = "adr")
    void aClusterTheEngineFindsNoRoomForNeitherAddsToTheCountNorClearsIt() {
        List<RecordedCluster> clusters = clusters(ONE_EITHER_SIDE_OF_A_STREAK_WITH_ONE_WALKED_PAST);
        everyClusterTurnedDownBut(THE_THIRD, THE_STREAK_THAT_STOPS_THE_STEP + 1);
        engine.script(label(THE_THIRD), Script.COUNTED_PAST_THE_ROOM);

        GenerationOutcome outcome = write(clusters);

        claim(
                "the writing stops after exactly " + THE_STREAK_THAT_STOPS_THE_STEP + " answers, none of them the"
                        + " third group's: it was never asked for an answer, so it is no evidence either way",
                () -> {
                    assertThat(outcome).isInstanceOf(GenerationOutcome.Stopped.class);
                    assertThat(engine.answered()).hasSize(THE_STREAK_THAT_STOPS_THE_STEP);
                    assertThat(engine.answered()).noneMatch(question -> question.contains(label(THE_THIRD)));
                });
        claim(
                "the reasons the stop carries are the " + THE_STREAK_THAT_STOPS_THE_STEP + " turned-down answers"
                        + " alone, and none of them is the third group's",
                () -> assertThat(((GenerationOutcome.Stopped) outcome).turnedDownInARow())
                        .hasSize(THE_STREAK_THAT_STOPS_THE_STEP)
                        .noneMatch(fault -> fault.kind() == ClusterFaultKind.PROMPT_EVALUATION_CEILING));
        claim(
                "and a reason is still kept for the third group, " + (THE_STREAK_THAT_STOPS_THE_STEP + 1) + " in"
                        + " all: it was asked about, and the engine's count is what came back",
                () -> assertThat(clusterFaults.forRun(run))
                        .hasSize(THE_STREAK_THAT_STOPS_THE_STEP + 1)
                        .anySatisfy(kept -> {
                            assertThat(kept.clusterOrdinal()).isEqualTo(THE_THIRD);
                            assertThat(kept.fault().kind()).isEqualTo(ClusterFaultKind.PROMPT_EVALUATION_CEILING);
                        }));
    }

    @Test
    @Story("Answers nobody believes, one after another, stop the step")
    @DisplayName("Five groups in a row none of whose documents fits the window with room left for the answer do not stop the writing")
    @Link(name = "ADR-166", url = Adr.THE_SERVING_ENGINE_COUNTS_A_QUESTION_BEFORE_IT_IS_SENT, type = "adr")
    void fiveClustersTheEngineFindsNoRoomForDoNotStop() {
        List<RecordedCluster> clusters = clusters(ONE_MORE_THAN_THE_STREAK);
        for (int ordinal = 0; ordinal < THE_STREAK_THAT_STOPS_THE_STEP; ordinal++) {
            engine.script(label(ordinal), Script.COUNTED_PAST_THE_ROOM);
        }

        GenerationOutcome outcome = write(clusters);

        claim(
                "the group after the five is written: were the five to stop the writing, every later run would"
                        + " stop at the same five, and the groups after them would never be written",
                () -> assertThat(synthesisDocs.forRun(run))
                        .singleElement()
                        .satisfies(doc -> assertThat(doc.clusterOrdinal()).isEqualTo(THE_STREAK_THAT_STOPS_THE_STEP)));
        claim(
                "exactly " + ONE + " answer was asked for, and the writing is left unfinished with the "
                        + THE_STREAK_THAT_STOPS_THE_STEP + " reasons standing",
                () -> {
                    assertThat(engine.answered()).hasSize(ONE);
                    assertThat(outcome).isInstanceOfSatisfying(GenerationOutcome.LeftUnfinished.class, left ->
                            assertThat(left.standingFaults()).isEqualTo(THE_STREAK_THAT_STOPS_THE_STEP));
                });
    }

    @Test
    @Story("Answers nobody believes, one after another, stop the step")
    @DisplayName("A group an earlier invocation already wrote is walked past without its documents being gathered, and neither adds to the count nor clears it")
    @Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
    void aClusterAlreadyWrittenIsWalkedPastWithoutClearingTheCount() {
        List<RecordedCluster> clusters = clusters(ONE_EITHER_SIDE_OF_A_STREAK_WITH_ONE_WALKED_PAST);
        alreadyWritten(THE_THIRD);
        everyClusterTurnedDownBut(THE_THIRD, THE_STREAK_THAT_STOPS_THE_STEP + 1);

        GenerationOutcome outcome = write(clusters);

        claim(
                "the writing stops after exactly " + THE_STREAK_THAT_STOPS_THE_STEP + " answers: the group already"
                        + " written was not asked about again and did not clear the count",
                () -> {
                    assertThat(outcome).isInstanceOf(GenerationOutcome.Stopped.class);
                    assertThat(engine.answered()).hasSize(THE_STREAK_THAT_STOPS_THE_STEP);
                });
        claim(
                "and its documents were never gathered: a group already written costs nothing to walk past",
                () -> assertThat(askedForDocuments).doesNotContain(label(THE_THIRD)));
    }

    @Test
    @Story("The work is recorded as done only when every group carries its writing")
    @DisplayName("Every group written, or written before, is the one outcome under which the work may be recorded as done")
    @Link(name = "ADR-116", url = Adr.A_RUNS_COMPLETION_IS_RECORDED_PER_STEP, type = "adr")
    void finishesWhenEveryClusterCarriesItsWriting() {
        List<RecordedCluster> clusters = clusters(THREE_CLUSTERS);
        alreadyWritten(1);

        GenerationOutcome outcome = write(clusters);

        claim(
                "the writing is finished: " + (THREE_CLUSTERS - ONE) + " groups written now and " + ONE
                        + " written by an earlier invocation, and no group found that nothing could be sent for",
                () -> assertThat(outcome).isInstanceOfSatisfying(GenerationOutcome.Finished.class, finished -> {
                    assertThat(finished.written()).isEqualTo(THREE_CLUSTERS - ONE);
                    assertThat(finished.alreadyWritten()).isEqualTo(ONE);
                    assertThat(finished.unsendable()).isEmpty();
                }));
    }

    @Test
    @Story("The work is recorded as done only when every group carries its writing")
    @DisplayName("A group nothing could be sent for leaves the work unfinished, though no answer was turned down")
    @Link(name = "ADR-116", url = Adr.A_RUNS_COMPLETION_IS_RECORDED_PER_STEP, type = "adr")
    void leavesTheWorkUnfinishedForAClusterNothingCouldBeSentFor() {
        List<RecordedCluster> clusters = clusters(THREE_CLUSTERS);
        documentsOf.put(1, List.of());

        GenerationOutcome outcome = write(clusters);

        claim(
                "the writing is left unfinished, with no reason standing: a group with nothing to send has no"
                        + " writing, and recording the work as done would leave that hole permanent and"
                        + " unannounced",
                () -> assertThat(outcome).isInstanceOfSatisfying(GenerationOutcome.LeftUnfinished.class, left -> {
                    assertThat(left.standingFaults()).isEqualTo(NONE);
                    assertThat(left.faultedThisInvocation()).isEqualTo(NONE);
                    assertThat(left.unsendable()).containsExactly(Map.entry(slot(1), Unwritten.NO_SENDABLE_DOCUMENT));
                }));
    }

    @Test
    @Story("The work is recorded as done only when every group carries its writing")
    @DisplayName("A reason an earlier invocation kept, still standing, leaves the work unfinished and is counted apart from this invocation's")
    @Link(name = "ADR-116", url = Adr.A_RUNS_COMPLETION_IS_RECORDED_PER_STEP, type = "adr")
    void countsTheReasonsStandingRatherThanThisInvocationsOwn() {
        List<RecordedCluster> clusters = clusters(THREE_CLUSTERS);
        documentsOf.put(1, List.of());
        clusterFaults.record(run, seed, 1, new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, "kept earlier"));

        GenerationOutcome outcome = write(clusters);

        claim(
                "the writing is left unfinished with " + ONE + " reason standing and " + NONE + " of them from"
                        + " this invocation: a repair pass that turned nothing down itself still meets an earlier"
                        + " one's reason, and reporting only its own zero would say nothing went wrong and then"
                        + " refuse to finish",
                () -> assertThat(outcome).isInstanceOfSatisfying(GenerationOutcome.LeftUnfinished.class, left -> {
                    assertThat(left.standingFaults()).isEqualTo(ONE);
                    assertThat(left.faultedThisInvocation()).isEqualTo(NONE);
                }));
    }

    @Test
    @Story("A reason is dropped once a later answer about the same group is believed")
    @DisplayName("A group turned down before and believed now carries its writing, and the reason kept against it is gone")
    @Issue("185")
    void dropsTheReasonKeptOnceTheAnswerIsBelieved() {
        List<RecordedCluster> clusters = clusters(TWO_CLUSTERS);
        clusterFaults.record(run, seed, 1, new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, "kept earlier"));

        GenerationOutcome outcome = write(clusters);

        claim(
                "no reason stands any more: the group was written over this time, and a reason still standing"
                        + " would have the record say one group was both written over and left unwritten",
                () -> assertThat(clusterFaults.forRun(run)).isEmpty());
        claim(
                "so the work is finished, with both of the " + TWO_CLUSTERS + " groups carrying their writing",
                () -> {
                    assertThat(synthesisDocs.forRun(run)).hasSize(TWO_CLUSTERS);
                    assertThat(outcome).isInstanceOf(GenerationOutcome.Finished.class);
                });
    }

    @Test
    @Story("A refusal that would come back for every group still stops the run")
    @DisplayName("A refusal that is not about the length of the question reaches the caller unchanged, and no reason is kept for it")
    @Link(name = "ADR-166", url = Adr.THE_SERVING_ENGINE_COUNTS_A_QUESTION_BEFORE_IT_IS_SENT, type = "adr")
    void aRefusalNotAboutLengthReachesTheCaller() {
        List<RecordedCluster> clusters = clusters(TWO_CLUSTERS);
        engine.script(label(1), Script.REFUSED_AS_A_MODEL_NEVER_FETCHED);

        Throwable thrown = catchThrowable(() -> write(clusters));

        claim(
                "the refusal leaves the writing as it was raised, so the step fails: a model the engine does not"
                        + " have refuses every group alike, and turning that into one group's reason would spend"
                        + " five calls to say so",
                () -> assertThat(thrown).isInstanceOf(NonTransientAiException.class));
        claim(
                "and no reason is kept against the group it was refused for",
                () -> assertThat(clusterFaults.forRun(run)).isEmpty());
    }

    @Test
    @Story("A group is written from the documents gathered for it")
    @DisplayName("Each group's documents are gathered once, in the order of the arrangement, and its question carries its label and its seed")
    void gathersEachClustersDocumentsOnceInOrder() {
        List<RecordedCluster> clusters = clusters(THREE_CLUSTERS);

        write(clusters);

        claim(
                "the documents of each of the " + THREE_CLUSTERS + " groups were gathered exactly once, in the order the arrangement"
                        + " gives the groups",
                () -> assertThat(askedForDocuments).containsExactly(label(0), label(1), label(2)));
        claim(
                "and every question asked carries its group's label and the path of the seed it was gathered"
                        + " under",
                () -> assertThat(engine.answered()).allSatisfy(question -> assertThat(question).contains(SEED_PATH))
                        .anySatisfy(question -> assertThat(question).contains(label(0))));
    }

    @Test
    @Story("A page with nothing written on it says why, in words a reader of the tree can follow")
    @DisplayName("Each group left unwritten is reported once, with the reason it was left so")
    void reportsEachClusterLeftUnwrittenOnce() {
        List<RecordedCluster> clusters = clusters(FIVE_CLUSTERS);
        engine.script(label(0), Script.UNREADABLE);
        documentsOf.put(1, List.of());
        documentsOf.put(2, List.of(aDocument(2, MORE_WORDS_THAN_THE_ROOM_HOLDS)));
        engine.script(label(3), Script.COUNTED_PAST_THE_ROOM);

        write(clusters);

        claim(
                "four reports, one for each group left unwritten and none for the one written: an answer turned"
                        + " down, nothing to send, nothing that fits, and no document the engine counted inside"
                        + " the room",
                () -> assertThat(reported).containsExactly(
                        "answer turned down " + label(0),
                        "no sendable document " + label(1),
                        "nothing fits " + label(2) + " " + ONE + " " + THE_WINDOW,
                        "no document counted inside the room " + label(3)));
    }

    /** Scripts every cluster but {@code spared} among the first {@code count} to be turned down. */
    private void everyClusterTurnedDownBut(int spared, int count) {
        for (int ordinal = 0; ordinal < count; ordinal++) {
            if (ordinal != spared) {
                engine.script(label(ordinal), Script.UNREADABLE);
            }
        }
    }

    /** Runs the loop over {@code clusters}, gathering each one's documents through {@link #documentsOf}. */
    private GenerationOutcome write(List<RecordedCluster> clusters) {
        return generation.write(
                run,
                clusters,
                recorded -> {
                    askedForDocuments.add(recorded.label().value());
                    int ordinal = recorded.cluster().ordinal();
                    return new ClusterMaterial(
                            SEED_PATH, documentsOf.getOrDefault(ordinal, List.of(aDocument(ordinal, A_FEW_WORDS))));
                },
                MODEL_NAME,
                THE_WINDOW,
                new GenerationProgress() {
                    @Override
                    public void noSendableDocument(RecordedCluster cluster) {
                        reported.add("no sendable document " + cluster.label().value());
                    }

                    @Override
                    public void nothingFitsTheWindow(RecordedCluster cluster, int documents, int contextWindow) {
                        reported.add("nothing fits " + cluster.label().value() + " " + documents + " " + contextWindow);
                    }

                    @Override
                    public void noDocumentCountedInsideTheRoom(RecordedCluster cluster, ClusterFault fault) {
                        reported.add("no document counted inside the room " + cluster.label().value());
                    }

                    @Override
                    public void answerTurnedDown(RecordedCluster cluster, ClusterFault fault) {
                        reported.add("answer turned down " + cluster.label().value());
                    }
                });
    }

    /** {@code count} clusters of one partition, at ordinals 0 to count - 1, in that order. */
    private List<RecordedCluster> clusters(int count) {
        return IntStream.range(0, count)
                .mapToObj(ordinal -> new RecordedCluster(
                        new ArrangedCluster(seed, ordinal, 1, 1, ordinal + 1), new ClusterLabel(label(ordinal))))
                .toList();
    }

    /** A label no other label here reads as part of, so the engine double can key on it. */
    private static String label(int ordinal) {
        return "Cluster %02d".formatted(ordinal);
    }

    private ClusterSlot slot(int ordinal) {
        return new ClusterSlot(seed, ordinal);
    }

    /** A synthesis doc an earlier invocation of this run already wrote over cluster {@code ordinal}. */
    private void alreadyWritten(int ordinal) {
        synthesisDocs.record(
                run, seed, ordinal, new SynthesisDoc("Written before", "Written before [1].", List.of(document(ordinal))));
    }

    /** One document of cluster {@code ordinal}, opening with {@code words} words. */
    private Exemplar aDocument(int ordinal, int words) {
        return new Exemplar(document(ordinal), "word ".repeat(words).strip(), words, 0.9);
    }

    /** The occurrence that is cluster {@code ordinal}'s document, recorded once per test. */
    private final Map<Integer, OccurrenceId> documents = new HashMap<>();

    private OccurrenceId document(int ordinal) {
        return documents.computeIfAbsent(ordinal, at -> anotherOccurrenceInTheWalkOf(seed, "corpus/document-" + at + ".txt"));
    }

    private OccurrenceId anOccurrence(String path) {
        documents.clear();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        ledger.fileOccurrence(
                walkId, new OccurrencePath(path), 1, Instant.parse("2026-09-15T10:15:30Z"), Instant.parse("2026-09-01T08:00:00Z"));
        return ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    private OccurrenceId anotherOccurrenceInTheWalkOf(OccurrenceId sibling, String path) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = theWalkOf(sibling);
        ledger.fileOccurrence(
                walkId, new OccurrencePath(path), 1, Instant.parse("2026-09-15T10:15:30Z"), Instant.parse("2026-09-01T08:00:00Z"));
        return ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    private WalkId theWalkOf(OccurrenceId occurrence) {
        return new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, occurrence.value()));
    }

    /** What the engine double does with a question carrying a given label. */
    private enum Script {
        /** Counted small, answered with writing citing the one document sent: believed. */
        BELIEVED,
        /** Counted small, answered with text that stops partway through: a schema violation. */
        UNREADABLE,
        /** Counted small, answered with writing citing a document number never sent. */
        CITES_A_DOCUMENT_NEVER_SENT,
        /** Counted at the window less one however few documents it carries: never answered. */
        COUNTED_PAST_THE_ROOM,
        /** Refused outright, for a reason that is not the question's length. */
        REFUSED_AS_A_MODEL_NEVER_FETCHED
    }

    /** A serving engine scripted per cluster label; a label nobody scripted is believed. */
    private static final class ScriptedEngine implements ChatModel {

        private final Map<String, Script> byLabel = new HashMap<>();
        private final List<String> counted = new ArrayList<>();
        private final List<String> answered = new ArrayList<>();

        void script(String label, Script script) {
            byLabel.put(label, script);
        }

        List<String> counted() {
            return List.copyOf(counted);
        }

        List<String> answered() {
            return List.copyOf(answered);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            String question = prompt.getContents();
            Script script = byLabel.entrySet().stream()
                    .filter(entry -> question.contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(Script.BELIEVED);
            if (script == Script.REFUSED_AS_A_MODEL_NEVER_FETCHED) {
                throw new NonTransientAiException("HTTP 404 - {\"error\":\"model 'qwen3:8b' not found\"}");
            }
            boolean counting = ((OllamaChatOptions) prompt.getOptions()).getNumPredict() == 1;
            if (counting) {
                counted.add(question);
                return response("", script == Script.COUNTED_PAST_THE_ROOM ? COUNTED_PAST_THE_ROOM : A_SMALL_COUNT);
            }
            answered.add(question);
            return response(
                    switch (script) {
                        case UNREADABLE -> "{\"title\":\"x\",\"prose\":";
                        case CITES_A_DOCUMENT_NEVER_SENT -> "{\"title\":\"T\",\"prose\":\"It cites [7].\"}";
                        default -> "{\"title\":\"Written now\",\"prose\":\"The records [1] agree.\"}";
                    },
                    A_SMALL_COUNT);
        }

        private static ChatResponse response(String text, int promptTokens) {
            return new ChatResponse(
                    List.of(new Generation(
                            new AssistantMessage(text),
                            ChatGenerationMetadata.builder().finishReason("stop").build())),
                    ChatResponseMetadata.builder().usage(new DefaultUsage(promptTokens, 1)).build());
        }
    }
}
