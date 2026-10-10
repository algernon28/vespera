package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** The digest the runtime reports once the embedding model has been pulled again. */
    private static final String A_SECOND_PULLS_DIGEST = "5ec0nd9a11000000000000000000000000000000000000000000000000000";

    /** A weight format other than the one the scripted runtime reports unless told. */
    private static final String ANOTHER_WEIGHT_DTYPE = "Q8_0";

    /** Above anything the scripted embedder can produce, so every scored document falls under it. */
    private static final String A_FLOOR_ABOVE_EVERY_SCORE = "1.5";

    /** Below anything it can produce, so it removes nothing: it is set only to make a scoring run of its own. */
    private static final String A_FLOOR_BELOW_EVERY_SCORE = "0.1";

    private static final int CORPUS_DOCUMENTS = 2;

    /** The runs of the one corpus before the pull, and after it. */
    private static final int ONE_SCORING_RUN = 1;

    private static final int TWO_SCORING_RUNS = 2;

    /** One with no threshold, one with the threshold, and one for the threshold under the second pull. */
    private static final int THREE_SCORING_RUNS = 3;

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

    @Test
    @Story("A run scores on the vectors of the pull it names and no other")
    @DisplayName("A run scored after another pull's vectors were stored gives every document the score it had before")
    void aRunScoresOnTheVectorsOfItsOwnDigestOnly(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        String theFirstRun = scoringRunIdsFor(root).getFirst();
        List<Double> theScoresBefore = scoresUnder(theFirstRun);
        everyVectorAlsoStoredUnderAnotherDigest(fixtureDimensionOr(0));
        // A threshold that removes nothing, set so that the next invocation scores under a run of its own.
        profile(seeds, A_FLOOR_BELOW_EVERY_SCORE);

        cli.run("run", root.toString());

        List<String> theSecondRun = theRunsNotAmong(root, List.of(theFirstRun));
        claim("the second invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the first run scored all " + CORPUS_DOCUMENTS + " documents",
                () -> assertThat(theScoresBefore).hasSize(CORPUS_DOCUMENTS));
        claim(
                "the second run gives each document the score the first gave it: both name the digest the"
                        + " runtime reports, and the vectors stored for the same documents under another digest,"
                        + " which point elsewhere, were not read with them. Read together they would put two"
                        + " models' similarities into one mean",
                () -> assertThat(theSecondRun)
                        .singleElement()
                        .satisfies(run -> assertThat(scoresUnder(run)).isEqualTo(theScoresBefore)));
    }

    @Test
    @Story("A run scores on the vectors of the pull it names and no other")
    @DisplayName("Another pull's vectors of half the length do not stop a run that names its own digest, or change a score")
    void anotherPullsShorterVectorsAreNotReadWithTheRuns(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        String theFirstRun = scoringRunIdsFor(root).getFirst();
        List<Double> theScoresBefore = scoresUnder(theFirstRun);
        everyVectorAlsoStoredUnderAnotherDigest(fixtureDimensionOr(0) / HALF);
        profile(seeds, A_FLOOR_BELOW_EVERY_SCORE);

        cli.run("run", root.toString());

        List<String> theSecondRun = theRunsNotAmong(root, List.of(theFirstRun));
        claim(
                "the second invocation reports success: no vector of the run's own was compared with one of half"
                        + " its length, which reads past the end of the shorter",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the second run gives each of the " + CORPUS_DOCUMENTS + " documents the score the first gave it",
                () -> assertThat(theSecondRun).singleElement().satisfies(run -> assertThat(scoresUnder(run))
                        .hasSize(CORPUS_DOCUMENTS)
                        .isEqualTo(theScoresBefore)));
    }

    /**
     * ADR-230 has clustering done again under the same scoring run, perhaps invocations later. Its completion
     * is forgotten here by removing the row that records it, as {@code
     * StageFiveReportsItsProgressInvocationTest} does, so that the step runs again under the run it ran under.
     *
     * <p><b>It holds that the path goes through, not which vectors were read.</b> The corpus is two
     * documents, which any vectors cluster alike. That a second pass reads the first's vectors is held on
     * forty documents by {@code embedding.ARunReadsTheVectorsOfOneEmbedderIdentityTest}.
     */
    @Test
    @Story("A run grouped again reads the vectors it was scored on")
    @DisplayName("Grouping can be done again under the same scoring run after another pull's shorter vectors were stored, and the invocation succeeds")
    @Issue("489")
    void aRunClusteredAgainReadsTheVectorsItWasScoredOn(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        String theRun = scoringRunIdsFor(root).getFirst();
        List<String> theGroupsBefore = clustersUnder(theRun);
        everyVectorAlsoStoredUnderAnotherDigest(fixtureDimensionOr(0) / HALF);
        forgetThatItFinished(theRun, StepNames.CLUSTERING);

        cli.run("run", root.toString());

        claim("the second invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "it arrived at the scoring run the first made: the runtime reports the digest it reported then",
                () -> assertThat(scoringRunIdsFor(root)).containsExactly(theRun));
        claim(
                "grouping ran again under that run, its completion being recorded once more",
                () -> assertThat(finishedRows(theRun, StepNames.CLUSTERING)).isEqualTo(1L));
        claim(
                "and each of the " + CORPUS_DOCUMENTS + " documents has a group again, the one it had. Two"
                        + " documents are grouped alike whatever vectors are read, so this says the second"
                        + " pass went through with the shorter vectors stored, not which it read",
                () -> assertThat(clustersUnder(theRun)).hasSize(CORPUS_DOCUMENTS).isEqualTo(theGroupsBefore));
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
        List<String> theGroupsBefore = clustersUnder(theRun);
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
                () -> assertThat(clustersUnder(theRun)).hasSize(CORPUS_DOCUMENTS).isEqualTo(theGroupsBefore));
        claim(
                "and grouping is not recorded as finished, so the next invocation tries it again",
                () -> assertThat(finishedRows(theRun, StepNames.CLUSTERING)).isZero());
    }

    @Test
    @Story("A run whose vectors carry no single identity is not grouped or scored")
    @DisplayName("Scoring done again where the run's own digest carries vectors of two lengths scores nothing and is not recorded as finished")
    void scoringWhereTheRunsVectorsCarryTwoIdentitiesScoresNothing(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        String theRun = scoringRunIdsFor(root).getFirst();
        long scoredBefore = countUnder("relevance_score", theRun);
        everyVectorAlsoStoredUnderTheSameDigestWithAnotherDimension();
        forgetThatItFinished(theRun, StepNames.RELEVANCE_SCORING);

        cli.run("run", root.toString());

        claim(
                "the first invocation scored all " + CORPUS_DOCUMENTS + " documents",
                () -> assertThat(scoredBefore).isEqualTo(CORPUS_DOCUMENTS));
        claim("the second invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "under the scoring run the first made",
                () -> assertThat(scoringRunIdsFor(root)).containsExactly(theRun));
        claim(
                "no score stands under the run: a step that runs again discards its own scores first, and"
                        + " then read no vector, there being no one identity to read them under",
                () -> assertThat(countUnder("relevance_score", theRun)).isZero());
        claim(
                "and scoring is not recorded as finished, so the next invocation tries it again",
                () -> assertThat(finishedRows(theRun, StepNames.RELEVANCE_SCORING)).isZero());
    }

    @Test
    @Story("The embedding model pulled again under another digest is scored as a piece of work of its own")
    @DisplayName("The same digest reported with another weight format is a second scoring run, which names that format")
    void anotherWeightDtypeAloneIsADifferentScoringRun(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        List<String> theRunsBefore = scoringRunIdsFor(root);
        EmbeddingScriptedBeans.reportsTheWeightDtype(ANOTHER_WEIGHT_DTYPE);

        cli.run("run", root.toString());

        claim("the second invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "it scored under a second run, whose recorded settings name the digest reported both times"
                        + " and the weight format reported the second time",
                () -> assertThat(theRunsNotAmong(root, theRunsBefore)).singleElement().satisfies(run -> {
                    assertThat(settingsOf(run))
                            .contains("\"embeddingModelDigest\":\"" + EmbeddingScriptedBeans.DIGEST + "\"")
                            .contains("\"embeddingModelWeightDtype\":\"" + ANOTHER_WEIGHT_DTYPE + "\"");
                    assertThat(countUnder("relevance_score", run)).isEqualTo(CORPUS_DOCUMENTS);
                }));
    }

    @Test
    @Story("After a pull the threshold applies again once the sample is answered under the new pull")
    @DisplayName("After a pull the threshold removes nothing until an answer is given under the new digest, which the label file names, and then removes again")
    @Link(name = "ADR-227", url = Adr.THE_FLOORS_STEP_WITHDRAWS_ITS_REMOVALS_IN_EVERY_CASE, type = "adr")
    @Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
    void afterAPullTheThresholdAppliesOnceAnsweredUnderTheNewDigest(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        String theFirstPullsIdentity = theOneIdentityStored();
        anAnswerGivenUnder(root, seeds, theFirstPullsIdentity);
        profile(seeds, A_FLOOR_ABOVE_EVERY_SCORE);
        cli.run("run", root.toString());
        List<String> theRunsBeforeThePull = scoringRunIdsFor(root);
        Map<String, Long> removedBeforeThePull = removalsByRun(root);
        EmbeddingScriptedBeans.hasPulledAgainAs(A_SECOND_PULLS_DIGEST);

        cli.run("run", root.toString());

        Map<String, Long> removedAfterThePull = removalsByRun(root);
        String theLabelFileAfterThePull = Files.readString(workingDirectory.resolve(RelevanceLabelFile.FILE_NAME));
        String theLabellingPageAfterThePull =
                Files.readString(workingDirectory.resolve(RelevanceLabellingReport.FILE_NAME));
        String theSecondPullsIdentity =
                theFirstPullsIdentity.replace(EmbeddingScriptedBeans.DIGEST, A_SECOND_PULLS_DIGEST);
        anAnswerGivenUnder(root, seeds, theSecondPullsIdentity);

        cli.run("run", root.toString());

        Map<String, Long> removedOnceAnswered = removalsByRun(root);
        List<String> theRunOfThePull = theRunsNotAmong(root, theRunsBeforeThePull);
        claim(
                "before the pull the threshold had removed all " + CORPUS_DOCUMENTS + " documents, under one run",
                () -> assertThat(removedBeforeThePull.values()).containsExactly((long) CORPUS_DOCUMENTS));
        claim(
                "the pull made a third scoring run of the " + THREE_SCORING_RUNS + " this corpus now has",
                () -> assertThat(scoringRunIdsFor(root)).hasSize(THREE_SCORING_RUNS));
        claim(
                "under that run nothing was removed at first: the answers the threshold was read off were given"
                        + " under the first pull, which is another scale. What the earlier run removed is still"
                        + " recorded under the earlier run, which this invocation does not read",
                () -> assertThat(removedAfterThePull).isEqualTo(removedBeforeThePull));
        claim(
                "the label file written after the pull says it was generated under the second digest, so an"
                        + " answer given from it is recorded on the scale the new scores are on",
                () -> assertThat(theLabelFileAfterThePull.lines().filter(line -> line.contains("generatedUnderEmbedder")))
                        .singleElement()
                        .asString()
                        .contains(";digest=" + A_SECOND_PULLS_DIGEST + ";"));
        claim(
                "the page written after the pull tells the operator the threshold is not being applied, naming"
                        + " what the answers were given under, the first pull, and what this run scored under,"
                        + " the second",
                () -> assertThat(theLabellingPageAfterThePull)
                        .contains("not being applied")
                        .contains(theFirstPullsIdentity)
                        .contains(theSecondPullsIdentity));
        claim(
                "and once an answer is given under the second pull, the next invocation removes all "
                        + CORPUS_DOCUMENTS + " documents under the third run: vectors of two pulls in one"
                        + " database do not keep the threshold from ever being applied again",
                () -> assertThat(theRunOfThePull).singleElement().satisfies(run -> assertThat(removedOnceAnswered)
                        .containsEntry(run, (long) CORPUS_DOCUMENTS)));
    }

    /**
     * Every stored vector written once more under the identity of another digest of the same embedding
     * model, with {@code components} components of which the first is one and the rest nothing: no vector
     * the scripted embedder produces, which answers every chunk with ones.
     */
    private void everyVectorAlsoStoredUnderAnotherDigest(int components) {
        String theFixturesIdentity = theOneIdentityStored();
        String anotherPullsIdentity = theFixturesIdentity
                .replace(EmbeddingScriptedBeans.DIGEST, A_SECOND_PULLS_DIGEST)
                .replace(";dimension=" + fixtureDimensionOr(0) + ";", ";dimension=" + components + ";");
        ByteBuffer vector = ByteBuffer.allocate(components * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        vector.putFloat(0, 1f);
        jdbcTemplate.update(
                "INSERT INTO vector SELECT content_hash, chunker_identity, chunking_rule_identity, ordinal, ?, ?"
                        + " FROM vector WHERE embedder_identity = ?",
                anotherPullsIdentity,
                vector.array(),
                theFixturesIdentity);
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
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .relevanceScoreFloor(floor, floor == null ? null : "set by this test")
                .build());
    }

    /** One answer about one of this corpus's documents, recorded as given under {@code embedderIdentity}. */
    private void anAnswerGivenUnder(Path root, Path seeds, String embedderIdentity) {
        String aDocument = jdbcTemplate.queryForObject(
                "SELECT f.path FROM file_occurrence f JOIN walk w ON w.id = f.walk_id"
                        + " WHERE w.root = ? ORDER BY f.path LIMIT 1",
                String.class,
                walkRoot(root));
        relevanceLabels.record(
                new OccurrencePath(aDocument),
                Walk.canonicalRoot(seeds).toString(),
                true,
                new RunId(scoringRunIdsFor(root).getFirst()),
                1.0,
                embedderIdentity);
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

    /** The scores {@code run} recorded, in the order of the documents they are of. */
    private List<Double> scoresUnder(String run) {
        return jdbcTemplate.queryForList(
                "SELECT score FROM relevance_score WHERE run_id = ? ORDER BY occurrence_id", Double.class, run);
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

    /** How many below-threshold verdicts each run carries over this corpus; a run with none is absent. */
    private Map<String, Long> removalsByRun(Path root) {
        Map<String, Long> removals = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT v.run_id, COUNT(*) FROM verdict v JOIN file_occurrence f ON f.id = v.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ? AND v.kind = 'BELOW_THRESHOLD'"
                        + " GROUP BY v.run_id ORDER BY v.run_id",
                resultSet -> {
                    removals.put(resultSet.getString(1), resultSet.getLong(2));
                },
                walkRoot(root));
        return removals;
    }
}
