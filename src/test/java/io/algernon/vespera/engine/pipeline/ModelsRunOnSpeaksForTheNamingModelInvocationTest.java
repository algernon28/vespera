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
 * The naming model is one of the models the profile's {@code modelsRunOn} speaks for, and the start of
 * {@code vespera run} refuses for it as for the embedding and generation models (ADR-408, amending ADR-375 §2 and
 * ADR-384 §1).
 *
 * <p>The arrangement step sends the naming model each topic's strongest seed passages (ADR-407), so it is a model
 * Vespera sends a document's text to. Where {@code modelsRunOn} says {@code cloud}, the invocation is refused at its
 * start, before anything is walked, and the naming model is neither asked about nor put a question. Where it is unset,
 * the naming model is taken to run on this machine whatever its tag, a tag ending in {@code cloud} among them: the
 * arrangement step asks Ollama only whether it serves it, and puts it the naming question.
 *
 * <p>The naming model is named in configuration, as a machine names it, with a tag of the shape Ollama gives a cloud
 * model. The serving engine is {@link EmbeddingScriptedBeans}' client and the naming model {@link
 * GenerationScriptedBeans}' chat double, each counting what it is asked. No test reaches a model or a daemon.
 *
 * <p><b>A class of its own</b>, for the configuration that names the naming model and for {@link
 * GenerationIdentityInvocationTest}'s reason: the database outlives each method, so every claim is scoped to the walk
 * of that method's own corpus, and every script is dropped before and after each method.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Synthesis")
@Feature("Only a model served on this machine is sent anything")
@Issue("847")
@Link(name = "ADR-408", url = Adr.THE_NAMING_MODEL_IS_ONE_OF_THE_MODELS_MODELS_RUN_ON_SPEAKS_FOR, type = "adr")
@Link(name = "ADR-384", url = Adr.WHETHER_THE_MODELS_RUN_LOCALLY_OR_IN_THE_CLOUD_IS_THE_PROFILES_TO_SAY, type = "adr")
@Link(name = "ADR-375", url = Adr.AN_UNANSWERED_CHECK_TAKES_A_MODEL_AS_LOCAL_AND_A_CLOUD_MODEL_IS_REFUSED_AT_THE_START, type = "adr")
@Link(name = "ADR-407", url = Adr.TOPICS_ARE_NAMED_AT_THE_HEAD_OF_THE_ARRANGEMENT, type = "adr")
class ModelsRunOnSpeaksForTheNamingModelInvocationTest {

    /** A naming model's tag of the shape Ollama gives a cloud model: served by the local daemon, answered elsewhere. */
    private static final String A_CLOUD_TAGGED_NAMING_MODEL = "a-naming-model:4b-cloud";

    /** The exit code of every refusal at the start of {@code vespera run}. */
    private static final int REFUSED = 1;

    /** The exit code of an invocation that ends at the arrangement's approval, as one with none approved does. */
    private static final int ENDED_AT_THE_APPROVAL = 0;

    /** What every line of a refusal at the start of {@code vespera run} begins with. */
    private static final String STOPPED_AT_ITS_START = "vespera run stopped at its start: ";

    /** What the arrangement step's line says where it refuses the naming model. */
    private static final String THE_NAMING_MODEL_WAS_REFUSED = "the naming model " + A_CLOUD_TAGGED_NAMING_MODEL
            + " was refused";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectoryAndNamingModel(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.naming.model", () -> A_CLOUD_TAGGED_NAMING_MODEL);
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
    @DisplayName("A profile saying the models run in the cloud stops the invocation at its start, before the model that names the topics is asked anything")
    void cloudInTheProfileStopsTheStartBeforeTheNamingModelIsAskedAnything(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        aCorpusAsFarAsTheArrangement(root, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .modelsRunOn("cloud", "set by this test")
                .build());
        int before = output.getErr().length();

        cli.run("run", root.toString());
        String onStandardError = output.getErr().substring(before);

        claim(
                "the start refused the invocation: exit code " + REFUSED + ", the code of every refusal at its start,"
                        + " one line on standard error beginning \"" + STOPPED_AT_ITS_START + "\", and nothing walked"
                        + " (the line's words are held where every refused value is)",
                () -> {
                    assertThat(cli.getExitCode()).isEqualTo(REFUSED);
                    assertThat(onStandardError.lines().filter(line -> line.startsWith(STOPPED_AT_ITS_START)))
                            .hasSize(1);
                    assertThat(walksOf(root)).isZero();
                });
        claim(
                "the model that names the topics, " + A_CLOUD_TAGGED_NAMING_MODEL + ", was neither asked about nor"
                        + " put a question: nothing was sent to it",
                () -> {
                    assertThat(EmbeddingScriptedBeans.shownModels()).doesNotContain(A_CLOUD_TAGGED_NAMING_MODEL);
                    assertThat(GenerationScriptedBeans.namingModelsAsked()).isEmpty();
                });
    }

    @Test
    @Story("Where the models run is the operator's to say")
    @DisplayName("With nothing said of where the models run, the model that names the topics is taken to run on this machine whatever its name, and is asked to name them")
    void aCloudTaggedNamingModelNamesTheTopicsWhereModelsRunOnIsUnset(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        aCorpusAsFarAsTheArrangement(root, seeds);
        int before = output.getAll().length();
        int beforeErr = output.getErr().length();

        cli.run("run", root.toString());
        String said = output.getAll().substring(before);
        String onStandardError = output.getErr().substring(beforeErr);
        List<String> namingModelsAsked = GenerationScriptedBeans.namingModelsAsked();

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
                "the arrangement step asked Ollama whether it serves " + A_CLOUD_TAGGED_NAMING_MODEL + ", and did not"
                        + " refuse it: its name says nothing of where it runs",
                () -> {
                    assertThat(EmbeddingScriptedBeans.shownModels()).contains(A_CLOUD_TAGGED_NAMING_MODEL);
                    assertThat(said).doesNotContain(THE_NAMING_MODEL_WAS_REFUSED);
                });
        claim(
                "and it was asked to name the topics, so the seed passages were sent to it, and no other model was asked",
                () -> assertThat(namingModelsAsked).isNotEmpty().containsOnly(A_CLOUD_TAGGED_NAMING_MODEL));
    }

    /** One corpus document and one seed, with every gate up to the arrangement open and no arrangement approved. */
    private void aCorpusAsFarAsTheArrangement(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }

    /** How many walks were recorded over this method's own corpus. */
    private int walksOf(Path root) {
        Integer walks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk WHERE root = ?", Integer.class, Walk.canonicalRoot(root).toString());
        return walks == null ? 0 : walks;
    }
}
