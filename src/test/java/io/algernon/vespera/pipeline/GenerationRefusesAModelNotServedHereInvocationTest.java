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
 * Stage 6b sends nothing to a generation model that is not served on this machine (ADR-202, #431).
 *
 * <p>A loopback address does not keep a cluster's text here: Ollama serves a cloud model through the
 * local daemon and forwards each request to ollama.com. So before stage 6b mints its run or puts a
 * single question, the generation model it resolved passes the check the labeller already makes
 * (ADR-197 section 6): a tag that ends in {@code cloud} is refused without asking Ollama anything, a
 * model {@code /api/show} reports as remote is refused, and a model Ollama cannot answer about is
 * refused rather than assumed local. A refusal is one line naming the model, the invocation ends
 * non-zero, and no run is minted.
 *
 * <p>The serving engine here is {@link EmbeddingScriptedBeans}' client, scripted by name, and the chat
 * model is {@link GenerationScriptedBeans}' double, which counts every call. No test reaches a model or
 * a daemon.
 *
 * <p><b>A class of its own, for the working directory</b>, for {@link GenerationIdentityInvocationTest}'s
 * reason: the database outlives each method, so every claim is scoped to the walk of that method's own
 * corpus, and every script is dropped before and after each method.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Synthesis")
@Feature("Only a model served on this machine is sent anything")
@Issue("431")
@Link(name = "ADR-202", url = Adr.GENERATION_AND_EMBEDDING_REFUSE_A_MODEL_NOT_SERVED_HERE, type = "adr")
@Link(name = "ADR-197", url = Adr.A_LOCAL_MODEL_LABELS_AND_THE_FLOOR_IS_A_RULE, type = "adr")
@Link(name = "ADR-114", url = Adr.THE_GENERATION_MODEL_IS_CONFIGURATION_WITH_A_DEFAULT, type = "adr")
class GenerationRefusesAModelNotServedHereInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the way every other invocation test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model this fixture names, so gate 3 and everything behind it open. */
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    /** A tag Ollama gives a cloud model: served by the local daemon, answered by ollama.com. */
    private static final String A_CLOUD_TAGGED_GENERATION_MODEL = "gpt-oss:120b-cloud";

    /** A name that says nothing about where it runs, scripted as one Ollama reports as remote. */
    private static final String A_GENERATION_MODEL_OLLAMA_FORWARDS = "a-model-made-from-a-cloud-one:8b";

    /** A name Ollama's {@code /api/show} is scripted to fail on. */
    private static final String A_GENERATION_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT = "a-model-nobody-can-place:8b";

    /** A name Ollama reports as having its weights on this machine. */
    private static final String A_GENERATION_MODEL_SERVED_HERE = "a-model-served-here:8b";

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
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @Test
    @Story("No cluster's text leaves this machine through Ollama")
    @DisplayName("A generation model whose tag ends in cloud is refused before any question is put, without asking Ollama")
    void aCloudTaggedGenerationModelIsRefusedBeforeAnyQuestion(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        anApprovedArrangementToWriteUnder(root, seeds, A_CLOUD_TAGGED_GENERATION_MODEL);
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        claim("the invocation ends non-zero, because it was stopped", () -> assertThat(cli.getExitCode())
                .isNotZero());
        claim(
                "no question reached the generation model -- neither a counting call nor a call for the"
                        + " writing -- because a cloud model is answered by a hosted service, and the"
                        + " first question would already carry a cluster's text there",
                () -> {
                    assertThat(GenerationScriptedBeans.countingCallsMade()).isZero();
                    assertThat(GenerationScriptedBeans.callsMade()).isZero();
                });
        claim(
                "one line says the generation model " + A_CLOUD_TAGGED_GENERATION_MODEL + " is a cloud"
                        + " model, and no stack trace stands in its place",
                () -> {
                    assertThat(linesSaying(said, A_CLOUD_TAGGED_GENERATION_MODEL + " is a cloud model"))
                            .isNotEmpty();
                    assertThat(said).doesNotContain(A_STACK_FRAME);
                });
        claim(
                "Ollama was not asked about it: the tag alone settles it, so the refusal needs no answer"
                        + " from anything",
                () -> assertThat(EmbeddingScriptedBeans.shownModels())
                        .doesNotContain(A_CLOUD_TAGGED_GENERATION_MODEL));
        claim(
                "no generation run was minted, because a run that sent nothing would read as one that"
                        + " found nothing to write",
                () -> assertThat(generationRunsOver(root)).isEmpty());
    }

    @Test
    @Story("No cluster's text leaves this machine through Ollama")
    @DisplayName("A generation model Ollama reports as remote is refused before any question is put, whatever its name")
    void aGenerationModelReportedAsRemoteIsRefusedBeforeAnyQuestion(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        anApprovedArrangementToWriteUnder(root, seeds, A_GENERATION_MODEL_OLLAMA_FORWARDS);
        EmbeddingScriptedBeans.forwardsToAHostedService(A_GENERATION_MODEL_OLLAMA_FORWARDS);
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        claim("the invocation ends non-zero, because it was stopped", () -> assertThat(cli.getExitCode())
                .isNotZero());
        claim(
                "no question reached the generation model: a model created from a cloud one carries no"
                        + " cloud tag, and Ollama's own report is what says it is forwarded",
                () -> {
                    assertThat(GenerationScriptedBeans.countingCallsMade()).isZero();
                    assertThat(GenerationScriptedBeans.callsMade()).isZero();
                });
        claim(
                "one line names the generation model " + A_GENERATION_MODEL_OLLAMA_FORWARDS + " and says"
                        + " Ollama reports it as remote, with no stack trace",
                () -> {
                    assertThat(linesSaying(said, A_GENERATION_MODEL_OLLAMA_FORWARDS))
                            .anySatisfy(line -> assertThat(line).contains("as remote"));
                    assertThat(said).doesNotContain(A_STACK_FRAME);
                });
        claim("no generation run was minted", () -> assertThat(generationRunsOver(root)).isEmpty());
    }

    @Test
    @Story("No cluster's text leaves this machine through Ollama")
    @DisplayName("A generation model Ollama cannot answer about is refused, not assumed to be served here")
    void aGenerationModelOllamaCannotAnswerAboutIsRefused(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        anApprovedArrangementToWriteUnder(root, seeds, A_GENERATION_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT);
        EmbeddingScriptedBeans.cannotSayWhereItRuns(A_GENERATION_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT);
        int before = output.getAll().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);

        claim("the invocation ends non-zero, because it was stopped", () -> assertThat(cli.getExitCode())
                .isNotZero());
        claim(
                "no question reached the generation model: a model whose remoteness cannot be"
                        + " established is refused, as the labeller refuses one (ADR-197 section 6)",
                () -> {
                    assertThat(GenerationScriptedBeans.countingCallsMade()).isZero();
                    assertThat(GenerationScriptedBeans.callsMade()).isZero();
                });
        claim(
                "one line names the generation model " + A_GENERATION_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT
                        + " and says it could not be established where it runs, with no stack trace",
                () -> {
                    assertThat(linesSaying(said, A_GENERATION_MODEL_OLLAMA_CANNOT_ANSWER_ABOUT))
                            .anySatisfy(line -> assertThat(line).contains("could not be established"));
                    assertThat(said).doesNotContain(A_STACK_FRAME);
                });
        claim("no generation run was minted", () -> assertThat(generationRunsOver(root)).isEmpty());
    }

    @Test
    @Story("No cluster's text leaves this machine through Ollama")
    @DisplayName("A generation model served here is asked about once, and then writes as before")
    void aGenerationModelServedHereIsCheckedOnceAndWrites(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedArrangementToWriteUnder(root, seeds, A_GENERATION_MODEL_SERVED_HERE);

        cli.run("run", root.toString());

        claim(
                "the generation model was put questions, so the check stops only what it exists to stop",
                () -> assertThat(GenerationScriptedBeans.callsMade()).isPositive());
        claim("a generation run was minted", () -> assertThat(generationRunsOver(root)).isNotEmpty());
        claim(
                "Ollama was asked where it runs exactly once in the invocation, at the start of stage"
                        + " 6b, rather than once per call: the generation model cannot change while"
                        + " the stage is running, and the stage sends every call under the name it"
                        + " checked",
                () -> assertThat(Collections.frequency(
                                EmbeddingScriptedBeans.shownModels(), A_GENERATION_MODEL_SERVED_HERE))
                        .isEqualTo(1));
    }

    /**
     * One corpus document and one seed, run once so an arrangement exists, then that arrangement
     * approved and {@code generationModel} named, so the next invocation reaches stage 6b.
     */
    private void anApprovedArrangementToWriteUnder(Path root, Path seeds, String generationModel)
            throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
        cli.run("run", root.toString());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(ArrangementGate.shortNameOf(theLatestArrangement(root)), "read by this test")
                .generationModel(generationModel, "set by this test")
                .build());
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    private static List<String> linesSaying(String said, String text) {
        return said.lines().filter(line -> line.contains(text)).toList();
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

    /** Every generation run over this method's own corpus. */
    private List<String> generationRunsOver(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ?",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
    }
}
