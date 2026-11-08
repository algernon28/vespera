package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.WholeRun;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
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

    private static final String MODEL_NAME = "a-writing-model:8b";

    /** The window every call here reads in, written out rather than read off the code under test. */
    private static final int THE_WINDOW = 8192;

    /** What the engine counts a question at when it had to cut it: the window less one. */
    private static final int COUNTED_PAST_THE_ROOM = THE_WINDOW - 1;

    /** What the engine counts every other question at: the chat template and a few words. */
    private static final int A_SMALL_COUNT = 40;

    /** Words in an ordinary document's opening chunk: far inside the room. */
    private static final int A_FEW_WORDS = 5;

    /** How many answers turned down in a row stop the step, written out. */
    private static final int THE_STREAK_THAT_STOPS_THE_STEP = 5;

    /** Six clusters: five to stop on, and one after them the loop must never reach. */
    private static final int ONE_MORE_THAN_THE_STREAK = THE_STREAK_THAT_STOPS_THE_STEP + 1;

    /** Seven clusters: two turned down, one walked past, three turned down, one never reached. */
    private static final int ONE_EITHER_SIDE_OF_A_STREAK_WITH_ONE_WALKED_PAST = THE_STREAK_THAT_STOPS_THE_STEP + 2;

    /** Where the walked-past cluster sits among those seven, counting from zero: inside the streak. */
    private static final int THE_THIRD = 2;

    /** Three clusters: enough for the one a test is about to have a cluster either side of it. */
    private static final int THREE_CLUSTERS = 3;

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
        run = new Ledger(jdbcTemplate).runs().startRun("generation", "g" + System.nanoTime(), "{}", theWalkOf(seed), List.of());
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
                    assertThat(WholeRun.clusterFaults(jdbcTemplate,run)).hasSize(THE_STREAK_THAT_STOPS_THE_STEP);
                    assertThat(WholeRun.synthesisDocs(jdbcTemplate,run)).isEmpty();
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
                clusters.size(),
                clusters,
                recorded -> {
                    askedForDocuments.add(recorded.label().value());
                    int ordinal = recorded.cluster().ordinal();
                    return new ClusterMaterial(
                            SEED_PATH, documentsOf.getOrDefault(ordinal, List.of(aDocument(ordinal, A_FEW_WORDS))));
                },
                MODEL_NAME,
                THE_WINDOW,
                DeliverableLanguage.ENGLISH,
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
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        ledger.occurrences().fileOccurrence(
                walkId, new OccurrencePath(path), 1, Instant.parse("2026-09-15T10:15:30Z"), Instant.parse("2026-09-01T08:00:00Z"));
        return ledger.occurrences().occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    private OccurrenceId anotherOccurrenceInTheWalkOf(OccurrenceId sibling, String path) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = theWalkOf(sibling);
        ledger.occurrences().fileOccurrence(
                walkId, new OccurrencePath(path), 1, Instant.parse("2026-09-15T10:15:30Z"), Instant.parse("2026-09-01T08:00:00Z"));
        return ledger.occurrences().occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
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
                throw new NonTransientAiException("HTTP 404 - {\"error\":\"model 'a-writing-model:8b' not found\"}");
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
