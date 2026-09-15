package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.embedding.ChunkEmbedderBeans;
import io.algernon.vespera.embedding.ClusteringBeans;
import io.algernon.vespera.embedding.DocumentClusters;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.embedding.RelevanceScoringBeans;
import io.algernon.vespera.embedding.SeedCorpusComparison;
import io.algernon.vespera.embedding.UnusableSeeds;
import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.HybridChunkerBeans;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.extraction.LeadingChunks;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.algernon.vespera.similarity.BoilerplateShingles;
import io.algernon.vespera.similarity.DocumentFrequency;
import io.algernon.vespera.similarity.RedundancyResolution;
import io.algernon.vespera.similarity.RedundancySignatures;
import io.algernon.vespera.similarity.Shingler;
import io.algernon.vespera.synthesis.ClusterSynthesis;
import io.algernon.vespera.synthesis.Clusters;
import io.algernon.vespera.synthesis.SynthesisDocs;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a second invocation over an unchanged corpus does to the work the first one recorded
 * (ADR-115, ADR-116, #191).
 *
 * <p><b>Several steps share one run, and that is what this class is about.</b> Fourteen of the job's
 * fifteen steps write under eight run identities: stage 4's two steps share one, the seed
 * measurement's two share one, and stage 5's scoring half has five steps under a single run. A
 * record of completion kept against the run therefore says "all of this is recorded" on the strength
 * of whichever step reached the end first, and the steps behind it are told their work is done. So
 * completion is recorded against the step that did the work, and every claim here is a claim about
 * one step rather than about a stage.
 *
 * <p>The fixture is a whole invocation rather than a tasklet on purpose: what these claims are about
 * is what two invocations do to each other, which nothing below the command line can show.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(BatchAutoConfiguration.class)
@Import({
    CensusJobConfiguration.class,
    GenerationJobConfiguration.class,
    GenerationTasklet.class,
    ClusterSynthesis.class,
    SynthesisDocs.class,
    LeadingChunks.class,
    GenerationScriptedBeans.class,
    GenerationRun.class,
    GenerationModel.class,
    CensusTasklet.class,
    ByteLevelReductionJobConfiguration.class,
    ByteLevelReductionTasklet.class,
    ExtractionJobConfiguration.class,
    ExtractionItemProcessor.class,
    ExtractionItemWriter.class,
    ExtractionRun.class,
    ExtractionTimeoutStreak.class,
    ExtractionCircuitBreaker.class,
    ExtractionHealthCheckListener.class,
    ContentCensusJobConfiguration.class,
    ContentCensusTasklet.class,
    ContentCensusRun.class,
    RedundancyJobConfiguration.class,
    RedundancyRun.class,
    RedundancyGate.class,
    RedundancyBoilerplate.class,
    RedundancySignatureItemWriter.class,
    RedundancyResolutionTasklet.class,
    SeedExtractionJobConfiguration.class,
    SeedExtractionItemProcessor.class,
    SeedExtractionItemWriter.class,
    SeedCorpusComparisonJobConfiguration.class,
    SeedCorpusComparisonTasklet.class,
    EmbeddingModelJobConfiguration.class,
    EmbeddingScoringTasklet.class,
    RelevanceScoringJobConfiguration.class,
    RelevanceScoringTasklet.class,
    RelevanceFloorJobConfiguration.class,
    RelevanceFloorTasklet.class,
    RelevanceFloor.class,
    ClusteringJobConfiguration.class,
    ClusteringTasklet.class,
    ArrangementJobConfiguration.class,
    ArrangementTasklet.class,
    io.algernon.vespera.extraction.DocumentTitles.class,
    ArrangementRun.class,
    ArrangementGate.class,
    Clusters.class,
    ClusteringBeans.class,
    DocumentClusters.class,
    RelevanceReportJobConfiguration.class,
    RelevanceReportTasklet.class,
    RelevanceDistribution.class,
    EmbeddingModelGate.class,
    SeedMeasurementRun.class,
    ScoringRun.class,
    SeedGate.class,
    UsableSeedGate.class,
    ChunkEmbedderBeans.class,
    RelevanceScoringBeans.class,
    EmbeddingScriptedBeans.class,
    RedundancySignatures.class,
    RedundancyResolution.class,
    BoilerplateShingles.class,
    DocumentFrequency.class,
    ConfidenceDistribution.class,
    SeedCorpusComparison.class,
    UnusableSeeds.class,
    Shingler.class,
    HybridChunkerBeans.class,
    SeedScriptedExtractionBeans.class,
    ExtractionMetrics.class,
    LanguageDetection.class,
    ContentIdentity.class,
    DetectedFormats.class,
    WalkRecorder.class,
    AnomalyLog.class,
    Ledger.class,
    ImplementationVersions.class,
    ProfileStore.class,
    NextAction.class,
    VesperaCommand.class,
    VesperaCommand.Run.class,
    VesperaCommand.Label.class,
    LabelIngestion.class,
    RelevanceLabels.class,
    VesperaCli.class
})
@Epic("Pipeline")
@Feature("Repeated invocation")
@Issue("191")
@Link(name = "ADR-116", url = Adr.A_RUNS_COMPLETION_IS_RECORDED_PER_STEP, type = "adr")
@Link(name = "ADR-115", url = Adr.A_REPEATED_OBSERVATION_IS_DISCARDED_AND_A_RUN_IS_CONTINUED, type = "adr")
class RepeatedInvocationTest {

    /** A floor of 1.0 opens stage 4's gate, so both of its steps run and share stage 4's run. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model this fixture names, so gate 3 opens and stage 5's five-step scoring half runs. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** How many corpus documents this fixture walks, every one of which survives to be clustered. */
    private static final int CORPUS_DOCUMENTS = 2;

    /** What each of those documents is worth once the job has run: one row each, per table. */
    private static final int ONE_ROW_EACH = CORPUS_DOCUMENTS;

    /** The step of stage 5's scoring half whose work the second claim below puts back. */
    private static final String THE_CLUSTERING_STEP = "clustering";

    /** The stage name stage 5's scoring run is recorded under. */
    private static final String THE_SCORING_STAGE = "embedding-scoring";

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

    @Test
    @Story("A second invocation over an unchanged corpus repeats nothing")
    @DisplayName("Invoking twice over an unchanged corpus succeeds and writes no second copy of anything")
    void aSecondInvocationOverAnUnchangedCorpusRepeatsNothing(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds);
        cli.run("run", root.toString());

        cli.run("run", root.toString());

        claim(
                "the second invocation reports success: everything it was asked to do was already"
                        + " recorded from the first, so it was left alone rather than done again",
                () -> assertThat(cli.getExitCode()).isZero());
        // Stage 4 is deliberately not asserted on here. This fixture's extractor answers every
        // document the same text, so every shingle occurs in all of them, and a boilerplate floor of
        // 1.0 -- the value that opens stage 4's gate -- strips all of them as boilerplate. Stage 4a
        // therefore signs an empty shingle set and writes no row for any document, whatever the step
        // completion records say, so a count here would pin the fixture rather than the behaviour.
        // The seed measurement below makes the same point about two steps sharing one run.
        claim(
                "the seed measurement still holds " + ONE_ROW_EACH + " rows for " + CORPUS_DOCUMENTS
                        + " documents rather than twice that: reading the seeds and comparing them are"
                        + " recorded together, and the first finishing does not speak for the second",
                () -> assertThat(rowsOver(root, "extraction_metric")).isEqualTo(ONE_ROW_EACH));
        claim(
                "each of the " + CORPUS_DOCUMENTS + " documents carries one relevance score rather than"
                        + " two, although five separate pieces of work are recorded together",
                () -> assertThat(rowsOver(root, "relevance_score")).isEqualTo(ONE_ROW_EACH));
        claim(
                "and each is in exactly one cluster -- the last of those five, and the one a shared"
                        + " record would have let the first answer for",
                () -> assertThat(rowsOver(root, "document_cluster")).isEqualTo(ONE_ROW_EACH));
    }

    @Test
    @Story("Work answers for itself, never for the work beside it")
    @DisplayName("Work that is missing is done again, although everything recorded alongside it is finished")
    void aStepWhoseWorkIsMissingDoesItAgainThoughItsStepMatesAreDone(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        profile(seeds);
        cli.run("run", root.toString());
        String scoringRun = scoringRunIdFor(root);
        aClusteringPassThatStoppedPartway(scoringRun);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success rather than colliding with the row the interrupted"
                        + " invocation left behind: work that is not recorded as finished throws away"
                        + " what it wrote before and does it again",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "each of the " + CORPUS_DOCUMENTS + " documents is in exactly one cluster again -- "
                        + ONE_ROW_EACH + " rows, neither the single row the interrupted invocation left"
                        + " nor three -- so the clustering was done afresh although everything recorded"
                        + " alongside it had finished",
                () -> assertThat(rowsOver(root, "document_cluster")).isEqualTo(ONE_ROW_EACH));
        claim(
                "and it was recorded under the same name as before, because nothing it reads had"
                        + " changed: unfinished work is carried on rather than started somewhere new",
                () -> assertThat(scoringRunIdFor(root)).isEqualTo(scoringRun));
        claim(
                "while the scoring beside it wrote nothing a second time, so redoing one piece of"
                        + " work is not redoing all of it",
                () -> assertThat(rowsOver(root, "relevance_score")).isEqualTo(ONE_ROW_EACH));
    }

    private void aCorpus(Path root, Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** The seed folder named, stage 4's gate open and gate 3 open, so every shared run is reached. */
    private void profile(Path seeds) {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }

    /**
     * What the ledger holds after a clustering pass that stopped partway: one membership row written,
     * the other never reached, and no record that the step finished.
     *
     * <p>Written straight into the tables because no invocation can be stopped in the middle of one
     * step from out here. The completion record is removed by the name of the step it belongs to,
     * which is what the claims above turn on: the steps sharing this run keep theirs.
     */
    private void aClusteringPassThatStoppedPartway(String scoringRun) {
        jdbcTemplate.update(
                "DELETE FROM finished_step WHERE run_id = ? AND step = ?", scoringRun, THE_CLUSTERING_STEP);
        jdbcTemplate.update(
                "DELETE FROM document_cluster WHERE run_id = ? AND occurrence_id ="
                        + " (SELECT MIN(occurrence_id) FROM document_cluster WHERE run_id = ?)",
                scoringRun,
                scoringRun);
    }

    /** The one scoring run recorded over this corpus. */
    private String scoringRunIdFor(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = ? AND w.root = ?",
                String.class,
                THE_SCORING_STAGE,
                walkRoot(root));
    }

    /** How many rows the named table holds against occurrences of this corpus, over every run. */
    private int rowsOver(Path root, String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " t JOIN file_occurrence f ON f.id = t.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                Integer.class,
                walkRoot(root));
        return count == null ? 0 : count;
    }

    private String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }
}
