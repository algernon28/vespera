package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceLabels;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What a pull of the embedding model that changes its digest does to the invocations after it, end to end
 * (ADR-228, #488): the scoring run names the digest and the weight dtype the serving runtime reports, so
 * the pull is a different scoring run, embedded and scored again; and a run reads the vectors of the one
 * embedder identity it names, whatever another pull left under the same model name.
 *
 * <p><b>Two ways a second identity gets into the database here.</b> The first and last tests script the
 * runtime to report another digest ({@link EmbeddingScriptedBeans#hasPulledAgainAs}), which is the pull
 * itself. The scripted embedder answers every chunk with the same vector under either digest, so a pull
 * made that way cannot show two scales being mixed; the second and third tests therefore store the other
 * pull's vectors themselves, with other values, as {@code RelevanceFloorInvocationTest} does for ADR-227.
 *
 * <p><b>A third way, for the state nothing shipped produces.</b> Two tests store every vector once more
 * under the run's own digest and weight dtype with another dimension, so that no single identity answers
 * for the run, and remove the row recording that a step finished, so that the step runs again under the
 * same run and meets that state.
 *
 * <p>The vectors are keyed by content and not by walk, and the tests of this class share one database, so
 * every vector that is not under the fixture's first digest is taken away after each test, and the runtime
 * is put back to reporting that digest before and after each.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Embedder identity")
@Issue("488")
@Link(name = "ADR-228", url = Adr.A_SCORING_RUN_NAMES_THE_EMBEDDING_MODELS_ARTEFACT_AND_READS_ONE_IDENTITY, type = "adr")
class APullOfTheEmbeddingModelIsADifferentScoringRunInvocationTest {

    /** The digest the runtime reports once the embedding model has been pulled again. */
    private static final String A_SECOND_PULLS_DIGEST = "5ec0nd9a11000000000000000000000000000000000000000000000000000";

    private static final int CORPUS_DOCUMENTS = 2;

    /** The runs of the one corpus before the pull, and after it. */
    private static final int ONE_SCORING_RUN = 1;

    private static final int TWO_SCORING_RUNS = 2;

    /** How many components the shorter vectors have for each one the fixture's have: half as many. */
    private static final int HALF = 2;

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

    @Autowired
    private RelevanceLabels relevanceLabels;

    @BeforeEach
    void theRuntimeReportsItsFirstDigest() {
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @AfterEach
    void onlyTheFirstDigestsVectorsAreLeft() {
        EmbeddingScriptedBeans.servesEveryModel();
        jdbcTemplate.update(
                "DELETE FROM vector WHERE embedder_identity NOT LIKE ?",
                "%;digest=" + EmbeddingScriptedBeans.DIGEST + ";dtype=" + EmbeddingScriptedBeans.DTYPE + ";dimension="
                        + fixtureDimensionOr(0) + ";%");
    }

    @Test
    @Story("The embedding model pulled again under another digest is scored as a piece of work of its own")
    @DisplayName("After the embedding model is pulled again, the next invocation embeds and scores under a second scoring run, which names the new digest")
    void aPullIsADifferentScoringRunThatNamesItsDigest(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        List<String> theRunsBeforeThePull = scoringRunIdsFor(root);
        int callsBeforeThePull = EmbeddingScriptedBeans.embeddingCallsMade();
        EmbeddingScriptedBeans.hasPulledAgainAs(A_SECOND_PULLS_DIGEST);

        cli.run("run", root.toString());

        int callsAfterThePull = EmbeddingScriptedBeans.embeddingCallsMade() - callsBeforeThePull;
        List<String> theRunsOfThePull = theRunsNotAmong(root, theRunsBeforeThePull);
        claim("the invocation after the pull reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the first invocation scored under " + ONE_SCORING_RUN + " run, and the one after the pull under a"
                        + " second: a run is named after everything it was given, and the model it embedded"
                        + " with is the one the runtime served, not only the name the profile asks for",
                () -> {
                    assertThat(theRunsBeforeThePull).hasSize(ONE_SCORING_RUN);
                    assertThat(scoringRunIdsFor(root)).hasSize(TWO_SCORING_RUNS);
                });
        claim(
                "the first run's recorded settings name the digest the runtime reported then, so a reader can"
                        + " tell which pull its scores were computed under",
                () -> assertThat(settingsOf(theRunsBeforeThePull.getFirst()))
                        .contains("\"embeddingModelDigest\":\"" + EmbeddingScriptedBeans.DIGEST + "\""));
        claim(
                "and the second run's name the digest reported after the pull, with the weight format beside it",
                () -> assertThat(theRunsOfThePull).singleElement().satisfies(run -> assertThat(settingsOf(run))
                        .contains("\"embeddingModelDigest\":\"" + A_SECOND_PULLS_DIGEST + "\"")
                        .contains("\"embeddingModelWeightDtype\":\"" + EmbeddingScriptedBeans.DTYPE + "\"")));
        claim(
                "every chunk was embedded again under the second run, in as many calls as the first invocation"
                        + " made: the vectors of the first pull are another model's as far as a score goes",
                () -> assertThat(callsAfterThePull).isPositive().isEqualTo(callsBeforeThePull));
        claim(
                "all " + CORPUS_DOCUMENTS + " documents are scored and grouped under the second run",
                () -> assertThat(theRunsOfThePull).singleElement().satisfies(run -> {
                    assertThat(countUnder("relevance_score", run)).isEqualTo(CORPUS_DOCUMENTS);
                    assertThat(countUnder("document_cluster", run)).isEqualTo(CORPUS_DOCUMENTS);
                }));
        claim(
                "and the first run's " + CORPUS_DOCUMENTS + " scores are still recorded under it: nothing of an"
                        + " earlier run is removed because another exists",
                () -> assertThat(countUnder("relevance_score", theRunsBeforeThePull.getFirst()))
                        .isEqualTo(CORPUS_DOCUMENTS));
    }

    static Stream<Arguments> anotherPullsVectors() {
        return Stream.of(
                Arguments.of(Named.of("another pull's vectors of the same length, with other values", 1)),
                Arguments.of(Named.of("another pull's vectors of half the length", HALF)));
    }

    @Test
    @Story("A run whose vectors carry no single identity is not grouped or scored")
    @DisplayName("Grouping done again where the run's own digest carries vectors of two lengths fails the invocation and leaves the groups as they were")
    void clusteringWhereTheRunsVectorsCarryTwoIdentitiesFailsAndChangesNothing(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        String theRun = scoringRunIdsFor(root).getFirst();
        List<String> theClustersBefore = clustersUnder(theRun);
        everyVectorAlsoStoredUnderTheSameDigestWithAnotherDimension();
        forgetThatItFinished(theRun, StepNames.CLUSTERING);

        cli.run("run", root.toString());

        claim(
                "the second invocation fails: scores are recorded under the run, and the vectors under the"
                        + " digest it names no longer say which one set they were computed on",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "under the scoring run the first made",
                () -> assertThat(scoringRunIdsFor(root)).containsExactly(theRun));
        claim(
                "the " + CORPUS_DOCUMENTS + " documents' groups are as the first invocation left them: the step"
                        + " stopped before it discarded any",
                () -> assertThat(clustersUnder(theRun)).hasSize(CORPUS_DOCUMENTS).isEqualTo(theClustersBefore));
        claim(
                "and grouping is not recorded as finished, so the next invocation tries it again",
                () -> assertThat(finishedRows(theRun, StepNames.CLUSTERING)).isZero());
    }

    /**
     * Every stored vector written once more under an identity of the same embedding model, digest and weight
     * dtype, differing in the dimension it states alone: the one state in which a run that names its digest
     * finds no single identity. The values are the fixture's own; only the identity matters here.
     */
    private void everyVectorAlsoStoredUnderTheSameDigestWithAnotherDimension() {
        jdbcTemplate.update(
                "INSERT INTO vector SELECT content_hash, chunker_identity, chunking_rule_identity, ordinal, ?,"
                        + " embedding FROM vector",
                theOneIdentityStored().replace(";dimension=", ";dimension=1"));
    }

    /** Removes the record that {@code step} finished under {@code run}, so the next invocation does it again. */
    private void forgetThatItFinished(String run, String step) {
        jdbcTemplate.update("DELETE FROM finished_step WHERE run_id = ? AND step = ?", run, step);
    }

    private long finishedRows(String run, String step) {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?", Long.class, run, step);
        return rows == null ? 0 : rows;
    }

    /** The one embedder identity the vectors carry, read back; it throws where there are two. */
    private String theOneIdentityStored() {
        return jdbcTemplate.queryForObject("SELECT DISTINCT embedder_identity FROM vector", String.class);
    }

    /** How many components a vector stored under the fixture's first digest has, or {@code none} with none stored. */
    private int fixtureDimensionOr(int none) {
        Integer bytes = jdbcTemplate.queryForObject(
                "SELECT MAX(LENGTH(embedding)) FROM vector WHERE embedder_identity LIKE ?",
                Integer.class,
                "%;digest=" + EmbeddingScriptedBeans.DIGEST + ";%");
        return bytes == null ? none : bytes / Float.BYTES;
    }

    private void aCorpus(Path root, Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** The seed folder named, stage 4's gate open, gate 3 open, and the threshold as given. */
    private void profile(Path seeds, String floor) {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(EveryGateOpen.BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EveryGateOpen.EMBEDDING_MODEL, "set by this test, so gate 3 is open")
                .relevanceScoreFloor(floor, floor == null ? null : "set by this test")
                .build());
    }

    private String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }

    private List<String> scoringRunIdsFor(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = 'embedding-scoring' AND w.root = ? ORDER BY run.id",
                String.class,
                walkRoot(root));
    }

    /**
     * The scoring runs of this corpus that are not among {@code earlier}. Named by exclusion: a run id is
     * derived from what the run was given (ADR-048), so its order says nothing of which came first.
     */
    private List<String> theRunsNotAmong(Path root, List<String> earlier) {
        return scoringRunIdsFor(root).stream().filter(run -> !earlier.contains(run)).toList();
    }

    private String settingsOf(String run) {
        return jdbcTemplate.queryForObject("SELECT config_consumed FROM run WHERE id = ?", String.class, run);
    }

    /** Each document {@code run} clustered with its seed and cluster ordinal, in the order of the documents. */
    private List<String> clustersUnder(String run) {
        return jdbcTemplate.queryForList(
                "SELECT occurrence_id || ' ' || winning_seed_occurrence_id || ' ' || cluster_ordinal"
                        + " FROM document_cluster WHERE run_id = ? ORDER BY occurrence_id",
                String.class,
                run);
    }

    private long countUnder(String table, String run) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE run_id = ?", Long.class, run);
        return count == null ? 0 : count;
    }
}
