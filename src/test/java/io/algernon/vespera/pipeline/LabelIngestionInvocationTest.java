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
import io.algernon.vespera.embedding.RelevanceScoringBeans;
import io.algernon.vespera.embedding.SeedCorpusComparison;
import io.algernon.vespera.embedding.UnusableSeeds;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.HybridChunkerBeans;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.algernon.vespera.similarity.BoilerplateShingles;
import io.algernon.vespera.similarity.DocumentFrequency;
import io.algernon.vespera.similarity.RedundancyResolution;
import io.algernon.vespera.similarity.RedundancySignatures;
import io.algernon.vespera.similarity.Shingler;
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

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(BatchAutoConfiguration.class)
@Import({
    CensusJobConfiguration.class,
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
    ClusteringJobConfiguration.class,
    ClusteringTasklet.class,
    ClusteringBeans.class,
    DocumentClusters.class,
    RelevanceReportJobConfiguration.class,
    RelevanceReportTasklet.class,
    RelevanceDistribution.class,
    RelevanceLabels.class,
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
    VesperaCommand.class,
    VesperaCommand.Run.class,
    VesperaCommand.Label.class,
    LabelIngestion.class,
    VesperaCommand.Publish.class,
    VesperaCli.class
})
/**
 * Ingesting a completed label file through the command an operator actually types (ADR-088, #105).
 *
 * <p>Label ingestion is its own subcommand rather than a step in the job, because it is a different
 * act: a person invokes it, having just finished labelling, and it writes rows under no run. The
 * claims here are about that act -- what it records, what it refuses, and that it mints nothing.
 */
@Epic("Relevance")
@Feature("Labelling")
@Issue("111")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
class LabelIngestionInvocationTest {

    /** A floor of 1.0 opens stage 4's gate, the way every stage-5 invocation fixture does. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model this fixture names, so gate 3 and the steps behind it open. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** The file the scoring run writes for a person to answer. */
    private static final String LABEL_FILE = "relevance-labels.yaml";

    /** Built rather than written literally, so the fixture carries no escaped quotes. */
    private static final String QUOTE = String.valueOf('"');

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
    @Story("Answers become rows, and nothing else changes")
    @DisplayName("A completed file is ingested, its answers recorded, and no run is minted for it")
    void ingestsACompletedFileAndMintsNoRun(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);
        long runsBefore = runCount();
        answerEveryQuestion();

        cli.run("label");

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the answer a person gave is now a row, which is the whole point of the command",
                () -> assertThat(labelCount(seeds)).isEqualTo(1));
        claim(
                "and no run was minted for it: a label is a fact about a document rather than something"
                        + " derived under a configuration, so nothing about ingesting one is a run",
                () -> assertThat(runCount()).isEqualTo(runsBefore));
        claim(
                "nor did it write a verdict of any kind -- the floor that reads these answers into a"
                        + " removal is a later ticket, and until it lands nothing here removes anything",
                () -> assertThat(verdictCount()).isZero());
    }

    @Test
    @Story("Answers become rows, and nothing else changes")
    @DisplayName("Ingesting the same file twice leaves the answer standing once")
    void ingestingTwiceLeavesOneRow(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);
        answerEveryQuestion();

        cli.run("label");
        cli.run("label");

        claim(
                "one answer was given, so one row stands: running the command again is something an"
                        + " operator will do, and it must not turn one judgement into two",
                () -> assertThat(labelCount(seeds)).isEqualTo(1));
    }

    @Test
    @Story("A file that is not about this sample is refused")
    @DisplayName("A file generated under a different run is refused, and records nothing")
    void refusesAFileFromADifferentRun(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);
        answerEveryQuestion();
        Path stale = workingDirectory.resolve("stale-labels.yaml");
        String staleStamp = "generatedUnderRun: " + QUOTE + "a-run-that-never-was" + QUOTE;
        Files.writeString(
                stale,
                Files.readString(workingDirectory.resolve(LABEL_FILE))
                        .replaceFirst("generatedUnderRun: .*", staleStamp));

        cli.run("label", stale.toString());

        claim(
                "the invocation reports failure, because an operator who labelled the wrong file needs to"
                        + " be stopped rather than quietly given a result",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "and nothing from it was recorded: the entries whose paths line up would be answers to"
                        + " questions this sample never asked",
                () -> assertThat(labelCount(seeds)).isZero());
    }

    @Test
    @Story("A file that is not about this sample is refused")
    @DisplayName("Naming a file that is not there is refused rather than passed over")
    void refusesAFileThatIsNotThere(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);

        cli.run("label", workingDirectory.resolve("nothing-here.yaml").toString());

        claim(
                "the invocation reports failure: someone typing this believes they have finished"
                        + " labelling, and exiting zero would tell them their answers landed when the file"
                        + " they named does not even exist",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim("and no answer was recorded", () -> assertThat(labelCount(seeds)).isZero());
    }

    @Test
    @Story("A file that is not about this sample is refused")
    @DisplayName("A file nobody has answered yet is refused, rather than reported as a pass of nothing")
    void refusesAFileWithNoAnswersYet(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aScoredCorpus(root, seeds);

        cli.run("label");

        claim(
                "the invocation reports failure, because succeeding on a file with every answer still"
                        + " blank would tell the operator sixty judgements landed when none did",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim("and no answer was recorded", () -> assertThat(labelCount(seeds)).isZero());
    }

    /** Runs the pipeline far enough that a sample exists and a label file has been written. */
    private void aScoredCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(new Profile(
                new ProfileValue(seeds.toString(), "set by this test", null),
                profile.degenerateOutputConfidenceFloor(),
                new ProfileValue(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open", null),
                new ProfileValue(MODEL_NAME, "set by this test, so gate 3 is open", null)));
        cli.run("run", root.toString());
    }

    /** Fills in every blank answer, the way a person working through the file would. */
    private void answerEveryQuestion() throws IOException {
        Path labels = workingDirectory.resolve(LABEL_FILE);
        Files.writeString(labels, Files.readString(labels).replace("relevant: null", "relevant: true"));
    }

    /**
     * Answers recorded about this test's own seed folder. The database outlives each test method, so
     * counting every row would count another test's answers -- and scoping by seed set is also what
     * a label is keyed by, so the count asks the same question the table answers.
     */
    private long labelCount(Path seeds) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relevance_label WHERE seed_set = ?",
                Long.class,
                io.algernon.vespera.corpus.Walk.canonicalRoot(seeds).toString());
    }

    private long runCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM run", Long.class);
    }

    private long verdictCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM verdict", Long.class);
    }
}
