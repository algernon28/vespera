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
 * A generation model that is not local is refused at the start of every invocation that names it, before anything is
 * walked, and not first at the generation step (ADR-375, #725, extending ADR-202 and #431).
 *
 * <p>A loopback address does not keep a cluster's text here: Ollama serves a cloud model through the local daemon
 * and forwards each request to ollama.com. {@code vespera run} checks, at its start, every model the profile and the
 * configuration name for it, the generation model among them, whether or not the invocation will reach the step that
 * sends to it: the generation step comes invocations after the walk, behind the arrangement's approval. A tag that
 * ends in {@code cloud} is refused without asking Ollama anything, in one line on standard error, and the invocation
 * ends with exit code 1.
 *
 * <p>The serving engine here is {@link EmbeddingScriptedBeans}' client, scripted by name. No test reaches a model or
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
@Issue("725")
@Link(name = "ADR-375", url = Adr.AN_UNANSWERED_CHECK_TAKES_A_MODEL_AS_LOCAL_AND_A_CLOUD_MODEL_IS_REFUSED_AT_THE_START, type = "adr")
@Link(name = "ADR-202", url = Adr.GENERATION_AND_EMBEDDING_REFUSE_A_MODEL_NOT_SERVED_HERE, type = "adr")
@Link(name = "ADR-114", url = Adr.THE_GENERATION_MODEL_IS_CONFIGURATION_WITH_A_DEFAULT, type = "adr")
class GenerationRefusesAModelNotServedHereInvocationTest {

    /** A tag Ollama gives a cloud model: served by the local daemon, answered by ollama.com. */
    private static final String A_CLOUD_TAGGED_GENERATION_MODEL = "a-large-model:120b-cloud";

    /** The exit code of every refusal at the start of {@code vespera run}. */
    private static final int REFUSED = 1;

    /** The whole line the refusal prints on standard error. */
    private static final String THE_LINE = "vespera run stopped at its start: the generation model "
            + A_CLOUD_TAGGED_GENERATION_MODEL + " was refused, so nothing was walked and no run was minted -- the model "
            + A_CLOUD_TAGGED_GENERATION_MODEL + " is a cloud model: Ollama forwards it to a hosted service, and no"
            + " document's text may be sent there";

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
    @DisplayName("A generation model whose tag ends in cloud is refused at the start of an invocation that would not reach generation, before anything is walked")
    void aCloudTaggedGenerationModelIsRefusedAtTheStartOfAnInvocationThatWouldNotReachIt(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .generationModel(A_CLOUD_TAGGED_GENERATION_MODEL, "set by this test, with no arrangement approved")
                .build());
        int before = output.getErr().length();

        cli.run("run", root.toString());
        String onStandardError = output.getErr().substring(before);

        claim(
                "the invocation ends with exit code " + REFUSED + ", the code of every refusal at its start, where"
                        + " with no arrangement approved it would have ended at that approval with exit code 0",
                () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim(
                "standard error carries the refusal once, whole: \"" + THE_LINE + "\"",
                () -> assertThat(onStandardError.lines().filter(THE_LINE::equals)).hasSize(1));
        claim("nothing was walked, so no stage ran before the refusal", () -> assertThat(walksOf(root)).isZero());
        claim(
                "Ollama was not asked about it: the tag alone settles it",
                () -> assertThat(EmbeddingScriptedBeans.shownModels()).doesNotContain(A_CLOUD_TAGGED_GENERATION_MODEL));
    }

    /** How many walks were recorded over this method's own corpus. */
    private int walksOf(Path root) {
        Integer walks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk WHERE root = ?", Integer.class, Walk.canonicalRoot(root).toString());
        return walks == null ? 0 : walks;
    }
}
