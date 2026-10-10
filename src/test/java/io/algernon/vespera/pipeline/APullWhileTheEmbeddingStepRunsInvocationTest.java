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
 * What a pull of the embedding model does when it lands while stage 5's embedding step is running, end to
 * end (ADR-231, #496). The scoring run names the digest Ollama reported when it was minted (ADR-228), and
 * each chunk is stored under what Ollama reports when it is embedded (ADR-084), so the chunks after the pull
 * are not under the run's embedder identity. The embedding step is last to embed the seeds, so a late pull
 * leaves seeds on the far side of it, and scoring used to go on against the seeds that were left.
 *
 * <p><b>Two ways a seed loses its vector here.</b> Three tests script the runtime to report another digest
 * once it has embedded {@link #CHUNKS_BEFORE_THE_PULL} chunks ({@link
 * EmbeddingScriptedBeans#pullsAgainOnceItHasEmbedded}), which is the pull itself. The fourth moves one seed's
 * stored vectors under another digest's identity by hand and removes the row recording that scoring
 * finished, so that scoring runs again where the embedding step is recorded and makes no comparison: the way
 * into the same state that the embedding step's own check does not close.
 *
 * <p><b>And one seed that never had a vector to lose.</b> The fifth test adds a seed whose only text is a
 * page header, which the chunker leaves out: usable, with no chunk and so nothing to embed. It is left out
 * of the seeds scoring reads, with a line, and is not refused.
 *
 * <p><b>Every file is written with its folder's name in it.</b> A vector is keyed by content and not by
 * walk, and the tests of every class sharing this context share one database, so a file with the bytes of
 * another test's would find that test's vectors already stored under the fixture's first digest and no seed
 * would be without one. Every vector not under the first digest is taken away after each test, as {@code
 * APullOfTheEmbeddingModelIsADifferentScoringRunInvocationTest} does.
 */
@CascadeSliceTest
@ExtendWith(OutputCaptureExtension.class)
@Import(SeedScriptedExtractionBeans.class)
@Epic("Relevance")
@Feature("Embedder identity")
@Issue("496")
@Link(name = "ADR-231", url = Adr.A_PULL_WHILE_THE_EMBEDDING_STEP_RUNS_STOPS_IT_AND_SCORING_REFUSES_A_SEED_WHOSE_CHUNKS_HAVE_NO_VECTOR, type = "adr")
@Link(name = "ADR-228", url = Adr.A_SCORING_RUN_NAMES_THE_EMBEDDING_MODELS_ARTEFACT_AND_READS_ONE_IDENTITY, type = "adr")
class APullWhileTheEmbeddingStepRunsInvocationTest {

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** The digest the runtime reports once the embedding model has been pulled again. */
    private static final String A_SECOND_PULLS_DIGEST = "5ec0nd9a11000000000000000000000000000000000000000000000000000";

    private static final int CORPUS_DOCUMENTS = 2;

    private static final int SEED_DOCUMENTS = 2;

    /**
     * The corpus documents are embedded first and the seeds after them, and each of these short files is one
     * chunk: so after this many, both corpus documents and one seed, the one chunk left is the other seed's.
     */
    private static final int CHUNKS_BEFORE_THE_PULL = CORPUS_DOCUMENTS + 1;

    /** Each of the corpus documents and seeds is one chunk, so this many calls embed them all. */
    private static final int EVERY_CHUNK = CORPUS_DOCUMENTS + SEED_DOCUMENTS;

    /** The words of the embedding step's stop that say what happened. */
    private static final String THE_STOP_SAYS = "was pulled again while its chunks were being embedded";

    /** A seed on each side of the pull. */
    private static final long ONE_SEED = 1;

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
    void theRuntimeReportsItsFirstDigest() {
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @AfterEach
    void onlyTheFirstDigestsVectorsAreLeft() {
        EmbeddingScriptedBeans.servesEveryModel();
        jdbcTemplate.update(
                "DELETE FROM vector WHERE embedder_identity NOT LIKE ?",
                "%;digest=" + EmbeddingScriptedBeans.DIGEST + ";dtype=" + EmbeddingScriptedBeans.DTYPE + ";%");
    }

    @Test
    @Story("The embedding model pulled again while documents are being embedded stops the work")
    @DisplayName("A pull that lands before the last seed document is embedded stops the invocation: embedding is not recorded as finished and no document is scored")
    void aPullPartWayThroughTheEmbeddingStepStopsItUnrecorded(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        aCorpus(root, seeds);
        profile(seeds);
        EmbeddingScriptedBeans.pullsAgainOnceItHasEmbedded(CHUNKS_BEFORE_THE_PULL, A_SECOND_PULLS_DIGEST);

        cli.run("run", root.toString());

        List<String> theRuns = scoringRunIdsFor(root);
        claim(
                "the pull landed between the " + SEED_DOCUMENTS + " seed documents: " + ONE_SEED + " has its"
                        + " vectors under the digest reported before it and " + ONE_SEED + " under the digest"
                        + " reported after, and both corpus documents were embedded before it",
                () -> {
                    assertThat(documentsWithVectorsUnder(seeds, EmbeddingScriptedBeans.DIGEST))
                            .isEqualTo(ONE_SEED);
                    assertThat(documentsWithVectorsUnder(seeds, A_SECOND_PULLS_DIGEST))
                            .isEqualTo(ONE_SEED);
                    assertThat(documentsWithVectorsUnder(root, EmbeddingScriptedBeans.DIGEST))
                            .isEqualTo(CORPUS_DOCUMENTS);
                });
        claim(
                "the invocation fails: the model that finished the embedding is not the one the scoring was"
                        + " started for, and a seed document is missing from it",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "and it says why: the embedding model was pulled again while the documents were being embedded",
                () -> assertThat(output.getAll()).contains(THE_STOP_SAYS));
        claim(
                "it was started as one scoring run, which names the digest reported before the pull",
                () -> assertThat(theRuns).singleElement().satisfies(run -> assertThat(settingsOf(run))
                        .contains("\"embeddingModelDigest\":\"" + EmbeddingScriptedBeans.DIGEST + "\"")));
        claim(
                "embedding is not recorded as finished under that run, so if the run is ever continued it"
                        + " embeds again and does not go on without the seed document",
                () -> assertThat(finishedRows(theRuns.getFirst(), StepNames.EMBEDDING_SCORING))
                        .isZero());
        claim(
                "and no document is scored under it: a score against " + ONE_SEED + " of " + SEED_DOCUMENTS
                        + " seed documents would look like any other score and mean something narrower",
                () -> assertThat(countUnder("relevance_score", theRuns.getFirst())).isZero());
    }

    /**
     * The step asks at its end, whatever the vectors are stored under (ADR-231 section 1, and the fourth row
     * of section 5's table). The scripted runtime reports the other digest once the last chunk's call has
     * been answered, and that chunk's artefact was asked for before the call, so it is stored under the first.
     */
    @Test
    @Story("The embedding model pulled again while documents are being embedded stops the work")
    @DisplayName("A pull that lands as the last document is embedded stops the invocation too, though every vector is under the earlier digest")
    void aPullThatLandsWithTheLastChunkStillStopsTheStep(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        aCorpus(root, seeds);
        profile(seeds);
        EmbeddingScriptedBeans.pullsAgainOnceItHasEmbedded(EVERY_CHUNK, A_SECOND_PULLS_DIGEST);

        cli.run("run", root.toString());

        List<String> theRuns = scoringRunIdsFor(root);
        claim(
                "all " + SEED_DOCUMENTS + " seed documents and all " + CORPUS_DOCUMENTS + " corpus documents have"
                        + " their vectors under the digest reported before the pull, and none under the digest"
                        + " reported after: the pull landed once the last of the " + EVERY_CHUNK
                        + " chunks had been embedded",
                () -> {
                    assertThat(documentsWithVectorsUnder(seeds, EmbeddingScriptedBeans.DIGEST))
                            .isEqualTo(SEED_DOCUMENTS);
                    assertThat(documentsWithVectorsUnder(root, EmbeddingScriptedBeans.DIGEST))
                            .isEqualTo(CORPUS_DOCUMENTS);
                    assertThat(documentsWithVectorsUnder(seeds, A_SECOND_PULLS_DIGEST)
                                    + documentsWithVectorsUnder(root, A_SECOND_PULLS_DIGEST))
                            .isZero();
                });
        claim(
                "the invocation fails all the same: the model is no longer the one the scoring was started for",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "and it says why: the embedding model was pulled again while the documents were being embedded",
                () -> assertThat(output.getAll()).contains(THE_STOP_SAYS));
        claim(
                "embedding is not recorded as finished under the one scoring run, and no document is scored"
                        + " under it",
                () -> assertThat(theRuns).singleElement().satisfies(run -> {
                    assertThat(finishedRows(run, StepNames.EMBEDDING_SCORING)).isZero();
                    assertThat(countUnder("relevance_score", run)).isZero();
                }));
    }

    @Test
    @Story("The embedding model pulled again while documents are being embedded stops the work")
    @DisplayName("The invocation after such a pull embeds every document under the new digest and scores against every seed document")
    void theInvocationAfterThePullEmbedsAndScoresUnderTheNewDigest(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        aCorpus(root, seeds);
        profile(seeds);
        EmbeddingScriptedBeans.pullsAgainOnceItHasEmbedded(CHUNKS_BEFORE_THE_PULL, A_SECOND_PULLS_DIGEST);
        cli.run("run", root.toString());
        List<String> theRunOfTheFirstDigest = scoringRunIdsFor(root);

        cli.run("run", root.toString());

        List<String> theRunOfThePull = scoringRunIdsFor(root).stream()
                .filter(run -> !theRunOfTheFirstDigest.contains(run))
                .toList();
        claim("the invocation after the pull reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "it scored under a second run, which names the digest reported since the pull",
                () -> assertThat(theRunOfThePull).singleElement().satisfies(run -> assertThat(settingsOf(run))
                        .contains("\"embeddingModelDigest\":\"" + A_SECOND_PULLS_DIGEST + "\"")));
        claim(
                "both of the " + SEED_DOCUMENTS + " seed documents and both of the " + CORPUS_DOCUMENTS
                        + " corpus documents have vectors under that digest: nothing is stuck on the side of"
                        + " the pull it was first embedded on",
                () -> {
                    assertThat(documentsWithVectorsUnder(seeds, A_SECOND_PULLS_DIGEST))
                            .isEqualTo(SEED_DOCUMENTS);
                    assertThat(documentsWithVectorsUnder(root, A_SECOND_PULLS_DIGEST))
                            .isEqualTo(CORPUS_DOCUMENTS);
                });
        claim(
                "embedding is recorded as finished under the second run and all " + CORPUS_DOCUMENTS
                        + " corpus documents are scored under it",
                () -> assertThat(theRunOfThePull).singleElement().satisfies(run -> {
                    assertThat(finishedRows(run, StepNames.EMBEDDING_SCORING)).isEqualTo(RECORDED_ONCE);
                    assertThat(countUnder("relevance_score", run)).isEqualTo(CORPUS_DOCUMENTS);
                }));
        claim(
                "and the run the pull interrupted still has no score",
                () -> assertThat(countUnder("relevance_score", theRunOfTheFirstDigest.getFirst()))
                        .isZero());
        claim(
                "neither invocation says a seed document was left out for having nothing to embed: both have"
                        + " a body, and the one the pull took was embedded again, not passed over",
                () -> assertThat(output.getAll()).doesNotContain("produced text and no chunk"));
    }

    @Test
    @Story("The embedding model pulled again while documents are being embedded stops the work")
    @DisplayName("If the earlier digest is reported again, the run the pull interrupted embeds the seed document it lacked and is scored against every seed document")
    void theRunThePullInterruptedIsEmbeddedInFullWhenItsDigestIsReportedAgain(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds);
        EmbeddingScriptedBeans.pullsAgainOnceItHasEmbedded(CHUNKS_BEFORE_THE_PULL, A_SECOND_PULLS_DIGEST);
        cli.run("run", root.toString());
        List<String> theRunOfTheFirstDigest = scoringRunIdsFor(root);
        long seedsEmbeddedBeforeThePull = documentsWithVectorsUnder(seeds, EmbeddingScriptedBeans.DIGEST);
        // The runtime reports its first digest again, as after the earlier model is put back.
        EmbeddingScriptedBeans.servesEveryModel();

        cli.run("run", root.toString());

        claim(
                "the interrupted invocation had embedded " + ONE_SEED + " of the " + SEED_DOCUMENTS
                        + " seed documents under the first digest",
                () -> assertThat(seedsEmbeddedBeforeThePull).isEqualTo(ONE_SEED));
        claim(
                "the invocation under the first digest again reports success",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "it arrived at the run the pull interrupted and made no other",
                () -> assertThat(scoringRunIdsFor(root)).isEqualTo(theRunOfTheFirstDigest));
        claim(
                "both seed documents now have vectors under the first digest: embedding was not recorded as"
                        + " finished, so it was done again and reached the one the pull had taken",
                () -> assertThat(documentsWithVectorsUnder(seeds, EmbeddingScriptedBeans.DIGEST))
                        .isEqualTo(SEED_DOCUMENTS));
        claim(
                "embedding is recorded as finished under the run and all " + CORPUS_DOCUMENTS
                        + " corpus documents are scored under it",
                () -> {
                    assertThat(finishedRows(theRunOfTheFirstDigest.getFirst(), StepNames.EMBEDDING_SCORING))
                            .isEqualTo(RECORDED_ONCE);
                    assertThat(countUnder("relevance_score", theRunOfTheFirstDigest.getFirst()))
                            .isEqualTo(CORPUS_DOCUMENTS);
                });
    }

    /**
     * <b>The exit code alone does not tell here.</b> Before ADR-231 was built this invocation failed too,
     * later and for the state this test makes by hand: scoring went on against the one seed left and
     * recorded both scores again, and 6a, whose arrangement was recorded under the same scoring run before
     * the scores were computed again, stopped with {@code cluster 0 was arranged with no members} (ADR-231,
     * Context). So the test reads what the invocation said it stopped on.
     *
     * <p><b>The scores are the ones that stood, not none.</b> The step runs in one transaction, so the
     * refusal rolls back the discard the step begins with, as a survivor's refusal does. What stood here
     * stood only because this test removed the row recording that scoring finished.
     */
    @Test
    @Story("A seed document with no vector of the scoring's own model stops the scoring")
    @DisplayName("Scoring done again where one seed document's vectors are under another digest fails the invocation on that seed document and scores nothing anew")
    void aSeedWhoseVectorsAreUnderAnotherDigestStopsScoring(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        aCorpus(root, seeds);
        profile(seeds);
        cli.run("run", root.toString());
        String theRun = scoringRunIdsFor(root).getFirst();
        List<String> theScoresBefore = scoresUnder(theRun);
        oneSeedsVectorsMovedUnderAnotherDigest(seeds);
        forgetThatItFinished(theRun, StepNames.RELEVANCE_SCORING);
        int saidBefore = output.getAll().length();

        cli.run("run", root.toString());

        String saidByTheSecondInvocation = output.getAll().substring(saidBefore);
        claim(
                "the first invocation scored all " + CORPUS_DOCUMENTS + " corpus documents",
                () -> assertThat(theScoresBefore).hasSize(CORPUS_DOCUMENTS));
        claim(
                ONE_SEED + " of the " + SEED_DOCUMENTS + " seed documents is left with vectors under the digest"
                        + " the run names",
                () -> assertThat(documentsWithVectorsUnder(seeds, EmbeddingScriptedBeans.DIGEST))
                        .isEqualTo(ONE_SEED));
        claim("the second invocation fails", () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "and what it stopped on is the seed document: it says a seed has no stored vectors, and"
                        + " nothing about a group with no members, which is what a later step stopped on"
                        + " when scoring went on against the seed documents that were left",
                () -> assertThat(saidByTheSecondInvocation)
                        .contains("seed occurrence")
                        .contains("has no stored chunk vectors")
                        .doesNotContain("arranged with no members"));
        claim(
                "under the scoring run the first made",
                () -> assertThat(scoringRunIdsFor(root)).containsExactly(theRun));
        claim(
                "the " + CORPUS_DOCUMENTS + " scores under the run are the ones the first invocation recorded,"
                        + " each with the seed document it was computed against then: nothing was scored"
                        + " against the seed documents that were left",
                () -> assertThat(scoresUnder(theRun)).isEqualTo(theScoresBefore));
        claim(
                "and scoring is not recorded as finished, so the next invocation tries it again",
                () -> assertThat(finishedRows(theRun, StepNames.RELEVANCE_SCORING))
                        .isZero());
    }

    @Test
    @Story("A seed document with nothing to embed is left out and the scoring goes on")
    @DisplayName("A seed document whose only text is a page header has no vector, is not recorded as unusable, and does not stop the scoring")
    void aSeedWithNothingToEmbedIsLeftOutAndScoringGoesOn(
            @TempDir Path root, @TempDir Path seeds, CapturedOutput output) throws IOException {
        aCorpus(root, seeds);
        Files.writeString(
                seeds.resolve(SeedScriptedExtractionBeans.HEADER_ONLY_SEED),
                "a seed that converts to a page header alone, of " + seeds.getFileName());
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: a seed document that has nothing to embed is a fact about"
                        + " that document, known before anything is scored, and not a vector gone missing",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "no seed document is recorded as unusable: the page header is text, so the seed document"
                        + " produced some",
                () -> assertThat(unusableSeedsOf(seeds)).isZero());
        claim(
                "the " + SEED_DOCUMENTS + " seed documents with a body have vectors and the one with a page"
                        + " header alone has none",
                () -> assertThat(documentsWithVectorsUnder(seeds, EmbeddingScriptedBeans.DIGEST))
                        .isEqualTo(SEED_DOCUMENTS));
        claim(
                "all " + CORPUS_DOCUMENTS + " corpus documents are scored, and scoring is recorded as finished",
                () -> assertThat(scoringRunIdsFor(root)).singleElement().satisfies(run -> {
                    assertThat(countUnder("relevance_score", run)).isEqualTo(CORPUS_DOCUMENTS);
                    assertThat(finishedRows(run, StepNames.RELEVANCE_SCORING)).isEqualTo(RECORDED_ONCE);
                }));
        claim(
                "and the operator is told which seed document was left out and why",
                () -> assertThat(output.getAll()).contains("produced text and no chunk"));
    }

    /** How many seeds found beneath {@code seeds} are recorded as unusable, under any run. */
    private long unusableSeedsOf(Path seeds) {
        Long unusable = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM unusable_seed u JOIN file_occurrence f ON f.id = u.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                Long.class,
                walkRoot(seeds));
        return unusable == null ? 0 : unusable;
    }

    /** Each score under {@code run} with its document and the seed it was computed against, by document. */
    private List<String> scoresUnder(String run) {
        return jdbcTemplate.queryForList(
                "SELECT occurrence_id || ' ' || score || ' ' || winning_seed_occurrence_id FROM relevance_score"
                        + " WHERE run_id = ? ORDER BY occurrence_id",
                String.class,
                run);
    }

    /**
     * The vectors of one seed of {@code seeds}, the one of the least occurrence id, rewritten as stored under
     * the second pull's digest, so that the seed has none under the identity the run reads.
     */
    private void oneSeedsVectorsMovedUnderAnotherDigest(Path seeds) {
        jdbcTemplate.update(
                "UPDATE vector SET embedder_identity = REPLACE(embedder_identity, ?, ?)"
                        + " WHERE content_hash = (SELECT k.content_hash FROM extraction_cache_key k"
                        + " JOIN file_occurrence f ON f.id = k.occurrence_id JOIN walk w ON w.id = f.walk_id"
                        + " WHERE w.root = ? ORDER BY k.occurrence_id LIMIT 1)",
                EmbeddingScriptedBeans.DIGEST,
                A_SECOND_PULLS_DIGEST,
                walkRoot(seeds));
    }

    /**
     * How many file occurrences found beneath {@code walked} have a vector stored under {@code digest}, read
     * through the key their conversion was recorded under, which is the key a vector is stored by.
     */
    private long documentsWithVectorsUnder(Path walked, String digest) {
        Long documents = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT k.occurrence_id) FROM extraction_cache_key k"
                        + " JOIN file_occurrence f ON f.id = k.occurrence_id JOIN walk w ON w.id = f.walk_id"
                        + " WHERE w.root = ? AND EXISTS (SELECT 1 FROM vector v"
                        + " WHERE v.content_hash = k.content_hash AND v.embedder_identity LIKE ?)",
                Long.class,
                walkRoot(walked),
                "%;digest=" + digest + ";%");
        return documents == null ? 0 : documents;
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

    /** Two corpus documents and two seeds, each naming its folder so that no other test wrote the same bytes. */
    private void aCorpus(Path root, Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(
                    root.resolve("corpus-" + i + ".txt"), "a corpus document " + i + " of " + root.getFileName());
        }
        for (int i = 0; i < SEED_DOCUMENTS; i++) {
            Files.writeString(
                    seeds.resolve("seed-" + i + ".txt"), "a seed document " + i + " of " + seeds.getFileName());
        }
    }

    /** The seed folder named, stage 4's gate open, gate 3 open, and no threshold. */
    private void profile(Path seeds) {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
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

    private String settingsOf(String run) {
        return jdbcTemplate.queryForObject("SELECT config_consumed FROM run WHERE id = ?", String.class, run);
    }

    private long countUnder(String table, String run) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE run_id = ?", Long.class, run);
        return count == null ? 0 : count;
    }
}
