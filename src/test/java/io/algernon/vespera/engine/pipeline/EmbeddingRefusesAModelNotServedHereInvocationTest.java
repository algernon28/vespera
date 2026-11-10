package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.profile.Profile;
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
import java.util.Collections;
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
 * Stage 5 embeds nothing under an embedding model that is not served on this machine (ADR-202, #431).
 *
 * <p>Embedding scoring is the one place stage 5 sends text to Ollama: every chunk of every survivor and
 * every usable seed goes to the embedding model the profile names. That name is free text, and a
 * loopback address does not keep a chunk here, since Ollama forwards a cloud model's requests to
 * ollama.com. So before the scoring run is minted and before a chunk is sent, the embedding model passes
 * the check the labeller already makes (ADR-197 section 6): a tag that ends in {@code cloud} is refused
 * without asking Ollama anything, a model {@code /api/show} reports as remote is refused, a model Ollama
 * has never pulled is refused as one it does not serve, and a model Ollama cannot answer about is
 * refused rather than assumed local. A refusal is exactly one line at error level naming the stage, the
 * embedding model and the reason; the invocation ends non-zero, and no scoring run is minted. A stage
 * whose gate is shut asks nothing at all.
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

    /** A line in the console's pattern, {@code HH:mm:ss.SSS LEVEL ...}, which a closing line is no longer (ADR-364 section 4). */
    private static final java.util.regex.Pattern IN_THE_CONSOLES_PATTERN =
            java.util.regex.Pattern.compile("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} .*");

    /** What the refusal line calls this stage, so an operator reading the log knows which one stopped. */
    private static final String THE_STAGE = "stage 5's scoring step";

    /** A tag of the shape Ollama gives a cloud model. */
    private static final String A_CLOUD_TAGGED_EMBEDDING_MODEL = "a-comparing-model:0.6b-cloud";

    /** A name that says nothing about where it runs, scripted as one Ollama reports as remote. */
    private static final String AN_EMBEDDING_MODEL_OLLAMA_FORWARDS = "an-embedder-made-from-a-cloud-one:0.6b";

    /** A name Ollama's {@code /api/show} is scripted to fail on. */
    private static final String AN_EMBEDDING_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT = "an-embedder-nobody-can-place:0.6b";

    /** A name Ollama has never pulled, as a mistyped {@code embeddingModel} is. */
    private static final String AN_EMBEDDING_MODEL_OLLAMA_HAS_NEVER_PULLED = "an-embedder-nobody-pulled:0.6b";

    /** A name Ollama reports as having its weights on this machine: the one every gate is opened with. */
    private static final String AN_EMBEDDING_MODEL_SERVED_HERE = EveryGateOpen.EMBEDDING_MODEL;

    /** A name configured where stage 5's gates are shut, so it must never be asked about. */
    private static final String AN_EMBEDDING_MODEL_BEHIND_A_SHUT_GATE = "an-embedder-behind-a-shut-gate:0.6b";

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
        int beforeErr = output.getErr().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);
        String saidOnError = output.getErr().substring(beforeErr);

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
                () -> oneErrorLineSays(said, saidOnError, embeddingModel, reason));
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
     * Each way a model is not served here: how Ollama is scripted to answer about it, its name, what the
     * refusal says, and whether Ollama is asked at all.
     */
    static Stream<Arguments> modelsNotServedHere() {
        Consumer<String> leftAlone = model -> {};
        Consumer<String> forwarding = EmbeddingScriptedBeans::forwardsToAHostedService;
        Consumer<String> unanswered = EmbeddingScriptedBeans::cannotSayWhereItRuns;
        Consumer<String> neverPulled = EmbeddingScriptedBeans::hasNeverPulled;
        return Stream.of(
                Arguments.of(
                        Named.of("a model whose tag ends in cloud, refused without asking Ollama", leftAlone),
                        A_CLOUD_TAGGED_EMBEDDING_MODEL,
                        A_CLOUD_MODEL,
                        false),
                Arguments.of(
                        Named.of("a model Ollama reports as remote, whatever its name", forwarding),
                        AN_EMBEDDING_MODEL_OLLAMA_FORWARDS,
                        REPORTED_AS_REMOTE,
                        true),
                Arguments.of(
                        Named.of("a model Ollama cannot answer about, refused rather than assumed served here", unanswered),
                        AN_EMBEDDING_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT,
                        NOT_ESTABLISHED,
                        true),
                Arguments.of(
                        Named.of("a model Ollama has never pulled, refused as one it does not serve", neverPulled),
                        AN_EMBEDDING_MODEL_OLLAMA_HAS_NEVER_PULLED,
                        SERVES_NO_MODEL_NAMED,
                        true));
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

    @Test
    @Story("No chunk leaves this machine through Ollama")
    @DisplayName("An embedding model behind a shut stage 5 gate is not asked about")
    void anEmbeddingModelBehindAShutGateIsNotAskedAbout(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(
                        EveryGateOpen.BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(AN_EMBEDDING_MODEL_BEHIND_A_SHUT_GATE, "set by this test, with no seed folder")
                .build());

        cli.run("run", root.toString());

        claim(
                "Ollama was never asked about anything: the embedding model " + AN_EMBEDDING_MODEL_BEHIND_A_SHUT_GATE
                        + " is named, but with no seed folder stage 5's gate is shut and embedding scoring"
                        + " sends nothing, so the check runs after its gates and an operator is not asked to"
                        + " start Ollama to be told a gate is shut",
                () -> assertThat(EmbeddingScriptedBeans.shownModels()).isEmpty());
        claim(
                "and no chunk reached the embedding model",
                () -> assertThat(EmbeddingScriptedBeans.embeddingCallsMade()).isZero());
    }

    /**
     * Exactly one line names {@code model} and says {@code reason}, it is at error level, it names the
     * stage, and nothing printed a stack trace.
     */
    private static void oneErrorLineSays(String said, String saidOnError, String model, String reason) {
        List<String> lines = said.lines()
                .filter(line -> line.contains(model) && line.contains(reason))
                .toList();
        assertThat(lines)
                .as("the lines naming %s and saying \"%s\"", model, reason)
                .hasSize(1);
        assertThat(lines.getFirst())
                .as("the one line naming %s, on standard error as its words alone", model)
                .doesNotMatch(IN_THE_CONSOLES_PATTERN)
                .isIn(saidOnError.lines().toList())
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

    /** Every embedding-scoring run over this method's own corpus. */
    private List<String> scoringRunsOver(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ?",
                String.class,
                "embedding-scoring",
                Walk.canonicalRoot(root).toString());
    }
}
