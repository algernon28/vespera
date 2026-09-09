package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormats;
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
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.ProfileStore;
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
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.SimpleJob;
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
import picocli.CommandLine;

/**
 * One invocation, from the command line down to the rows (ADR-047, ADR-054).
 *
 * <p>What this covers is the wiring, and only the wiring: that {@code vespera run <root>} reaches the
 * job, that the job's one step reaches census, and that the root the operator typed arrives as the
 * root that gets walked. Everything census then does is pinned by {@link CensusTaskletTest}.
 *
 * <p>It is a slice rather than the whole application, because the whole application starts Chroma
 * and Ollama and this question does not involve either. The one non-obvious piece is
 * {@code @Transactional(NOT_SUPPORTED)}: the census step deliberately runs outside a transaction so
 * that a walk commits at its own checkpoints, and a test-managed transaction wrapped around it would
 * be suspended and then hold the only connection the test datasource has.
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
    SeedMeasurementRun.class,
    SeedGate.class,
    UsableSeedGate.class,
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
    ScoringRun.class,
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
    StubbedExtractionBeans.class,
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
@Epic("Census")
@Feature("Invocation")
@Issue("11")
@Link(name = "ADR-054", url = Adr.CORPUS_IS_ITS_ROOT_PATH, type = "adr")
class CensusInvocationTest {

    /** The working directory the profile is written to; the corpus root is the command's argument. */
    @TempDir
    static Path workingDirectory;

    /** Files written under the corpus root, so a claim can say where its number comes from. */
    private static final int CORPUS_FILES = 2;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private Ledger ledger;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private Job vesperaJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What census does in one invocation")
    @DisplayName("vespera run <root> walks that root and leaves the profile beside the database")
    void runsCensusOverTheRootTheOperatorNamed(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "a");
        Files.writeString(root.resolve("b.txt"), "b");

        cli.run("run", root.toString());

        claim(
                "the command reported success, which is the only report an unattended invocation makes",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the " + CORPUS_FILES + " files under the root the operator named were recorded",
                () -> assertThat(ledger.occurrenceCount(theWalk())).isEqualTo(CORPUS_FILES));
        claim(
                "the walk finished, so what it recorded may be judged",
                () -> assertThat(ledger.walkFinished(theWalk())).isTrue());
        claim(
                "the profile was written to the working directory rather than into the corpus",
                () -> assertThat(profileStore.file().startsWith(workingDirectory)).isTrue());
        claim(
                "and it is there to be edited",
                () -> assertThat(Files.exists(profileStore.file())).isTrue());
    }

    @Test
    @Story("What census does in one invocation")
    @DisplayName("The job is one job named for the tool, with census as its first step")
    @Link(name = "ADR-047", url = Adr.THE_PIPELINE_NEVER_BLOCKS, type = "adr")
    void assemblesOneJobForTheWholePipeline() {
        claim(
                "there is one job, named vespera, for later slices to add their stages to rather than to"
                        + " stand beside",
                () -> assertThat(vesperaJob.getName()).isEqualTo("vespera"));
    }

    @Test
    @Story("What census does in one invocation")
    @DisplayName("The stages run in cheapest-filter-first order, with the content census last")
    @Link(name = "ADR-075", url = Adr.STAGE_3_WRITES_A_CONFIDENCE_DISTRIBUTION_REPORT, type = "adr")
    @Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
    @Link(name = "ADR-086", url = Adr.SEED_CORPUS_MISMATCH_IS_MEASURED_AND_REPORTED, type = "adr")
    @Issue("106")
    void runsTheStagesInOrderWithTheContentCensusAfterExtraction() {
        List<String> stagesInOrder = List.copyOf(((SimpleJob) vesperaJob).getStepNames());

        claim(
                "the stages built so far run in the order they filter in -- census, then the byte-level"
                        + " reduction, then extraction, then the content census, then redundancy in its two"
                        + " steps, then the seed set stage 5 scores against, then how far that seed set"
                        + " resembles the documents still standing, then every vector gate 3 embeds, then"
                        + " every survivor's relevance score, then the threshold that removes what"
                        + " scored under it, then each seed partition grouped into clusters, and last the page and the questions a person needs in order to"
                        + " choose a cut -- so each pass only ever measures what the cheaper passes"
                        + " before it left standing, and nothing is put to a person until every score it"
                        + " would be read against exists",
                () -> assertThat(stagesInOrder)
                        .containsExactly(
                                "census",
                                "byte-level-reduction",
                                "extraction",
                                "content-census",
                                "redundancy-signature",
                                "content-redundancy",
                                "seed-extraction",
                                "seed-corpus-comparison",
                                "embedding-scoring",
                                "relevance-scoring",
                                "relevance-floor",
                                "clustering",
                                "relevance-report"));
        claim(
                "and the content census in particular runs after extraction rather than beside it: it"
                        + " summarises a whole extraction pass, and a summary computed over a pass still"
                        + " running would shift every time that pass resumed mid-corpus, quietly calibrating"
                        + " a threshold against part of a corpus",
                () -> assertThat(stagesInOrder.indexOf("content-census"))
                        .isGreaterThan(stagesInOrder.indexOf("extraction")));
        claim(
                "and redundancy runs after the content census, in that order: it compares documents against"
                        + " the boilerplate the census measured, and comparing before that measurement exists"
                        + " would let two documents sharing nothing but a footer read as the same document",
                () -> assertThat(stagesInOrder.indexOf("redundancy-signature"))
                        .isGreaterThan(stagesInOrder.indexOf("content-census")));
        claim(
                "and a signature is written before anything is resolved against it, since resolution reads"
                        + " the rows the signature step wrote",
                () -> assertThat(stagesInOrder.indexOf("content-redundancy"))
                        .isGreaterThan(stagesInOrder.indexOf("redundancy-signature")));
        claim(
                "and the seed set is compared against the corpus only after it has been extracted, since"
                        + " the comparison reads the measurements that extraction produced: comparing first"
                        + " would report a seed set of no documents against the whole corpus",
                () -> assertThat(stagesInOrder.indexOf("seed-corpus-comparison"))
                        .isGreaterThan(stagesInOrder.indexOf("seed-extraction")));
        claim(
                "and after redundancy too, so the documents the seeds are compared against are the ones"
                        + " that would actually be scored -- a document ruled out as redundant is not one"
                        + " of them",
                () -> assertThat(stagesInOrder.indexOf("seed-corpus-comparison"))
                        .isGreaterThan(stagesInOrder.indexOf("content-redundancy")));
        claim(
                "and relevance scoring runs after gate 3 embeds every vector, since scoring reads the"
                        + " rows that step wrote rather than re-deriving them (#108, blocked by #107)",
                () -> assertThat(stagesInOrder.indexOf("relevance-scoring"))
                        .isGreaterThan(stagesInOrder.indexOf("embedding-scoring")));
        claim(
                "and clustering runs after relevance scoring, because a partition is the set of documents"
                        + " one seed won: clustering before the winning seeds were recorded would have no"
                        + " partition to run within, and running it corpus-wide instead is what ADR-045"
                        + " forbids (#109)",
                () -> assertThat(stagesInOrder.indexOf("clustering"))
                        .isGreaterThan(stagesInOrder.indexOf("relevance-scoring")));
        claim(
                "and the threshold runs between scoring and clustering, which is the whole of ADR-087's"
                        + " 'a below-threshold document costs nothing': a document removed here is not a"
                        + " survivor by the time clustering reads the partition, so it is never grouped and"
                        + " never given a page. Ordered the other way the run would cluster documents it"
                        + " was about to remove (#112)",
                () -> assertThat(stagesInOrder.indexOf("relevance-floor"))
                        .isGreaterThan(stagesInOrder.indexOf("relevance-scoring"))
                        .isLessThan(stagesInOrder.indexOf("clustering")));
        claim(
                "and before the page a person reads, so that everything one invocation derives exists"
                        + " before anything is put to them -- a person who has answered sixty questions"
                        + " should not find the run still working afterwards",
                () -> assertThat(stagesInOrder.indexOf("clustering"))
                        .isLessThan(stagesInOrder.indexOf("relevance-report")));
    }

    @Test
    @Story("A gate ends the invocation rather than failing it")
    @DisplayName("With the boilerplate floor unset, redundancy mints no run and the command still succeeds")
    @Link(name = "ADR-080", url = Adr.THE_BOILERPLATE_FLOOR_IS_A_GATE, type = "adr")
    void stopsAtTheBoilerplateGateWithoutFailing(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "a");

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: a gate is a value the pipeline needs and does not have,"
                        + " not an error -- the run ends there having recorded everything the earlier stages"
                        + " learned (ADR-047)",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and stage 4 minted no run at all, because a run row for a stage that did nothing would"
                        + " read as a pass that found no redundancy",
                () -> assertThat(runCount("content-redundancy")).isZero());
        claim(
                "so nothing was signed either -- the floor is applied before signatures are computed, so"
                        + " an unset floor means there is nothing correct to sign yet (ADR-080)",
                () -> assertThat(signatureCount()).isZero());
    }

    @Test
    @Story("A gate ends the invocation rather than failing it")
    @DisplayName("With no embedding model named, stage 5c mints no scoring run and the command still succeeds")
    @Link(name = "ADR-084", url = Adr.THE_EMBEDDING_MODEL_IS_A_PROFILE_GATE, type = "adr")
    @Issue("107")
    void stopsAtTheEmbeddingModelGateWithoutFailing(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "a");

        cli.run("run", root.toString());

        claim(
                "the invocation still reports success: gate 3 is a value the pipeline needs and does not"
                        + " have, not an error -- the run ends there having recorded everything stage 5's"
                        + " measurement step already learned (ADR-047)",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and no scoring run was minted at all, because a run row for a stage that scored nothing"
                        + " would read as a corpus scored against zero documents",
                () -> assertThat(runCount("embedding-scoring")).isZero());
    }

    @Test
    @Story("The commands the tool offers")
    @DisplayName("Publishing reports that it is not built yet rather than appearing to have run")
    void publishSaysItIsNotBuiltYet() {
        cli.run("publish");

        claim(
                "publishing fails, because a command that quietly does nothing is worse than one that says"
                        + " it cannot",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE));
    }

    @Test
    @Story("The commands the tool offers")
    @DisplayName("Naming no command prints what the commands are, and does not report success")
    void namingNoCommandPrintsTheCommands() {
        cli.run();

        claim(
                "an invocation naming no command does not exit as though work was done",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.USAGE));
    }

    @Test
    @Story("The commands the tool offers")
    @DisplayName("A database directory that disagrees with the one actually opened stops the run")
    void refusesADatabaseDirectoryThatWasNotTheOneOpened(@TempDir Path root, @TempDir Path elsewhere)
            throws IOException {
        Files.writeString(root.resolve("a.txt"), "a");
        // The context, and so the database, is shared with the other tests in this class, which do
        // record walks. What matters is that this invocation adds none.
        long walksBefore = walkCount();

        cli.run("run", root.toString(), "--db-dir=" + elsewhere);

        claim(
                "the run refuses rather than writing to one directory while the operator believes they"
                        + " named another",
                () -> assertThat(cli.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim(
                "and this invocation recorded no walk of its own, the refusal coming before any walking",
                () -> assertThat(walkCount()).isEqualTo(walksBefore));
    }

    private long walkCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM walk", Long.class);
    }

    private long runCount(String stage) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM run WHERE stage = ?", Long.class, stage);
    }

    private long signatureCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM minhash_signature", Long.class);
    }

    private WalkId theWalk() {
        return new WalkId(jdbcTemplate.queryForObject("SELECT id FROM walk", Long.class));
    }
}
