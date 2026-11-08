package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
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
 * model {@code /api/show} reports as remote is refused, a model Ollama has never pulled is refused as
 * one it does not serve, and a model Ollama cannot answer about is refused rather than assumed local.
 * A refusal is exactly one line at error level naming the stage, the generation model and the reason;
 * the invocation ends non-zero, and no run is minted. A stage whose gate is shut asks nothing at all.
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

    /** What the refusal line calls this stage, so an operator reading the log knows which one stopped. */
    private static final String THE_STAGE = "the generation step";

    /** A tag Ollama gives a cloud model: served by the local daemon, answered by ollama.com. */
    private static final String A_CLOUD_TAGGED_GENERATION_MODEL = "a-large-model:120b-cloud";

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
                "exactly one line, at error level, names " + THE_STAGE + " and says the generation model "
                        + A_CLOUD_TAGGED_GENERATION_MODEL + " is a cloud model, and no stack trace stands in"
                        + " its place",
                () -> oneErrorLineSays(said, A_CLOUD_TAGGED_GENERATION_MODEL, " is a cloud model"));
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

    /** One corpus document and one seed, with every gate up to the arrangement open. */
    private void aCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }

    /**
     * {@link #aCorpus}, run once so an arrangement exists, then that arrangement approved and {@code
     * generationModel} named, so the next invocation reaches stage 6b.
     */
    private void anApprovedArrangementToWriteUnder(Path root, Path seeds, String generationModel)
            throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(
                        ArrangementGate.shortNameOf(new RunsOver(jdbcTemplate, root).latest("arrangement")),
                        "read by this test")
                .generationModel(generationModel, "set by this test")
                .build());
        GenerationScriptedBeans.forgetScriptedAnswers();
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
