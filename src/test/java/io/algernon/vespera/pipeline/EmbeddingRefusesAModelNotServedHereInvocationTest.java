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
import java.util.Collections;
import java.util.List;
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
 * Stage 5 embeds nothing under an embedding model that is not served on this machine (ADR-202, #431).
 *
 * <p>Embedding scoring is the one place stage 5 sends text to Ollama: every chunk of every survivor and
 * every usable seed goes to the embedding model the profile names. That name is free text, and a
 * loopback address does not keep a chunk here, since Ollama forwards a cloud model's requests to
 * ollama.com. So before the scoring run is minted and before a chunk is sent, the embedding model passes
 * the check the labeller already makes (ADR-197 section 6): a tag that ends in {@code cloud} is refused
 * without asking Ollama anything, a model {@code /api/show} reports as remote is refused, and a model
 * Ollama cannot answer about is refused rather than assumed local. A refusal is one line naming the
 * model, the invocation ends non-zero, and no scoring run is minted.
 *
 * <p>The serving engine and the embedding model are {@link EmbeddingScriptedBeans}' doubles, scripted by
 * name and counting their calls. No test reaches a model or a daemon.
 *
 * <p><b>A class of its own, for the working directory</b>, for {@link GenerationIdentityInvocationTest}'s
 * reason: every claim is scoped to the walk of that method's own corpus, and every script is dropped
 * before and after each method.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Relevance")
@Feature("Only a model served on this machine is sent anything")
@Issue("431")
@Link(name = "ADR-202", url = Adr.GENERATION_AND_EMBEDDING_REFUSE_A_MODEL_NOT_SERVED_HERE, type = "adr")
@Link(name = "ADR-197", url = Adr.A_LOCAL_MODEL_LABELS_AND_THE_FLOOR_IS_A_RULE, type = "adr")
class EmbeddingRefusesAModelNotServedHereInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the way every other invocation test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** A tag of the shape Ollama gives a cloud model. */
    private static final String A_CLOUD_TAGGED_EMBEDDING_MODEL = "qwen3-embedding:0.6b-cloud";

    /** A name that says nothing about where it runs, scripted as one Ollama reports as remote. */
    private static final String AN_EMBEDDING_MODEL_OLLAMA_FORWARDS = "an-embedder-made-from-a-cloud-one:0.6b";

    /** A name Ollama's {@code /api/show} is scripted to fail on. */
    private static final String AN_EMBEDDING_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT = "an-embedder-nobody-can-place:0.6b";

    /** A name Ollama reports as having its weights on this machine. */
    private static final String AN_EMBEDDING_MODEL_SERVED_HERE = "qwen3-embedding:0.6b";

    /** The one line of Java's stack trace format that a refusal must not print. */
    private static final String A_STACK_FRAME = "\tat ";

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

    /** Drops every script and count before each test as well as after it, for the class-order reason. */
    @BeforeEach
    @AfterEach
    void everyModelIsServedHereAgain() {
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @Test
    @Story("No chunk leaves this machine through Ollama")
    @DisplayName("An embedding model whose tag ends in cloud is refused before any chunk is sent, without asking Ollama")
    void aCloudTaggedEmbeddingModelIsRefusedBeforeAnyChunk(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        aCorpusScoredUnder(root, seeds, A_CLOUD_TAGGED_EMBEDDING_MODEL);
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        claim("the invocation ends non-zero, because it was stopped", () -> assertThat(cli.getExitCode())
                .isNotZero());
        claim(
                "no chunk reached the embedding model, because a cloud model is answered by a hosted"
                        + " service and the first chunk would already be there",
                () -> assertThat(EmbeddingScriptedBeans.embeddingCallsMade()).isZero());
        claim(
                "one line says the embedding model " + A_CLOUD_TAGGED_EMBEDDING_MODEL + " is a cloud model,"
                        + " and no stack trace stands in its place",
                () -> {
                    assertThat(linesSaying(said, A_CLOUD_TAGGED_EMBEDDING_MODEL + " is a cloud model"))
                            .isNotEmpty();
                    assertThat(said).doesNotContain(A_STACK_FRAME);
                });
        claim(
                "Ollama was not asked about it: the tag alone settles it",
                () -> assertThat(EmbeddingScriptedBeans.shownModels())
                        .doesNotContain(A_CLOUD_TAGGED_EMBEDDING_MODEL));
        claim(
                "no embedding-scoring run was minted, because a run that embedded nothing would read as"
                        + " one that found nothing to score",
                () -> assertThat(scoringRunsOver(root)).isEmpty());
    }

    @Test
    @Story("No chunk leaves this machine through Ollama")
    @DisplayName("An embedding model Ollama reports as remote is refused before any chunk is sent, whatever its name")
    void anEmbeddingModelReportedAsRemoteIsRefusedBeforeAnyChunk(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        EmbeddingScriptedBeans.forwardsToAHostedService(AN_EMBEDDING_MODEL_OLLAMA_FORWARDS);
        aCorpusScoredUnder(root, seeds, AN_EMBEDDING_MODEL_OLLAMA_FORWARDS);
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        claim("the invocation ends non-zero, because it was stopped", () -> assertThat(cli.getExitCode())
                .isNotZero());
        claim(
                "no chunk reached the embedding model: a model created from a cloud one carries no cloud"
                        + " tag, and Ollama's own report is what says it is forwarded",
                () -> assertThat(EmbeddingScriptedBeans.embeddingCallsMade()).isZero());
        claim(
                "one line names the embedding model " + AN_EMBEDDING_MODEL_OLLAMA_FORWARDS + " and says"
                        + " Ollama reports it as remote, with no stack trace",
                () -> {
                    assertThat(linesSaying(said, AN_EMBEDDING_MODEL_OLLAMA_FORWARDS))
                            .anySatisfy(line -> assertThat(line).contains("as remote"));
                    assertThat(said).doesNotContain(A_STACK_FRAME);
                });
        claim("no embedding-scoring run was minted", () -> assertThat(scoringRunsOver(root)).isEmpty());
    }

    @Test
    @Story("No chunk leaves this machine through Ollama")
    @DisplayName("An embedding model Ollama cannot answer about is refused, not assumed to be served here")
    void anEmbeddingModelOllamaCannotAnswerAboutIsRefused(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        EmbeddingScriptedBeans.cannotSayWhereItRuns(AN_EMBEDDING_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT);
        aCorpusScoredUnder(root, seeds, AN_EMBEDDING_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT);
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        claim("the invocation ends non-zero, because it was stopped", () -> assertThat(cli.getExitCode())
                .isNotZero());
        claim(
                "no chunk reached the embedding model: a model whose remoteness cannot be established is"
                        + " refused, as the labeller refuses one (ADR-197 section 6)",
                () -> assertThat(EmbeddingScriptedBeans.embeddingCallsMade()).isZero());
        claim(
                "one line names the embedding model " + AN_EMBEDDING_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT
                        + " and says it could not be established where it runs, with no stack trace",
                () -> {
                    assertThat(linesSaying(said, AN_EMBEDDING_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT))
                            .anySatisfy(line -> assertThat(line).contains("could not be established"));
                    assertThat(said).doesNotContain(A_STACK_FRAME);
                });
        claim("no embedding-scoring run was minted", () -> assertThat(scoringRunsOver(root)).isEmpty());
    }

    @Test
    @Story("No chunk leaves this machine through Ollama")
    @DisplayName("An embedding model served here is asked about once, and then embeds as before")
    void anEmbeddingModelServedHereIsCheckedOnceAndEmbeds(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusScoredUnder(root, seeds, AN_EMBEDDING_MODEL_SERVED_HERE);

        cli.run("run", root.toString());

        claim(
                "chunks reached the embedding model, so the check stops only what it exists to stop",
                () -> assertThat(EmbeddingScriptedBeans.embeddingCallsMade()).isPositive());
        claim("an embedding-scoring run was minted", () -> assertThat(scoringRunsOver(root)).isNotEmpty());
        claim(
                "Ollama was asked where it runs exactly once in the invocation, at the start of"
                        + " embedding scoring, rather than once per chunk: the embedding model cannot"
                        + " change while the stage is running, and every chunk is sent under the name it"
                        + " checked",
                () -> assertThat(Collections.frequency(
                                EmbeddingScriptedBeans.shownModels(), AN_EMBEDDING_MODEL_SERVED_HERE))
                        .isEqualTo(1));
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

    private static List<String> linesSaying(String said, String text) {
        return said.lines().filter(line -> line.contains(text)).toList();
    }

    /** Every embedding-scoring run over this method's own corpus. */
    private List<String> scoringRunsOver(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ?",
                String.class,
                "embedding-scoring",
                Walk.canonicalRoot(root).toString());
    }
}
