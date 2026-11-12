package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.profile.ProfileFixture;
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
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 5 embeds nothing under an embedding model that is not served on this machine (ADR-202, #431), and a model
 * the invocation names that is not local is refused at its start (ADR-375, #725).
 *
 * <p>Embedding scoring is the one place stage 5 sends text to Ollama: every chunk of every survivor and
 * every usable seed goes to the embedding model the profile names. That name is free text, and a
 * loopback address does not keep a chunk here, since Ollama forwards a cloud model's requests to
 * ollama.com. So {@code vespera run} refuses, at its start and before anything is walked, a model whose tag ends in
 * {@code cloud}, without asking Ollama anything, and a model {@code /api/show} reports as remote: one line on
 * standard error, exit code 1. At the scoring step, before the scoring run is minted and before a chunk is sent,
 * a model Ollama has never pulled is refused as one it does not serve, in exactly one line at error level naming
 * the stage, the embedding model and the reason; the invocation ends non-zero, and no scoring run is minted. A
 * model Ollama cannot answer about is taken as local, at the start and at the step, and its chunks are embedded.
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
@Link(name = "ADR-375", url = Adr.AN_UNANSWERED_CHECK_TAKES_A_MODEL_AS_LOCAL_AND_A_CLOUD_MODEL_IS_REFUSED_AT_THE_START, type = "adr")
class EmbeddingRefusesAModelNotServedHereInvocationTest {

    /** What the refusal line calls this stage, so an operator reading the log knows which one stopped. */
    private static final String THE_STAGE = "stage 5's scoring step";

    /** A tag of the shape Ollama gives a cloud model. */
    private static final String A_CLOUD_TAGGED_EMBEDDING_MODEL = "a-comparing-model:0.6b-cloud";

    /** A name Ollama has never pulled, as a mistyped {@code embeddingModel} is. */
    private static final String AN_EMBEDDING_MODEL_OLLAMA_HAS_NEVER_PULLED = "an-embedder-nobody-pulled:0.6b";

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("modelsNotServedHere")
    @Story("No chunk leaves this machine through Ollama")
    @DisplayName("An embedding model not served here is refused before any chunk is sent, on one error line saying why")
    void anEmbeddingModelNotServedHereIsRefusedBeforeAnyChunk(
            Consumer<String> howOllamaAnswers,
            String embeddingModel,
            String reason,
            boolean ollamaIsAsked,
            @TempDir Path root,
            @TempDir Path seeds,
            CapturedOutput output)
            throws IOException {
        howOllamaAnswers.accept(embeddingModel);
        aCorpusScoredUnder(root, seeds, embeddingModel);
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        CharSequence[] otherReasons =
                EVERY_REASON.stream().filter(other -> !other.equals(reason)).toArray(CharSequence[]::new);

        claim("the invocation ends non-zero, because it was stopped", () -> assertThat(cli.getExitCode())
                .isNotZero());
        claim(
                "no chunk reached the embedding model, because the first chunk sent to a model not served"
                        + " here would already have left this machine",
                () -> assertThat(EmbeddingScriptedBeans.embeddingCallsMade()).isZero());
        claim(
                "exactly one line, at error level, names " + THE_STAGE + " and the embedding model "
                        + embeddingModel + " and says \"" + reason + "\", and no stack trace stands in its place",
                () -> oneErrorLineSays(said, embeddingModel, reason));
        claim(
                "and no line naming it gives another of the reasons a model is refused: an operator who"
                        + " mistyped embeddingModel is told the name is not served here, and not that its"
                        + " remoteness is in doubt",
                () -> assertThat(linesSaying(said, embeddingModel))
                        .allSatisfy(line -> assertThat(line).doesNotContain(otherReasons)));
        claim(
                ollamaIsAsked
                        ? "Ollama was asked where the model runs, since its name alone does not settle it"
                        : "Ollama was not asked about it: the tag alone settles it",
                () -> assertThat(EmbeddingScriptedBeans.shownModels().contains(embeddingModel))
                        .isEqualTo(ollamaIsAsked));
        claim(
                "no embedding-scoring run was minted, because a run that embedded nothing would read as"
                        + " one that found nothing to score",
                () -> assertThat(scoringRunsOver(root)).isEmpty());
    }

    /** What the refusal line says of a cloud-tagged model. */
    private static final String A_CLOUD_MODEL = " is a cloud model";

    /** What it says of a model Ollama reports as remote. */
    private static final String REPORTED_AS_REMOTE = "as remote";

    /** What it says of a model Ollama cannot answer about. */
    private static final String NOT_ESTABLISHED = "could not be established";

    /** What it says of a model Ollama has never pulled. */
    private static final String SERVES_NO_MODEL_NAMED = "serves no model named " + AN_EMBEDDING_MODEL_OLLAMA_HAS_NEVER_PULLED;

    /** The four reasons, so a line can be shown to give its own and none of the others. */
    private static final List<String> EVERY_REASON =
            List.of(A_CLOUD_MODEL, REPORTED_AS_REMOTE, NOT_ESTABLISHED, SERVES_NO_MODEL_NAMED);

    /**
     * Each way a model is not served here that the scoring step meets: how Ollama is scripted to answer about it, its
     * name, what the refusal says, and whether Ollama is asked at all. A cloud-tagged model and one Ollama reports as
     * remote are refused at the start instead, and never reach the step (ADR-375 §2).
     */
    static Stream<Arguments> modelsNotServedHere() {
        Consumer<String> neverPulled = EmbeddingScriptedBeans::hasNeverPulled;
        return Stream.of(
                Arguments.of(
                        Named.of("a model Ollama has never pulled, refused as one it does not serve", neverPulled),
                        AN_EMBEDDING_MODEL_OLLAMA_HAS_NEVER_PULLED,
                        SERVES_NO_MODEL_NAMED,
                        true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("modelsNotLocal")
    @Story("No chunk leaves this machine through Ollama")
    @DisplayName("An embedding model that is not local is refused at the start of the invocation, before anything is walked, in one line")
    void anEmbeddingModelNotLocalIsRefusedAtTheStartBeforeTheWalk(
            Consumer<String> howOllamaAnswers,
            String embeddingModel,
            String reason,
            boolean ollamaIsAsked,
            @TempDir Path root,
            @TempDir Path seeds,
            CapturedOutput output)
            throws IOException {
        howOllamaAnswers.accept(embeddingModel);
        aCorpusScoredUnder(root, seeds, embeddingModel);
        int before = output.getErr().length();

        cli.run("run", root.toString());
        String onStandardError = output.getErr().substring(before);
        String theLine = REFUSED_AT_THE_START.formatted("embedding model", embeddingModel, reason.formatted(embeddingModel));

        claim(
                "the invocation ends with exit code " + REFUSED + ", the code of every refusal at its start",
                () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim(
                "standard error carries the refusal once, whole: \"" + theLine + "\"",
                () -> assertThat(onStandardError.lines().filter(theLine::equals)).hasSize(1));
        claim(
                "nothing was walked, so no stage ran before the refusal",
                () -> assertThat(walksOf(root)).isZero());
        claim(
                ollamaIsAsked
                        ? "Ollama was asked where the model runs, since its name alone does not settle it"
                        : "Ollama was not asked about it: the tag alone settles it",
                () -> assertThat(EmbeddingScriptedBeans.shownModels().contains(embeddingModel))
                        .isEqualTo(ollamaIsAsked));
    }

    /**
     * Each model the start refuses: how Ollama is scripted to answer about it, its name, the reason the line gives,
     * which names the model where it holds {@code %s}, and whether Ollama is asked at all.
     */
    static Stream<Arguments> modelsNotLocal() {
        Consumer<String> leftAlone = model -> {};
        Consumer<String> forwarded = EmbeddingScriptedBeans::forwardsToAHostedService;
        return Stream.of(
                Arguments.of(
                        Named.of("a model whose tag ends in cloud, refused without asking Ollama", leftAlone),
                        A_CLOUD_TAGGED_EMBEDDING_MODEL,
                        IS_A_CLOUD_MODEL,
                        false),
                Arguments.of(
                        Named.of("a model Ollama reports as remote", forwarded),
                        AN_EMBEDDING_MODEL_OLLAMA_FORWARDS,
                        IS_REPORTED_AS_REMOTE,
                        true));
    }

    @Test
    @Story("A model whose check cannot be completed is taken as local")
    @DisplayName("An embedding model Ollama cannot answer about is refused neither at the start nor at the scoring step, and its chunks are embedded")
    void anEmbeddingModelOllamaCannotAnswerAboutIsTakenAsLocal(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        EmbeddingScriptedBeans.cannotSayWhereItRuns(AN_EMBEDDING_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT);
        aCorpusScoredUnder(root, seeds, AN_EMBEDDING_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT);
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        claim(
                "Ollama was asked about the model, and could not answer",
                () -> assertThat(EmbeddingScriptedBeans.shownModels()).contains(AN_EMBEDDING_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT));
        claim("the invocation ends with exit code 0: nothing stopped it", () -> assertThat(cli.getExitCode())
                .isZero());
        claim(
                "one embedding-scoring run was minted over this corpus, and chunks were sent to the embedding model",
                () -> {
                    assertThat(scoringRunsOver(root)).hasSize(1);
                    assertThat(EmbeddingScriptedBeans.embeddingCallsMade()).isPositive();
                });
        claim(
                "and nothing was printed at error level",
                () -> assertThat(said.lines()).noneMatch(line -> line.matches("^\\S+ ERROR .*")));
    }

    /** The line a refusal at the start prints: the role of the model, its name, and the reason. */
    private static final String REFUSED_AT_THE_START =
            "vespera run stopped at its start: the %s %s was refused, so nothing was walked and no run was minted -- %s";

    /** The exit code of every refusal at the start of {@code vespera run}. */
    private static final int REFUSED = 1;

    /** The reason given for a cloud-tagged model, as the check every call to Ollama passes words it. */
    private static final String IS_A_CLOUD_MODEL =
            "the model %s is a cloud model: Ollama forwards it to a hosted service, and no document's text may be sent there";

    /** The reason given for a model Ollama reports as remote, as that check words it. */
    private static final String IS_REPORTED_AS_REMOTE = "Ollama reports the model %s as remote, so it is forwarded to a"
            + " hosted service, and no document's text may be sent there";

    /** A name of no particular shape, which Ollama is scripted to report as forwarded to a hosted service. */
    private static final String AN_EMBEDDING_MODEL_OLLAMA_FORWARDS = "a-forwarded-embedder:0.6b";

    /** A name Ollama is scripted to be unable to answer {@code /api/show} about. */
    private static final String AN_EMBEDDING_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT = "an-embedder-of-a-busy-ollama:0.6b";

    /**
     * Exactly one line names {@code model} and says {@code reason}, it is at error level, it names the
     * stage, and nothing printed a stack trace.
     */
    private static void oneErrorLineSays(String said, String model, String reason) {
        List<String> lines = said.lines()
                .filter(line -> line.contains(model) && line.contains(reason))
                .toList();
        assertThat(lines)
                .as("the lines naming %s and saying \"%s\"", model, reason)
                .hasSize(1);
        assertThat(lines.getFirst())
                .as("the one line naming %s", model)
                .matches("^\\S+ ERROR .*")
                .contains(THE_STAGE);
        assertThat(said).as("what the invocation printed").doesNotContain(A_STACK_FRAME);
    }

    /** One corpus document and one seed, with every gate up to embedding scoring open. */
    private void aCorpusScoredUnder(Path root, Path seeds, String embeddingModel) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .embeddingModel(embeddingModel, "set by this test, so gate 3 is open")
                .build());
    }

    private static List<String> linesSaying(String said, String text) {
        return said.lines().filter(line -> line.contains(text)).toList();
    }

    /** How many walks were recorded over this method's own corpus. */
    private int walksOf(Path root) {
        Integer walks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk WHERE root = ?", Integer.class, Walk.canonicalRoot(root).toString());
        return walks == null ? 0 : walks;
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
