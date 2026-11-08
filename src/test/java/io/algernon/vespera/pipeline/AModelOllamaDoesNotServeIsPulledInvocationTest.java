package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
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

    /** An embedding model Ollama is scripted as not serving until it is pulled. */
    private static final String AN_EMBEDDING_MODEL_NOT_SERVED = "hf.co/An-Owner/A-Comparing-Model-GGUF:Q8_0";

    /** What stage 5's scoring step calls itself in its stop line. */
    private static final String THE_SCORING_STEP = "stage 5's scoring step";

    /** What Ollama answers with for a name its registry does not have. */
    private static final String NO_SUCH_MODEL = "pull model manifest: file does not exist";

    /** The one line of Java's stack trace format that a stop must not print. */
    private static final String A_STACK_FRAME = "\tat ";

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

    /** One corpus document and one seed, with every gate up to embedding scoring open. */
    private void aCorpusScoredUnder(Path root, Path seeds, String embeddingModel) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(EveryGateOpen.BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(embeddingModel, "set by this test, so gate 3 is open")
                .build());
    }

    private List<String> scoringRunsOver(Path root) {
        return runsOver(root, "embedding-scoring");
    }

    private List<String> runsOver(Path root, String stage) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ?",
                String.class,
                stage,
                Walk.canonicalRoot(root).toString());
    }
}
