package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code vespera run} pulls into Ollama, at the step that needs it, a model Ollama does not serve, and carries on
 * (ADR-329, #669): the embedding model at stage 5's scoring step and the generation model at the generation step.
 *
 * <p>Pulling is on here, which the test profile turns off everywhere else. Whether Ollama serves a model is answered
 * by {@link EmbeddingScriptedBeans}' client, scripted by name, and {@code /api/pull} by a {@link StubOllama} at
 * {@code spring.ai.ollama.base-url}, whose pull, once it reaches {@code success}, tells that client the model is now
 * served. Neither reaches a model or a daemon.
 *
 * <p><b>A class of its own, for the working directory and the property</b>, for {@link
 * GenerationIdentityInvocationTest}'s reason: every claim is scoped to the walk of that method's own corpus, and every
 * script is dropped before and after each method.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Relevance")
@Feature("A model Ollama does not serve is pulled")
@Issue("669")
@Link(name = "ADR-329", url = Adr.A_MODEL_OLLAMA_DOES_NOT_SERVE_IS_PULLED_AT_THE_STEP_THAT_NEEDS_IT, type = "adr")
@Link(name = "ADR-202", url = Adr.GENERATION_AND_EMBEDDING_REFUSE_A_MODEL_NOT_SERVED_HERE, type = "adr")
class AModelOllamaDoesNotServeIsPulledInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the way every other invocation test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** An embedding model Ollama is scripted as not serving until it is pulled. */
    private static final String AN_EMBEDDING_MODEL_NOT_SERVED = "hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q8_0";

    /** An embedding model Ollama serves. */
    private static final String AN_EMBEDDING_MODEL_SERVED = "qwen3-embedding:0.6b";

    /** A generation model Ollama is scripted as not serving until it is pulled. */
    private static final String A_GENERATION_MODEL_NOT_SERVED = "a-model-nobody-pulled-yet:8b";

    /** The digest Ollama reports once the embedding model has been pulled, other than the fixture's own. */
    private static final String THE_PULLED_DIGEST = "5ca1ab1e00000000000000000000000000000000000000000000000000000";

    /** What stage 5's scoring step calls itself in its stop line. */
    private static final String THE_SCORING_STEP = "stage 5's scoring step";

    /** What Ollama answers with for a name its registry does not have. */
    private static final String NO_SUCH_MODEL = "pull model manifest: file does not exist";

    /** The last counter line of a pull of the one layer of a hundred megabytes the stub sends. */
    private static final String ALL_OF_IT = ": 100 of 100 (100%)";

    /** The one line of Java's stack trace format that a stop must not print. */
    private static final String A_STACK_FRAME = "\tat ";

    private static final String LAYER = "{\"status\":\"pulling 2a0c1f1e3b4d\",\"digest\":\"sha256:2a0c1f1e3b4d\","
            + "\"total\":100000000,\"completed\":%d}";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final StubOllama OLLAMA = StubOllama.started();

    /** How many calls had reached the embedding model when the pull ended, or -1 before it has. */
    private static volatile int embeddingCallsWhenPulled = -1;

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void pullingOnAgainstTheStub(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
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
    }

    @AfterAll
    static void stopTheStub() {
        OLLAMA.close();
    }

    @Test
    @Story("A model Ollama does not serve is pulled before the step sends it anything")
    @DisplayName("An embedding model Ollama does not serve is pulled at scoring before any chunk is sent, and the scoring run names what Ollama reports after the pull")
    void anEmbeddingModelNotServedIsPulledAndScored(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        aCorpusScoredUnder(root, seeds, AN_EMBEDDING_MODEL_NOT_SERVED);
        EmbeddingScriptedBeans.hasNeverPulled(AN_EMBEDDING_MODEL_NOT_SERVED);
        OLLAMA.pulls(AN_EMBEDDING_MODEL_NOT_SERVED, aWholePull());
        OLLAMA.onPulled(name -> {
            embeddingCallsWhenPulled = EmbeddingScriptedBeans.embeddingCallsMade();
            EmbeddingScriptedBeans.hasNowPulled(name);
            EmbeddingScriptedBeans.hasPulledAgainAs(THE_PULLED_DIGEST);
        });
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        claim(
                "the model was pulled once, asked for by its name exactly",
                () -> assertThat(OLLAMA.requestsTo("/api/pull")).singleElement().satisfies(request -> assertThat(
                                JSON.readTree(request.body()).path("model").asString())
                        .isEqualTo(AN_EMBEDDING_MODEL_NOT_SERVED)));
        claim(
                "the pull ended before any chunk reached the embedding model, and chunks reached it after",
                () -> {
                    assertThat(embeddingCallsWhenPulled).isZero();
                    assertThat(EmbeddingScriptedBeans.embeddingCallsMade()).isPositive();
                });
        claim(
                "one scoring run was minted, and its settings name the digest Ollama reported once the pull had ended:"
                        + " the identity is read after the pull, as it is for a model pulled by hand",
                () -> assertThat(scoringRunsOver(root)).singleElement().satisfies(run -> assertThat(settingsOf(run))
                        .contains("\"embeddingModelDigest\":\"" + THE_PULLED_DIGEST + "\"")));
        claim(
                "the invocation said it was pulling the model, and how far the pull had got, as the step's progress",
                () -> assertThat(said.lines().toList())
                        .anySatisfy(line -> assertThat(line).endsWith("Stage 5c (embedding scoring) Ollama serves no"
                                + " model named " + AN_EMBEDDING_MODEL_NOT_SERVED + "; pulling it"))
                        .anySatisfy(line -> assertThat(line)
                                .endsWith("Stage 5c (embedding scoring, megabytes pulled)" + ALL_OF_IT)));
    }

    @Test
    @Story("A model Ollama serves is never pulled")
    @DisplayName("An embedding model Ollama serves is not pulled, and scoring goes on")
    void anEmbeddingModelServedIsNotPulled(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpusScoredUnder(root, seeds, AN_EMBEDDING_MODEL_SERVED);
        OLLAMA.pulls(AN_EMBEDDING_MODEL_SERVED, aWholePull());

        cli.run("run", root.toString());

        claim("chunks reached the embedding model", () -> assertThat(EmbeddingScriptedBeans.embeddingCallsMade())
                .isPositive());
        claim("a scoring run was minted", () -> assertThat(scoringRunsOver(root)).hasSize(1));
        claim(
                "and no pull was asked for: a model Ollama serves is never pulled again",
                () -> assertThat(OLLAMA.requestsTo("/api/pull")).isEmpty());
    }

    @Test
    @Story("A pull that cannot be made stops the step in one line")
    @DisplayName("A pull Ollama cannot make stops scoring in one line quoting Ollama, with no chunk sent and no run minted")
    void aPullThatFailsStopsTheStepInOneLine(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        aCorpusScoredUnder(root, seeds, AN_EMBEDDING_MODEL_NOT_SERVED);
        EmbeddingScriptedBeans.hasNeverPulled(AN_EMBEDDING_MODEL_NOT_SERVED);
        OLLAMA.pulls(AN_EMBEDDING_MODEL_NOT_SERVED, new StubOllama.Streamed(
                StubOllama.lines("{\"status\":\"pulling manifest\"}", "{\"error\":\"" + NO_SUCH_MODEL + "\"}"), false));
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        claim("the invocation ends non-zero, because it was stopped", () -> assertThat(cli.getExitCode())
                .isNotZero());
        claim(
                "exactly one line at error level names " + THE_SCORING_STEP + ", the model and Ollama's words, and no"
                        + " stack trace stands in its place",
                () -> {
                    List<String> lines = said.lines()
                            .filter(line -> line.contains(AN_EMBEDDING_MODEL_NOT_SERVED) && line.contains(NO_SUCH_MODEL))
                            .toList();
                    assertThat(lines).singleElement().satisfies(line -> assertThat(line)
                            .matches("^\\S+ ERROR .*")
                            .contains(THE_SCORING_STEP)
                            .contains("Ollama could not pull " + AN_EMBEDDING_MODEL_NOT_SERVED + " (" + NO_SUCH_MODEL
                                    + ")"));
                    assertThat(said).doesNotContain(A_STACK_FRAME);
                });
        claim("no chunk reached the embedding model", () -> assertThat(EmbeddingScriptedBeans.embeddingCallsMade())
                .isZero());
        claim("and no scoring run was minted", () -> assertThat(scoringRunsOver(root)).isEmpty());
    }

    @Test
    @Story("A model Ollama does not serve is pulled before the step sends it anything")
    @DisplayName("A generation model Ollama does not serve is pulled at the generation step, and the connecting text is written")
    void aGenerationModelNotServedIsPulledAndWrites(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        anApprovedArrangementToWriteUnder(root, seeds, A_GENERATION_MODEL_NOT_SERVED);
        EmbeddingScriptedBeans.hasNeverPulled(A_GENERATION_MODEL_NOT_SERVED);
        OLLAMA.pulls(A_GENERATION_MODEL_NOT_SERVED, aWholePull());
        OLLAMA.onPulled(EmbeddingScriptedBeans::hasNowPulled);
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        claim(
                "the generation model was pulled once, by its name",
                () -> assertThat(OLLAMA.requestsTo("/api/pull")).singleElement().satisfies(request -> assertThat(
                                JSON.readTree(request.body()).path("model").asString())
                        .isEqualTo(A_GENERATION_MODEL_NOT_SERVED)));
        claim("questions were put to it once it was pulled", () -> assertThat(GenerationScriptedBeans.callsMade())
                .isPositive());
        claim("a generation run was minted", () -> assertThat(generationRunsOver(root)).hasSize(1));
        claim(
                "and the pull's progress was the generation step's",
                () -> assertThat(said.lines().toList()).anySatisfy(line -> assertThat(line)
                        .endsWith("Stage 6b (generation, megabytes pulled)" + ALL_OF_IT)));
    }

    /** A pull of one layer of a hundred megabytes, reported at nought and at the end, then checked and written. */
    private static StubOllama.Streamed aWholePull() {
        return new StubOllama.Streamed(
                StubOllama.lines(
                        "{\"status\":\"pulling manifest\"}",
                        LAYER.formatted(0),
                        LAYER.formatted(100_000_000),
                        "{\"status\":\"verifying sha256 digest\"}",
                        "{\"status\":\"writing manifest\"}",
                        "{\"status\":\"success\"}"),
                false);
    }

    /** One corpus document and one seed, with every gate up to embedding scoring open. */
    private void aCorpusScoredUnder(Path root, Path seeds, String embeddingModel) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(embeddingModel, "set by this test, so gate 3 is open")
                .build());
    }

    /**
     * A corpus scored under an embedding model Ollama serves, run once so an arrangement exists, then that
     * arrangement approved and {@code generationModel} named, so the next invocation reaches the generation step.
     */
    private void anApprovedArrangementToWriteUnder(Path root, Path seeds, String generationModel)
            throws IOException {
        aCorpusScoredUnder(root, seeds, AN_EMBEDDING_MODEL_SERVED);
        cli.run("run", root.toString());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(ArrangementGate.shortNameOf(theLatestArrangement(root)), "read by this test")
                .generationModel(generationModel, "set by this test")
                .build());
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    /** The arrangement the most recent invocation over {@code root} recorded. */
    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString()));
    }

    private List<String> scoringRunsOver(Path root) {
        return runsOver(root, "embedding-scoring");
    }

    private List<String> generationRunsOver(Path root) {
        return runsOver(root, "generation");
    }

    private List<String> runsOver(Path root, String stage) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ?",
                String.class,
                stage,
                Walk.canonicalRoot(root).toString());
    }

    private String settingsOf(String run) {
        return jdbcTemplate.queryForObject("SELECT config_consumed FROM run WHERE id = ?", String.class, run);
    }
}
