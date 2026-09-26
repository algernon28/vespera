package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.ContentHash;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Which verdicts a run's survivors answer to, once one walk holds several runs of a stage (ADR-156,
 * #297).
 *
 * <p>ADR-115 made a walk reusable, so an unchanged archive keeps its walk and every run ever minted
 * over it. A changed relevance floor mints a new scoring run beside the old one (ADR-117), and each
 * stage names the run this invocation arrived at (ADR-154). Until ADR-156 the survivors query counted
 * a blocking verdict from any run of the walk, so a stricter floor's removals went on removing after
 * the floor was lowered, and the lower floor's run scored and clustered nothing. ADR-156 reads a run's
 * survivors through that run and the runs upstream of it: a verdict under any other run is kept and
 * removes nothing.
 *
 * <p>The fixture is {@link RelevanceFloorInvocationTest}'s, the one ADR-154 measured the fault on. The
 * scripted embedder answers every chunk with the same vector, so every document scores the same, and
 * a floor above that score removes both documents while one below it removes neither.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("Repeated invocation")
@Issue("297")
@Link(name = "ADR-156", url = Adr.A_RUNS_SURVIVORS_ARE_READ_THROUGH_ITS_UPSTREAM_RUNS, type = "adr")
@Link(name = "ADR-154", url = Adr.A_STAGE_READS_THE_UPSTREAM_RUN_THIS_INVOCATION_ARRIVED_AT, type = "adr")
@Link(name = "ADR-117", url = Adr.THE_RELEVANCE_FLOOR_JOINS_THE_SCORING_RUNS_IDENTITY, type = "adr")
class SurvivalOverAReusedWalkTest {

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** Above anything the scripted embedder can produce, so every scored document falls under it. */
    private static final String A_STRICT_FLOOR = "1.5";

    /** Below anything it can produce, so every scored document clears it. */
    private static final String A_LOOSE_FLOOR = "0.1";

    private static final int CORPUS_DOCUMENTS = 2;

    /** One scoring run under no floor, one under the strict floor, one under the loose floor. */
    private static final int THREE_SCORING_RUNS = 3;

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
    private Ledger ledger;

    @Autowired
    private RelevanceDistribution relevanceDistribution;

    @Autowired
    private RelevanceLabels relevanceLabels;

    @Test
    @Story("Loosening a floor brings back what a stricter one removed")
    @DisplayName("A floor lowered over a reused walk scores, clusters and arranges the documents a higher one removed")
    void aLoweredFloorBringsTheDocumentsBack(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        String theRunWithNoFloor = theOnlyScoringRun(root);
        anAnswerGivenUnderThisRunsModel(root, seeds);
        profile(seeds, A_STRICT_FLOOR);
        cli.run("run", root.toString());
        String theStrictRun = theScoringRunNotIn(root, List.of(theRunWithNoFloor));
        profile(seeds, A_LOOSE_FLOOR);

        cli.run("run", root.toString());

        String theLooseRun = theScoringRunNotIn(root, List.of(theRunWithNoFloor, theStrictRun));
        claim(
                "the invocation completes, and the lowered floor is a scoring run of its own, so this corpus"
                        + " now carries " + THREE_SCORING_RUNS + " scoring runs (ADR-117)",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(scoringRunIdsFor(root)).hasSize(THREE_SCORING_RUNS);
                });
        claim(
                "both documents were scored under the loose run. The strict run's below-threshold verdicts"
                        + " sit under a run the loose run does not name upstream, so they remove nothing"
                        + " from it -- before ADR-156 they removed both, and this run scored nothing",
                () -> assertThat(relevanceScoreCountUnder(theLooseRun)).isEqualTo(CORPUS_DOCUMENTS));
        claim(
                "and both were clustered under it, which is what #297 measured as zero",
                () -> assertThat(documentClusterCountUnder(theLooseRun)).isEqualTo(CORPUS_DOCUMENTS));
        claim(
                "and both reached the arrangement made over the loose run, so arrangement.html shows the"
                        + " operator the documents the lower floor keeps",
                () -> assertThat(documentsArrangedOver(theLooseRun)).isEqualTo(CORPUS_DOCUMENTS));
        claim(
                "the strict run's " + CORPUS_DOCUMENTS + " below-threshold verdicts are still recorded:"
                        + " nothing is deleted, and putting the strict floor back picks them up again",
                () -> assertThat(belowThresholdCountUnder(theStrictRun)).isEqualTo(CORPUS_DOCUMENTS));
        claim(
                "and they still remove both documents from the strict run's own survivors, so a verdict"
                        + " under the run it was written under keeps its force",
                () -> assertThat(ItemStreamReaders.drain(ledger.survivors(new RunId(theStrictRun))))
                        .isEmpty());
        claim(
                "while the loose run's survivors hold both",
                () -> assertThat(ItemStreamReaders.drain(ledger.survivors(new RunId(theLooseRun))))
                        .hasSize(CORPUS_DOCUMENTS));
    }

    @Test
    @Story("Loosening a floor brings back what a stricter one removed")
    @DisplayName("Putting the stricter floor back removes the documents again, and mints nothing")
    void puttingTheStricterFloorBackRemovesThemAgain(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profile(seeds, null);
        cli.run("run", root.toString());
        String theRunWithNoFloor = theOnlyScoringRun(root);
        anAnswerGivenUnderThisRunsModel(root, seeds);
        profile(seeds, A_STRICT_FLOOR);
        cli.run("run", root.toString());
        String theStrictRun = theScoringRunNotIn(root, List.of(theRunWithNoFloor));
        profile(seeds, A_LOOSE_FLOOR);
        cli.run("run", root.toString());
        profile(seeds, A_STRICT_FLOOR);

        cli.run("run", root.toString());

        claim(
                "the invocation completes and mints no scoring run: it re-derives the strict run's id and"
                        + " continues it, so the corpus still carries " + THREE_SCORING_RUNS + " (ADR-115)",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(scoringRunIdsFor(root)).hasSize(THREE_SCORING_RUNS);
                });
        claim(
                "and both documents stand removed under the run it arrived at, by the verdicts written"
                        + " there two invocations ago -- kept, not recomputed, and counting again because"
                        + " that run is the one the profile names now",
                () -> {
                    assertThat(belowThresholdCountUnder(theStrictRun)).isEqualTo(CORPUS_DOCUMENTS);
                    assertThat(ItemStreamReaders.drain(ledger.survivors(new RunId(theStrictRun))))
                            .isEmpty();
                });
    }

    @Test
    @Story("A stage names the run whose verdicts it is meant to see")
    @DisplayName("A document stage 4 removed is neither measured against the seeds nor scored")
    void whatStageFourRemovedIsNotMeasuredOrScored(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        profileUpToStageFour();
        cli.run("run", root.toString());
        // This fixture's documents share every fragment of text, so the boilerplate floor strips them all
        // and stage 4 removes nothing by itself. The verdict is written under the stage 4 run the next
        // invocation continues, standing in for a document stage 4 did find redundant.
        OccurrenceId removedByStageFour = anOccurrenceIn(root);
        String removedContentHash = ContentHash.sha256(root.resolve(aDocumentIn(root)));
        String keptContentHash = ContentHash.sha256(root.resolve(theOtherDocumentIn(root)));
        ledger.verdict(
                removedByStageFour,
                new RunId(theOnlyRunOf(root, "content-redundancy")),
                VerdictKind.REDUNDANT_WITH,
                "written by this test");
        profile(seeds, null);

        cli.run("run", root.toString());

        String measurementRun = theOnlyRunOf(root, "seed-measurement");
        String scoringRun = theOnlyScoringRun(root);
        claim(
                "the seed/corpus comparison counted one corpus document, not " + CORPUS_DOCUMENTS + ": its"
                        + " corpus side is what stage 4 left standing (ADR-089), so it reads survivors"
                        + " under the measurement run, whose upstream is stage 4's -- read under stage 2's"
                        + " run, it would count the redundant document too",
                () -> assertThat(corpusDocumentCountUnder(measurementRun)).isEqualTo(CORPUS_DOCUMENTS - 1));
        claim(
                "and scoring scored one document, for the same reason: embedding and scoring read what"
                        + " stage 4 left standing, never what stage 2 did",
                () -> assertThat(relevanceScoreCountUnder(scoringRun)).isEqualTo(CORPUS_DOCUMENTS - 1));
        claim(
                "and the redundant document was never embedded: no vector is recorded for its content,"
                        + " while the document stage 4 left standing has its vectors. Scoring's count alone"
                        + " would not show this -- embedding the redundant document under stage 2's"
                        + " survivors would still leave scoring one document to score",
                () -> {
                    assertThat(vectorCountFor(keptContentHash)).isPositive();
                    assertThat(vectorCountFor(removedContentHash)).isZero();
                });
    }

    private void aCorpus(Path root, Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            // The root is in the content so no other test's corpus shares these content hashes: a vector
            // is keyed by content, not by run (ADR-085), and this context's database outlives a method.
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i + " under " + root);
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

    /** Stage 4's gate open and nothing after it: no seed folder, so stage 5 mints no run. */
    private void profileUpToStageFour() {
        profileStore.save(ProfileFixture.profile()
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .build());
    }

    /** One answer recorded under the model this run scored with, which makes a floor applicable. */
    private void anAnswerGivenUnderThisRunsModel(Path root, Path seeds) {
        relevanceLabels.record(
                new OccurrencePath(aDocumentIn(root)),
                Walk.canonicalRoot(seeds).toString(),
                true,
                new RunId(scoringRunIdsFor(root).getFirst()),
                1.0,
                relevanceDistribution.embedderIdentityFor(MODEL_NAME).orElseThrow());
    }

    private String aDocumentIn(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT f.path FROM file_occurrence f JOIN walk w ON w.id = f.walk_id"
                        + " WHERE w.root = ? ORDER BY f.path LIMIT 1",
                String.class,
                walkRoot(root));
    }

    private String theOtherDocumentIn(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT f.path FROM file_occurrence f JOIN walk w ON w.id = f.walk_id"
                        + " WHERE w.root = ? ORDER BY f.path LIMIT 1 OFFSET 1",
                String.class,
                walkRoot(root));
    }

    private OccurrenceId anOccurrenceIn(Path root) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT f.id FROM file_occurrence f JOIN walk w ON w.id = f.walk_id"
                        + " WHERE w.root = ? ORDER BY f.path LIMIT 1",
                Long.class,
                walkRoot(root));
        return new OccurrenceId(id);
    }

    private String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }

    private List<String> runIdsOf(Path root, String stage) {
        return jdbcTemplate.queryForList(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = ? AND w.root = ? ORDER BY run.id",
                String.class,
                stage,
                walkRoot(root));
    }

    private List<String> scoringRunIdsFor(Path root) {
        return runIdsOf(root, "embedding-scoring");
    }

    private String theOnlyRunOf(Path root, String stage) {
        List<String> runs = runIdsOf(root, stage);
        assertThat(runs).as("the runs of stage %s over this corpus", stage).hasSize(1);
        return runs.getFirst();
    }

    private String theOnlyScoringRun(Path root) {
        return theOnlyRunOf(root, "embedding-scoring");
    }

    /**
     * The scoring run that is none of {@code earlier} -- the one the latest invocation minted.
     *
     * <p>Named by exclusion rather than by taking the last row: a run id is content-derived (ADR-048),
     * so ordering by it says nothing about which invocation came first.
     */
    private String theScoringRunNotIn(Path root, List<String> earlier) {
        return scoringRunIdsFor(root).stream()
                .filter(run -> !earlier.contains(run))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the latest invocation minted no scoring run"));
    }

    private long relevanceScoreCountUnder(String runId) {
        return count("SELECT COUNT(*) FROM relevance_score WHERE run_id = ?", runId);
    }

    private long documentClusterCountUnder(String runId) {
        return count("SELECT COUNT(*) FROM document_cluster WHERE run_id = ?", runId);
    }

    private long belowThresholdCountUnder(String runId) {
        return count("SELECT COUNT(*) FROM verdict WHERE run_id = ? AND kind = 'BELOW_THRESHOLD'", runId);
    }

    /** How many documents the arrangement made over {@code scoringRunId} places in its clusters. */
    private long documentsArrangedOver(String scoringRunId) {
        return count(
                "SELECT COALESCE(SUM(c.document_count), 0) FROM cluster c"
                        + " JOIN run r ON r.id = c.run_id"
                        + " JOIN run_upstream u ON u.run_id = r.id"
                        + " WHERE r.stage = 'arrangement' AND u.upstream_run_id = ?",
                scoringRunId);
    }

    /** The corpus side's document count the seed/corpus comparison recorded under {@code runId}. */
    private long corpusDocumentCountUnder(String runId) {
        return count(
                "SELECT MAX(corpus_document_count) FROM seed_corpus_comparison WHERE run_id = ?", runId);
    }

    private long vectorCountFor(String contentHash) {
        return count("SELECT COUNT(*) FROM vector WHERE content_hash = ?", contentHash);
    }

    private long count(String sql, Object... arguments) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return count == null ? 0 : count;
    }
}
