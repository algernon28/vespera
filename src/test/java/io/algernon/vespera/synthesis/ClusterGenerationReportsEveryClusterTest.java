package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * What {@code ClusterGeneration.write} tells its caller about every cluster, through the two methods ADR-192
 * gives {@code GenerationProgress} (amending ADR-190 sections 2 and 4): {@code toGoThrough(long)} once, with
 * the number of clusters, after reading the slots already written and before the walk, zero included; and
 * {@code clusterGoneThrough()} once at the end of each cluster's path, after any report of the four kinds,
 * whether the cluster was already written, had nothing sendable, had nothing fitting the window, had no
 * document counted inside the room, had its answer turned down, the fifth in a row included, or had it
 * believed.
 *
 * <p>The serving engine is a double keyed on the cluster's label, as in {@code ClusterGenerationTest}, whose
 * own {@code GenerationProgress} implements only the four methods ADR-190 gave it and compiles unchanged,
 * because the two new ones have defaults.
 *
 * <p><b>Part (d) of ADR-192.</b> Does not compile until the two methods exist; part (d) moves it into {@code
 * src/test}. Report text says <em>group</em> where these names say cluster (ADR-122).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Synthesis")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
@Link(name = "ADR-190", url = Adr.STAGE_6_RULES_LIVE_IN_SYNTHESIS, type = "adr")
class ClusterGenerationReportsEveryClusterTest {

    private static final String MODEL_NAME = "qwen3:8b";
    private static final int THE_WINDOW = 8192;
    private static final int COUNTED_PAST_THE_ROOM = THE_WINDOW - 1;
    private static final int A_SMALL_COUNT = 40;
    private static final int A_FEW_WORDS = 5;
    private static final int MORE_WORDS_THAN_THE_ROOM_HOLDS = 50_000;
    private static final String SEED_PATH = "seeds/safety.docx";

    /** One cluster for each way through the walk: already written, nothing sendable, nothing fits, no room counted, turned down, believed. */
    private static final int ONE_OF_EACH_PATH = 6;

    /** Five turned down in a row stop the walk; a sixth is never reached. */
    private static final int ONE_MORE_THAN_THE_STREAK = 6;

    private static final int THE_STREAK = 5;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SynthesisDocs synthesisDocs;
    private ScriptedEngine engine;
    private ClusterGeneration generation;
    private OccurrenceId seed;
    private RunId run;
    private final List<String> events = new ArrayList<>();
    private final Map<Integer, List<Exemplar>> documentsOf = new HashMap<>();
    private final Map<Integer, OccurrenceId> documents = new HashMap<>();

    @BeforeEach
    void aFreshRunAndEngine() {
        synthesisDocs = new SynthesisDocs(jdbcTemplate);
        engine = new ScriptedEngine();
        generation = new ClusterGeneration(new ClusterSynthesis(engine), synthesisDocs, new ClusterFaults(jdbcTemplate));
        documents.clear();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus-" + System.nanoTime()));
        ledger.occurrences().fileOccurrence(walkId, new OccurrencePath(SEED_PATH), 1, Instant.EPOCH, Instant.EPOCH);
        seed = ledger.occurrences().occurrenceId(walkId, new OccurrencePath(SEED_PATH)).orElseThrow();
        run = ledger.runs().startRun("generation", "g" + System.nanoTime(), "{}", walkId, List.of());
        events.clear();
        documentsOf.clear();
    }

    @Test
    @Story("Writing over the groups tells its caller about every group")
    @DisplayName("Every group is reported gone through once, after its own report, whichever way the walk went past it")
    void everyPathEndsInOneReportOfAClusterGoneThrough() {
        synthesisDocs.record(run, seed, 0, new SynthesisDoc("Written before", "Written before [1].", List.of(document(0))));
        documentsOf.put(1, List.of());
        documentsOf.put(2, List.of(aDocument(2, MORE_WORDS_THAN_THE_ROOM_HOLDS)));
        engine.script(label(3), Script.COUNTED_PAST_THE_ROOM);
        engine.script(label(4), Script.UNREADABLE);

        GenerationOutcome outcome = write(clusters(ONE_OF_EACH_PATH));

        claim(
                "the walk was left unfinished, since two groups could not be sent",
                () -> assertThat(outcome).isInstanceOf(GenerationOutcome.LeftUnfinished.class));
        claim(
                "the walk is announced once with six before anything else, and each group ends in one report of a"
                        + " group gone through, after the report its path earns: already written, nothing sendable,"
                        + " nothing fits, no room counted, turned down, believed",
                () -> assertThat(events)
                        .containsExactly(
                                "to-go-through 6",
                                "gone-through",
                                "no sendable document " + label(1), "gone-through",
                                "nothing fits " + label(2), "gone-through",
                                "no room counted " + label(3), "gone-through",
                                "turned down " + label(4), "gone-through",
                                "gone-through"));
    }

    @Test
    @Story("Writing over the groups tells its caller about every group")
    @DisplayName("The fifth answer turned down in a row is reported gone through before the walk stops, and the group after it is not")
    void theFifthIsGoneThroughBeforeTheWalkStops() {
        for (int ordinal = 0; ordinal < THE_STREAK; ordinal++) {
            engine.script(label(ordinal), Script.UNREADABLE);
        }

        GenerationOutcome outcome = write(clusters(ONE_MORE_THAN_THE_STREAK));

        claim("the walk stopped", () -> assertThat(outcome).isInstanceOf(GenerationOutcome.Stopped.class));
        claim(
                "five groups are reported gone through, the fifth included, and the sixth, never reached, is not",
                () -> assertThat(events.stream().filter("gone-through"::equals).count()).isEqualTo(THE_STREAK));
        claim(
                "and the last thing reported is the fifth group gone through, after its answer was turned down",
                () -> assertThat(events).endsWith("turned down " + label(4), "gone-through"));
    }

    @Test
    @Story("Writing over the groups tells its caller about every group")
    @DisplayName("An empty arrangement is announced with zero and reports nothing else")
    void anEmptyArrangementIsAnnouncedWithZero() {
        write(List.of());

        claim("the one report is the total, zero", () -> assertThat(events).containsExactly("to-go-through 0"));
    }

    private GenerationOutcome write(List<RecordedCluster> clusters) {
        return generation.write(
                run,
                clusters.size(),
                clusters,
                recorded -> {
                    int ordinal = recorded.cluster().ordinal();
                    return new ClusterMaterial(
                            SEED_PATH, documentsOf.getOrDefault(ordinal, List.of(aDocument(ordinal, A_FEW_WORDS))));
                },
                MODEL_NAME,
                THE_WINDOW,
                new GenerationProgress() {
                    @Override
                    public void noSendableDocument(RecordedCluster cluster) {
                        events.add("no sendable document " + cluster.label().value());
                    }

                    @Override
                    public void nothingFitsTheWindow(RecordedCluster cluster, int documentCount, int contextWindow) {
                        events.add("nothing fits " + cluster.label().value());
                    }

                    @Override
                    public void noDocumentCountedInsideTheRoom(RecordedCluster cluster, ClusterFault fault) {
                        events.add("no room counted " + cluster.label().value());
                    }

                    @Override
                    public void answerTurnedDown(RecordedCluster cluster, ClusterFault fault) {
                        events.add("turned down " + cluster.label().value());
                    }

                    @Override
                    public void toGoThrough(long clusterCount) {
                        events.add("to-go-through " + clusterCount);
                    }

                    @Override
                    public void clusterGoneThrough() {
                        events.add("gone-through");
                    }
                });
    }

    private List<RecordedCluster> clusters(int count) {
        return IntStream.range(0, count)
                .mapToObj(ordinal -> new RecordedCluster(
                        new ArrangedCluster(seed, ordinal, 1, 1, ordinal + 1), new ClusterLabel(label(ordinal))))
                .toList();
    }

    private static String label(int ordinal) {
        return "Cluster %02d".formatted(ordinal);
    }

    private Exemplar aDocument(int ordinal, int words) {
        return new Exemplar(document(ordinal), "word ".repeat(words).strip(), words, 0.9);
    }

    private OccurrenceId document(int ordinal) {
        return documents.computeIfAbsent(ordinal, at -> {
            Ledger ledger = new Ledger(jdbcTemplate);
            WalkId walkId = new WalkId(jdbcTemplate.queryForObject(
                    "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, seed.value()));
            OccurrencePath path = new OccurrencePath("corpus/document-" + at + ".txt");
            ledger.occurrences().fileOccurrence(walkId, path, 1, Instant.EPOCH, Instant.EPOCH);
            return ledger.occurrences().occurrenceId(walkId, path).orElseThrow();
        });
    }

    /** What the engine double does with a question carrying a given label. */
    private enum Script {
        BELIEVED,
        UNREADABLE,
        COUNTED_PAST_THE_ROOM
    }

    /** A serving engine scripted per cluster label; a label nobody scripted is believed. */
    private static final class ScriptedEngine implements ChatModel {

        private final Map<String, Script> byLabel = new HashMap<>();

        void script(String label, Script script) {
            byLabel.put(label, script);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            String question = prompt.getContents();
            Script script = byLabel.entrySet().stream()
                    .filter(entry -> question.contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(Script.BELIEVED);
            boolean counting = ((OllamaChatOptions) prompt.getOptions()).getNumPredict() == 1;
            if (counting) {
                return response("", script == Script.COUNTED_PAST_THE_ROOM ? COUNTED_PAST_THE_ROOM : A_SMALL_COUNT);
            }
            return response(
                    script == Script.UNREADABLE
                            ? "{\"title\":\"x\",\"prose\":"
                            : "{\"title\":\"Written now\",\"prose\":\"The records [1] agree.\"}",
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
