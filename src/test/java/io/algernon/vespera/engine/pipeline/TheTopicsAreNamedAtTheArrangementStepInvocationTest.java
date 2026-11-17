package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The topics are named at the head of the arrangement step (ADR-407 §1, §4, §9), end to end: the naming model, a
 * machine's own value of {@code vespera.naming.model} here, is pulled at that step where Ollama does not serve it, and
 * nowhere earlier; each topic is asked for its name from its passages as the chunk cache holds them; and, until the
 * names are stored, each is logged at INFO, a line a topic, with its key, its name and where the name came from.
 *
 * <p>Pulling is on, against a {@link StubOllama}, as {@link AModelOllamaDoesNotServeIsPulledInvocationTest} has it:
 * whether Ollama serves a model is answered by {@link EmbeddingScriptedBeans}' client, scripted by name, and {@code
 * /api/pull} by the stub. Every model is served unless a test says otherwise, so a test that scripts no refusal pulls
 * nothing. The naming model is a name of the test's own, so that no other step's check of a model is a check of it.
 *
 * <p>One seed of one passage gives one topic, keyed by that passage, {@code seed.txt} and its first chunk, 0.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Arrangement")
@Feature("Topic names")
@Issue("829")
@Link(name = "ADR-407", url = Adr.TOPICS_ARE_NAMED_AT_THE_HEAD_OF_THE_ARRANGEMENT, type = "adr")
class TheTopicsAreNamedAtTheArrangementStepInvocationTest {

    /** The model the topics are named by on this machine: a name of the test's own. */
    private static final String THE_NAMING_MODEL = "a-naming-model:4b";

    /** The seed's file, whose one passage is the one topic's first, and so its key. */
    private static final String THE_SEED = "seed.txt";

    /** How many topics one seed of one passage gives. */
    private static final int ONE_TOPIC = 1;

    /** The words the scripted converter gives every seed and corpus document, which the chunk cache stores. */
    private static final String THE_CONVERTED_WORDS = "stubbed but real content";

    /** What the chunk cache is made to hold in their place once every chunk was cut and embedded. */
    private static final String THE_CACHED_WORDS = "words the chunk cache alone holds";

    /** What Ollama answers a pull of a name its registry does not have with. */
    private static final String NO_SUCH_MODEL = "pull model manifest: file does not exist";

    /** A pull's last line, once the model is served. */
    private static final String SUCCESS = "{\"status\":\"success\"}";

    /**
     * The line logged for each topic, in the console's pattern as the tests configure it: the time, the level, the
     * thread and the logger, then the words ADR-407 §9 fixes.
     */
    private static final Pattern A_NAME_LINE = Pattern.compile(
            "^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} (\\S+)\\s+\\[[^\\]]*\\] \\S+ - (Stage 6a \\(arrangement\\) topic .*)$");

    private static final StubOllama OLLAMA = StubOllama.started();

    /** How many calls had reached the embedding model when the pull ended, or -1 before it has. */
    private static volatile int embeddingCallsWhenPulled = -1;

    /** How many naming calls had been made when the pull ended, or -1 before it has. */
    private static volatile int namingCallsWhenPulled = -1;

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void aNamingModelOfTheMachinesOwnAndPullingOn(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.naming.model", () -> THE_NAMING_MODEL);
        registry.add("vespera.models.pull", () -> "true");
        registry.add("spring.ai.ollama.base-url", () -> OLLAMA.baseUrl().toString());
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Drops every script, count and request before each test as well as after it, for the class-order reason. */
    @BeforeEach
    @AfterEach
    void everyModelIsServedHereAgain() {
        EmbeddingScriptedBeans.servesEveryModel();
        GenerationScriptedBeans.forgetScriptedAnswers();
        OLLAMA.forget();
        embeddingCallsWhenPulled = -1;
        namingCallsWhenPulled = -1;
    }

    @AfterAll
    static void stopTheStub() {
        OLLAMA.close();
    }

    @Test
    @Story("Each topic is named before the arrangement is shown for approval")
    @DisplayName("Each topic is asked for its name once, and the name is logged with the topic's key and where the name came from")
    void eachTopicIsNamedAndItsNameLogged(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        aCorpusUpToTheArrangement(root, seeds);
        int before = output.getAll().length();

        cli.run("run", root.toString());
        List<String[]> logged = nameLinesIn(output.getAll().substring(before));

        claim("the invocation reports success, stopping at the arrangement's approval",
                () -> assertThat(cli.getExitCode()).isZero());
        claim("the naming model was asked " + ONE_TOPIC + " time, once for the one topic, and under the machine's own"
                        + " naming model",
                () -> assertThat(GenerationScriptedBeans.namingModelsAsked()).containsExactly(THE_NAMING_MODEL));
        claim("and one line is logged at INFO for that topic, naming its key, the seed's file and its first chunk, the"
                        + " name the model wrote, between quotation marks, and that the model wrote it",
                () -> assertThat(logged).extracting(line -> line[0] + " " + line[1]).containsExactly(
                        "INFO Stage 6a (arrangement) topic " + THE_SEED + " chunk 0 named \""
                                + GenerationScriptedBeans.TOPIC_NAME_PREFIX + " 1\" (written)"));
    }

    @Test
    @Story("A name is asked from the passages most like the rest of the topic")
    @DisplayName("A topic's passages are sent as the chunk cache holds them, not cut again from the seed")
    void thePassagesAreReadFromTheChunkCache(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusUpToTheArrangement(root, seeds);
        // done as the arrangement step checks the naming model, after every chunk was cut and embedded and before
        // any topic is named: the cache then holds words the seed's file and its conversion never held
        EmbeddingScriptedBeans.whenAskedAbout(THE_NAMING_MODEL, () -> jdbcTemplate.update(
                "UPDATE chunk_cache SET chunk_text = ? WHERE chunk_text LIKE ?",
                THE_CACHED_WORDS,
                "%" + THE_CONVERTED_WORDS + "%"));

        cli.run("run", root.toString());
        List<String> asked = GenerationScriptedBeans.namingQuestionsSent();

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim("the one topic was asked for its name once, its first passage being the words the chunk cache held, and"
                        + " none of the words the seed was converted to",
                () -> assertThat(asked).singleElement().satisfies(question -> assertThat(question)
                        .contains("\nPassage 1:\n" + THE_CACHED_WORDS + "\n")
                        .doesNotContain(THE_CONVERTED_WORDS)));
    }

    @Test
    @Story("The naming model is pulled at the step that needs it")
    @DisplayName("A naming model Ollama does not serve is pulled at the arrangement step, after the scoring and before any topic is named")
    void aNamingModelNotServedIsPulledAtTheArrangementStep(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusUpToTheArrangement(root, seeds);
        EmbeddingScriptedBeans.hasNeverPulled(THE_NAMING_MODEL);
        OLLAMA.onPulled(name -> {
            embeddingCallsWhenPulled = EmbeddingScriptedBeans.embeddingCallsMade();
            namingCallsWhenPulled = GenerationScriptedBeans.namingCallsMade();
            EmbeddingScriptedBeans.hasNowPulled(name);
        });
        OLLAMA.pulls(THE_NAMING_MODEL, new StubOllama.Streamed(StubOllama.lines(SUCCESS), false));

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim("Ollama was asked to pull the naming model once, and nothing else",
                () -> assertThat(OLLAMA.requestsTo("/api/pull"))
                        .extracting(StubOllama.Request::body)
                        .singleElement()
                        .satisfies(body -> assertThat(body).contains("\"" + THE_NAMING_MODEL + "\"")));
        claim("the pull ended once every chunk this invocation embedded had been embedded, so after the scoring, and"
                        + " before any topic was asked for its name",
                () -> assertThat(List.of(embeddingCallsWhenPulled, namingCallsWhenPulled))
                        .containsExactly(EmbeddingScriptedBeans.embeddingCallsMade(), 0));
        claim("and the topic was then named by the model pulled",
                () -> assertThat(GenerationScriptedBeans.namingModelsAsked()).containsExactly(THE_NAMING_MODEL));
    }

    @Test
    @Story("The naming model is pulled at the step that needs it")
    @DisplayName("A naming model Ollama cannot pull stops the arrangement step in one line, with no topic named and no arrangement recorded")
    void aPullThatFailsStopsTheArrangementStep(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        aCorpusUpToTheArrangement(root, seeds);
        EmbeddingScriptedBeans.hasNeverPulled(THE_NAMING_MODEL);
        OLLAMA.pulls(THE_NAMING_MODEL, new StubOllama.Streamed(
                StubOllama.lines("{\"status\":\"pulling manifest\"}", "{\"error\":\"" + NO_SUCH_MODEL + "\"}"), false));
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        claim("the invocation ends non-zero, because it was stopped", () -> assertThat(cli.getExitCode()).isNotZero());
        claim("exactly one line names the arrangement step and quotes Ollama's refusal to pull the naming model",
                () -> assertThat(said.lines()
                                .filter(line -> line.contains("Ollama could not pull " + THE_NAMING_MODEL + " ("
                                        + NO_SUCH_MODEL + ")")))
                        .singleElement()
                        .satisfies(line -> assertThat(line).contains("the arrangement step")));
        claim("the scoring before it was done in this invocation",
                () -> assertThat(runsOver(root, "embedding-scoring")).hasSize(1));
        claim("no topic was asked for its name", () -> assertThat(GenerationScriptedBeans.namingCallsMade()).isZero());
        claim("and no arrangement was recorded", () -> assertThat(runsOver(root, "arrangement")).isEmpty());
    }

    /** One corpus document and one seed, {@link #THE_SEED}, with every gate before the arrangement's approval open. */
    private void aCorpusUpToTheArrangement(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve(THE_SEED), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }

    /** Each line of {@code said} logged for a topic's name: its level, then its words. */
    private static List<String[]> nameLinesIn(String said) {
        return said.lines()
                .map(A_NAME_LINE::matcher)
                .filter(Matcher::matches)
                .map(matched -> new String[] {matched.group(1), matched.group(2)})
                .toList();
    }

    private List<String> runsOver(Path root, String stage) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ?",
                String.class,
                stage,
                Walk.canonicalRoot(root).toString());
    }
}
