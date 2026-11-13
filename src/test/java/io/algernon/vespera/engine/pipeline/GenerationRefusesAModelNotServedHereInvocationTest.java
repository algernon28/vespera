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
 * A generation model is not refused for its name at the start of an invocation (ADR-384, amending ADR-375 §2): where
 * the models run is the profile's {@code modelsRunOn} to say, and the start reads that alone, asking Ollama nothing
 * about any model and reading nothing from a model's tag.
 *
 * <p>So a profile naming a generation model whose tag ends in {@code cloud}, with {@code modelsRunOn} unset, walks the
 * corpus and goes as far as the arrangement's approval, as any other profile with no arrangement approved does.
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
@Link(name = "ADR-384", url = Adr.WHETHER_THE_MODELS_RUN_LOCALLY_OR_IN_THE_CLOUD_IS_THE_PROFILES_TO_SAY, type = "adr")
@Link(name = "ADR-202", url = Adr.GENERATION_AND_EMBEDDING_REFUSE_A_MODEL_NOT_SERVED_HERE, type = "adr")
@Link(name = "ADR-114", url = Adr.THE_GENERATION_MODEL_IS_CONFIGURATION_WITH_A_DEFAULT, type = "adr")
class GenerationRefusesAModelNotServedHereInvocationTest {

    /** A tag Ollama gives a cloud model: served by the local daemon, answered by ollama.com. */
    private static final String A_CLOUD_TAGGED_GENERATION_MODEL = "a-large-model:120b-cloud";

    /** The exit code of an invocation that ends at the arrangement's approval, as one with none approved does. */
    private static final int ENDED_AT_THE_APPROVAL = 0;

    /** What every line of a refusal at the start of {@code vespera run} begins with. */
    private static final String STOPPED_AT_ITS_START = "vespera run stopped at its start: ";

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
    @Story("Where the models run is the operator's to say")
    @DisplayName("A generation model whose tag ends in cloud does not stop the start of an invocation: the corpus is walked and the invocation goes as far as the approval")
    void aCloudTaggedGenerationModelDoesNotStopTheStart(
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
                "the invocation ends with exit code " + ENDED_AT_THE_APPROVAL + ", at the arrangement's approval, as"
                        + " one with no arrangement approved does",
                () -> assertThat(cli.getExitCode()).isEqualTo(ENDED_AT_THE_APPROVAL));
        claim(
                "the start refused nothing, and the corpus was walked once",
                () -> {
                    assertThat(onStandardError.lines()).noneMatch(line -> line.startsWith(STOPPED_AT_ITS_START));
                    assertThat(walksOf(root)).isEqualTo(1);
                });
        claim(
                "and Ollama was not asked about the generation model: no step that sends to it was reached",
                () -> assertThat(EmbeddingScriptedBeans.shownModels()).doesNotContain(A_CLOUD_TAGGED_GENERATION_MODEL));
    }

    /** How many walks were recorded over this method's own corpus. */
    private int walksOf(Path root) {
        Integer walks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk WHERE root = ?", Integer.class, Walk.canonicalRoot(root).toString());
        return walks == null ? 0 : walks;
    }
}
