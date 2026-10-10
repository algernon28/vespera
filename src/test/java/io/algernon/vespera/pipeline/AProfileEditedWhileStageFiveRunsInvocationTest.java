package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
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
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A profile saved while stage 5 runs, after the scoring run was minted, end to end. The scoring run is named
 * after the embedding model's name, the artefact Ollama reports for it and the relevance floor, each read
 * when the run is minted (ADR-117, ADR-228 section 1), and every step of the run reads under what the run
 * names (ADR-228 sections 2 and 4): a key a run consumes without being named by is an input that can change
 * while the output keeps its name, which ADR-117's general rule forbids. So an edit made part-way through an
 * invocation is read by the next run minted, and not by the later steps of the run in hand.
 *
 * <p>The edit is made from inside the embedding double once it has answered its first call ({@link
 * EmbeddingScriptedBeans#onceItHasEmbedded}): the scoring run is minted before the first chunk is embedded,
 * so the edit lands after the mint and before 5c's check at its end, 5d, 5e, 5f and the relevance report.
 *
 * <p>Every file is written with its folder's name in it, for the reason {@code
 * APullWhileTheEmbeddingStepRunsInvocationTest} gives: a vector is keyed by content, and the tests of a
 * context share one database.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Embedder identity")
@Link(name = "ADR-228", url = Adr.A_SCORING_RUN_NAMES_THE_EMBEDDING_MODELS_ARTEFACT_AND_READS_ONE_IDENTITY, type = "adr")
@Link(name = "ADR-117", url = Adr.THE_RELEVANCE_FLOOR_JOINS_THE_SCORING_RUNS_IDENTITY, type = "adr")
class AProfileEditedWhileStageFiveRunsInvocationTest {

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** Another model the scripted runtime serves, under the same digest and weight format as the first. */
    private static final String ANOTHER_SERVED_MODEL = "another-embedding-model:1b";

    /** Above anything the scripted embedder can produce, so every scored document falls under it. */
    private static final String A_FLOOR_ABOVE_EVERY_SCORE = "1.5";

    /** The edit is made once the first chunk is embedded, which is after the scoring run was minted. */
    private static final int AFTER_THE_FIRST_CHUNK = 1;

    private static final int CORPUS_DOCUMENTS = 2;

    /** A step recorded as finished is recorded in one row. */
    private static final long RECORDED_ONCE = 1;

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

    @BeforeEach
    void theRuntimeIsAsShipped() {
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @AfterEach
    void noEditIsLeftScripted() {
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @Test
    @Story("A run's later steps read the embedding model the run was started under")
    @DisplayName("The embedding model renamed in the profile while documents are being embedded does not stop the scoring and grouping already started under the first name")
    void anEmbeddingModelRenamedWhileChunksAreEmbeddedIsNotReadByTheRunsLaterSteps(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds, MODEL_NAME, null);
        EmbeddingScriptedBeans.onceItHasEmbedded(AFTER_THE_FIRST_CHUNK, () -> profile(seeds, ANOTHER_SERVED_MODEL, null));

        cli.run("run", root.toString());

        List<String> theRuns = scoringRunIdsFor(root);
        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "it scored under one run, which names the model the profile named when the run was started",
                () -> assertThat(theRuns).singleElement().satisfies(run -> assertThat(settingsOf(run))
                        .contains("\"embeddingModel\":\"" + MODEL_NAME + "\"")));
        claim(
                "all " + CORPUS_DOCUMENTS + " documents are scored and grouped under it: the vectors the run"
                        + " embedded are under the first name, and a name read again from the profile part-way"
                        + " through finds none of them",
                () -> assertThat(theRuns).singleElement().satisfies(run -> {
                    assertThat(countUnder("relevance_score", run)).isEqualTo(CORPUS_DOCUMENTS);
                    assertThat(countUnder("document_cluster", run)).isEqualTo(CORPUS_DOCUMENTS);
                }));
    }

    @Test
    @Story("A run's later steps read the embedding model the run was started under")
    @DisplayName("The embedding model cleared from the profile while documents are being embedded does not undo the embedding already done")
    void anEmbeddingModelClearedWhileChunksAreEmbeddedLeavesTheEmbeddingRecorded(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds, MODEL_NAME, null);
        EmbeddingScriptedBeans.onceItHasEmbedded(AFTER_THE_FIRST_CHUNK, () -> profile(seeds, null, null));

        cli.run("run", root.toString());

        List<String> theRuns = scoringRunIdsFor(root);
        claim(
                "embedding is recorded as finished under the one run started: the runtime still reports, for"
                        + " the model the run names, what it reported when the run was started, so nothing"
                        + " was pulled, and the documents embedded are kept",
                () -> assertThat(theRuns).singleElement().satisfies(run ->
                        assertThat(finishedRows(run, StepNames.EMBEDDING_SCORING)).isEqualTo(RECORDED_ONCE)));
    }

    @Test
    @Story("A run removes by the threshold it was started under")
    @DisplayName("The threshold cleared from the profile while documents are being embedded still removes, under the run started with it, every document below it")
    void aFloorClearedWhileChunksAreEmbeddedStillRemovesUnderTheRunItNames(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds, MODEL_NAME, A_FLOOR_ABOVE_EVERY_SCORE);
        EmbeddingScriptedBeans.onceItHasEmbedded(AFTER_THE_FIRST_CHUNK, () -> profile(seeds, MODEL_NAME, null));

        cli.run("run", root.toString());

        List<String> theRuns = scoringRunIdsFor(root);
        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "it scored under one run, which names the threshold " + A_FLOOR_ABOVE_EVERY_SCORE
                        + " the profile held when the run was started",
                () -> assertThat(theRuns).singleElement().satisfies(run -> assertThat(settingsOf(run))
                        .contains("\"relevanceScoreFloor\":" + A_FLOOR_ABOVE_EVERY_SCORE)));
        claim(
                "and all " + CORPUS_DOCUMENTS + " documents, every one scoring under that threshold, are removed"
                        + " under it: the number that removes is the number the run is named by, and no answer"
                        + " was given about this seed folder, so nothing says the number is on another scale",
                () -> assertThat(theRuns).singleElement().satisfies(run ->
                        assertThat(removalsUnder(run)).isEqualTo(CORPUS_DOCUMENTS)));
    }

    /** Two corpus documents and a seed, each naming its folder so that no other test wrote the same bytes. */
    private void aCorpus(Path root, Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(
                    root.resolve("corpus-" + i + ".txt"), "a corpus document " + i + " of " + root.getFileName());
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document of " + seeds.getFileName());
    }

    /** The seed folder named, stage 4's gate open, the embedding model and the threshold as given. */
    private void profile(Path seeds, String model, String floor) {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(model, model == null ? null : "set by this test")
                .relevanceScoreFloor(floor, floor == null ? null : "set by this test")
                .build());
    }

    private List<String> scoringRunIdsFor(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = 'embedding-scoring' AND w.root = ? ORDER BY run.id",
                String.class,
                Walk.canonicalRoot(root).toString());
    }

    private String settingsOf(String run) {
        return jdbcTemplate.queryForObject("SELECT config_consumed FROM run WHERE id = ?", String.class, run);
    }

    private long countUnder(String table, String run) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE run_id = ?", Long.class, run);
        return count == null ? 0 : count;
    }

    private long removalsUnder(String run) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verdict WHERE run_id = ? AND kind = 'BELOW_THRESHOLD'", Long.class, run);
        return count == null ? 0 : count;
    }

    private long finishedRows(String run, String step) {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?", Long.class, run, step);
        return rows == null ? 0 : rows;
    }
}
