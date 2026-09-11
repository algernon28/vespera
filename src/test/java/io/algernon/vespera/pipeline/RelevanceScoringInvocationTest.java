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

/**
 * Stage 5's fourth step, end to end (ADR-020, #108): once gate 3 has embedded every vector, a corpus
 * survivor is scored against the resident seed set and the result lands under the scoring run gate 3
 * already minted -- one row, no verdict, and never a row at all for a document with no chunks.
 *
 * <p>A sibling of {@link EmbeddingScoringInvocationTest} for the same reason that class is a sibling
 * of {@link SeedCorpusComparisonInvocationTest}: this ticket's own step reads what #107's already
 * wrote rather than re-deriving it.
 */
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
    RelevanceFloorJobConfiguration.class,
    RelevanceFloorTasklet.class,
    RelevanceFloor.class,
    ClusteringJobConfiguration.class,
    ClusteringTasklet.class,
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
    VesperaCommand.Publish.class,
    VesperaCli.class
})
@Epic("Relevance")
@Feature("Relevance scoring")
@Issue("108")
@Link(name = "ADR-020", url = Adr.RELEVANCE_SCORING_FUNCTION, type = "adr")
class RelevanceScoringInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the same way {@link SeedCorpusComparisonInvocationTest} does. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model this fixture names, so gate 3 -- and this ticket's own step -- open too. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

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
    @Story("A survivor is scored under the scoring run gate 3 minted")
    @DisplayName("A corpus survivor's relevance score lands under gate 3's own scoring run, with no verdict")
    void scoresTheSurvivorUnderTheScoringRunGate3Minted(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the surviving corpus document was scored, its score landing in relevance_score rather"
                        + " than nowhere",
                () -> assertThat(relevanceScoreRowCountFor(root)).isEqualTo(1));
        claim(
                "the score was recorded under the very run gate 3 minted for embedding -- the row this"
                        + " step wrote and the vectors #107's step wrote share one run",
                () -> assertThat(relevanceScoreRunIdsFor(root)).containsExactly(scoringRunIdFor(root)));
        claim(
                "a winning seed occurrence is stored alongside the score, ADR-020's argmax having"
                        + " something to point at rather than the score standing alone",
                () -> assertThat(winningSeedOccurrenceIdFor(root)).isPositive());
        claim(
                "no verdict of any kind was written against this survivor for the relevance score itself"
                        + " -- the floor that would read this row into a below-threshold verdict is a"
                        + " later ticket, and until it lands nothing here removes anything",
                () -> assertThat(verdictCountFor(root)).isZero());
    }

    @Test
    @Story("A survivor with no chunks is empty by construction, confirmed rather than assumed")
    @DisplayName("A corpus document with no usable text never reaches relevance scoring at all")
    void aDocumentWithNoUsableTextIsNeverScored(@TempDir Path root, @TempDir Path seeds) throws IOException {
        // Named after SeedScriptedExtractionBeans.EMPTY_SEED: the shared scripted extractor answers
        // this file name with no text at all, wherever it appears -- corpus side or seed side.
        Files.writeString(root.resolve(SeedScriptedExtractionBeans.EMPTY_SEED), "stands in for a scan with no text");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success -- a document with no text is condemned by stage 2's"
                        + " tier-1 floor, not a failed invocation",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the no-text document earned stage 2's degenerate-output verdict, which is what removes"
                        + " it from every survivors query reaching stage 5",
                () -> assertThat(verdictCountFor(root)).isPositive());
        claim(
                "and consequently no relevance_score row exists for it at all: ADR-020's 'confirm, do"
                        + " not assume' holds end to end through the real pipeline, not only at the unit"
                        + " where RelevanceScoring would refuse to score an empty vector list",
                () -> assertThat(relevanceScoreRowCountFor(root)).isZero());
    }

    /** The seed folder named, stage 4's gate open, and gate 3 open too. */
    private void profile(Path seeds) {
        Profile profile = profileStore.load();
        profileStore.save(new Profile(
                new ProfileValue(seeds.toString(), "set by this test", null),
                profile.degenerateOutputConfidenceFloor(),
                new ProfileValue(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open", null),
                new ProfileValue(MODEL_NAME, "set by this test, so gate 3 is open", null)));
    }

    /**
     * The spelling the walk recorded for {@code root}, which is the walk's own canonicalisation of
     * it (ADR-055) rather than the path this test handed the CLI. The two differ wherever the
     * temporary directory is reached through a link or a short name -- a Windows runner whose
     * {@code %TEMP%} sits under {@code RUNNER~1} records the expanded {@code runneradmin} -- and a
     * query joining on the un-canonicalised spelling would then match no row and read as an empty
     * result rather than a mismatch.
     */
    private String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }

    /**
     * Every query below joins back to {@code root}'s own walk rather than reading the tables whole:
     * this database is shared across every {@code @Test} method in the class (one Spring context, one
     * connection pool), so an absolute count would silently read another test's rows too.
     */
    private long relevanceScoreRowCountFor(Path root) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relevance_score r JOIN file_occurrence f ON f.id = r.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                Long.class,
                walkRoot(root));
        return count == null ? 0 : count;
    }

    private List<String> relevanceScoreRunIdsFor(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.run_id FROM relevance_score r JOIN file_occurrence f ON f.id = r.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                String.class,
                walkRoot(root));
    }

    private long winningSeedOccurrenceIdFor(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT r.winning_seed_occurrence_id FROM relevance_score r"
                        + " JOIN file_occurrence f ON f.id = r.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                Long.class,
                walkRoot(root));
    }

    private String scoringRunIdFor(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT run.id FROM run JOIN walk w ON w.id = run.walk_id"
                        + " WHERE run.stage = 'embedding-scoring' AND w.root = ?",
                String.class,
                walkRoot(root));
    }

    private long verdictCountFor(Path root) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verdict v JOIN file_occurrence f ON f.id = v.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                Long.class,
                walkRoot(root));
        return count == null ? 0 : count;
    }
}
