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
 * Stage 5's fifth step, end to end (ADR-087, #109): once every survivor carries a score and a winning
 * seed, each seed's partition is grouped into clusters under that same scoring run — rows, a size
 * report, and nothing else.
 *
 * <p>A sibling of {@link RelevanceScoringInvocationTest} for the reason that class is a sibling of
 * {@link EmbeddingScoringInvocationTest}: this ticket's own step reads what the step before it wrote
 * rather than re-deriving it.
 *
 * <p><b>What this step must be shown not to do matters as much as what it does.</b> Clustering
 * arranges what survived, so no verdict may appear because of it, and its two parameters are code
 * defaults — a profile that gained a key here would be a question shipped unset that nobody could
 * answer, since cluster granularity is only discoverable from output that does not exist yet.
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
    ClusteringJobConfiguration.class,
    ClusteringTasklet.class,
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
    ClusteringBeans.class,
    DocumentClusters.class,
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
    RelevanceLabels.class,
    VesperaCommand.Publish.class,
    VesperaCli.class
})
@Epic("Relevance")
@Feature("Clustering")
@Issue("109")
@Link(name = "ADR-087", url = Adr.CLUSTERS_ARE_MODULARITY_COMMUNITIES, type = "adr")
class ClusteringInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the same way {@link RelevanceScoringInvocationTest} does. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model this fixture names, so gate 3 -- and this ticket's own step -- open too. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** How many corpus documents this fixture walks, all of which survive to be clustered. */
    private static final int CORPUS_DOCUMENTS = 3;

    /** Every key the profile is allowed to carry after this step has run — the same five it had before. */
    private static final List<String> THE_KNOWN_PROFILE_KEYS = List.of(
            "seedFolder",
            "degenerateOutputConfidenceFloor",
            "boilerplateDocumentFrequencyFloor",
            "embeddingModel",
            "relevanceScoreFloor");

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
    @Story("Every survivor lands in exactly one cluster")
    @DisplayName("Every scored survivor is clustered under gate 3's own scoring run, with no verdict")
    void clustersEverySurvivorUnderTheScoringRun(@TempDir Path root, @TempDir Path seeds) throws IOException {
        for (int i = 0; i < CORPUS_DOCUMENTS; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "each of the " + CORPUS_DOCUMENTS + " scored survivors carries exactly one membership row,"
                        + " so membership over the partition is total and disjoint through the real"
                        + " pipeline and not only at the unit",
                () -> assertThat(documentClusterRowCountFor(root)).isEqualTo(CORPUS_DOCUMENTS));
        claim(
                "recorded under the very run the scores were written beneath -- clustering needs the"
                        + " vectors and the vectors need the model, so it belongs to the scoring run"
                        + " rather than to one of its own",
                () -> assertThat(documentClusterRunIdsFor(root)).containsOnly(scoringRunIdFor(root)));
        claim(
                "no verdict of any kind was written: clustering removes nothing, it arranges what"
                        + " survived, and the verdict vocabulary is closed (ADR-042) with nothing in it"
                        + " for this",
                () -> assertThat(verdictCountFor(root)).isZero());
    }

    @Test
    @Story("The parameters are code defaults, never profile keys")
    @DisplayName("Clustering adds no profile key, so nothing new ships unset")
    void addsNoProfileKey(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the profile carries the same five keys it did before this step existed: k and the"
                        + " resolution are operational numbers, and cluster granularity is a preference"
                        + " about page size discoverable only from output that does not exist yet -- a key"
                        + " for it would ship unset, gate nothing, and be unanswerable",
                () -> assertThat(topLevelProfileKeys())
                        .containsExactlyInAnyOrderElementsOf(THE_KNOWN_PROFILE_KEYS));
        claim(
                "and no edge similarity floor appears among them either: an unmeasured threshold is what"
                        + " observe-before-enforce refuses, and k already bounds the edges",
                () -> assertThat(topLevelProfileKeys()).noneSatisfy(key -> assertThat(key).contains("similarity")));
    }

    @Test
    @Story("The spread of cluster sizes is reported per partition")
    @DisplayName("The size report is written beside the profile, naming the exemplar's partition")
    void writesTheSizeReport(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        Path report = workingDirectory.resolve(ClusteringTasklet.CLUSTER_SIZES_FILE_NAME);
        claim(
                "the report was written to the working directory rather than into the corpus (ADR-054),"
                        + " beside the profile the operator was already told to look at",
                () -> assertThat(Files.exists(report)).isTrue());
        claim(
                "and it names the exemplar whose partition was grouped, so the sizes belong to something"
                        + " a reader can find",
                () -> assertThat(Files.readString(report)).contains("seed.txt"));
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

    /** The keys the profile file actually carries, read as text rather than through the record. */
    private List<String> topLevelProfileKeys() throws IOException {
        return Files.readAllLines(profileStore.file()).stream()
                .filter(line -> !line.isBlank() && !Character.isWhitespace(line.charAt(0)) && line.contains(":"))
                .map(line -> line.substring(0, line.indexOf(':')).trim())
                .toList();
    }

    /**
     * The spelling the walk recorded for {@code root}, which is the walk's own canonicalisation of it
     * (ADR-055) rather than the path this test handed the CLI.
     */
    private String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }

    /**
     * Every query below joins back to {@code root}'s own walk rather than reading the tables whole:
     * this database is shared across every {@code @Test} method in the class, so an absolute count
     * would silently read another test's rows too.
     */
    private long documentClusterRowCountFor(Path root) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_cluster c JOIN file_occurrence f ON f.id = c.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                Long.class,
                walkRoot(root));
        return count == null ? 0 : count;
    }

    private List<String> documentClusterRunIdsFor(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT c.run_id FROM document_cluster c JOIN file_occurrence f ON f.id = c.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                String.class,
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
