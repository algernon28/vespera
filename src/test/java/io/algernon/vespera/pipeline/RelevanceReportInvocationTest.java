package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
    VesperaCli.class
})
/**
 * Stage 5's last step, driven through a whole invocation (ADR-088, #110): the page and the label
 * file have to exist where the operator will look for them, which is the one claim the rendering
 * tests beside this class cannot make.
 *
 * <p>A sibling of {@code RelevanceScoringInvocationTest}, on the same scripted extraction and
 * embedding beans, so the whole pipeline runs here without a Docker daemon. What the real sidecars
 * add is checked separately by {@code RelevanceReportIT}.
 */
@Epic("Relevance")
@Feature("Labelling")
@Issue("110")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
class RelevanceReportInvocationTest {

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /** A floor of 1.0 opens stage 4's gate, the way the sibling invocation tests do. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model this fixture names, so gate 3 and the steps behind it open. */
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

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void captureOperatorLines() {
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("A shut seed gate gates this step, as it gates every other step in stage 5")
    @DisplayName("Stage 4's gate open and a model named, with no seed folder, still reports success")
    void aModelNamedWithNoSeedFolderIsGatedRatherThanFatal(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        theFloorAndTheModelButNoSeedFolder();
        whateverAnotherTestLeftHere();

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: nothing here is a failure -- the operator has answered"
                        + " two of the three values a run wants and not the third, which is the state"
                        + " every other step in stage 5 reports as gated and exits 0 on",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and this step says it was gated, in the same sentence its siblings use, rather than"
                        + " resolving a scoring run that cannot exist while the seed gate is shut",
                () -> assertThat(operatorLines())
                        .anyMatch(line -> line.contains("relevance-report step is gated")
                                && line.contains("no seed folder is named")));
        claim(
                "nothing was put to a person, so no page and no label file were written -- there is no"
                        + " seed set for a question to be about",
                () -> assertThat(workingDirectory.resolve(RelevanceLabellingReport.FILE_NAME))
                        .doesNotExist());
    }

    @Test
    @Story("A seed folder naming nothing is census's finding, not this step's failure")
    @DisplayName("A seed folder that is not there is gated rather than fatal")
    void aSeedFolderThatIsNotThereIsGatedRatherThanFatal(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        profile(seeds.resolve("not-here"));

        cli.run("run", root.toString());

        claim(
                "the invocation reports success, because census already recorded why it could not walk"
                        + " the folder and carried on (ADR-064) -- turning that into a failed invocation"
                        + " two steps later reports one typo as two different kinds of problem",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the step is gated, reached by the same route: a seed folder that resolves to"
                        + " nothing leaves the seed gate shut, which is what SeedGate already decided",
                () -> assertThat(operatorLines())
                        .anyMatch(line -> line.contains("relevance-report step is gated")));
    }

    @Test
    @Story("The two files land where the operator will look for them")
    @DisplayName("A whole invocation leaves the page and the label file beside the database, and inside no corpus")
    void leavesBothFilesBesideTheDatabase(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the page a person reads before choosing a cut is written where the database and the"
                        + " profile already live, which is the one place an operator has been told to look",
                () -> assertThat(workingDirectory.resolve("relevance-labelling.html")).exists());
        claim(
                "and so is the file they write their answers into, beside the page that poses the"
                        + " questions rather than somewhere they have to be told about separately",
                () -> assertThat(workingDirectory.resolve("relevance-labels.yaml")).exists());
        claim(
                "and neither is written into the corpus or the seed folder: both hold exactly the files"
                        + " this test put in them, because a curation tool that leaves its own paperwork"
                        + " among the documents has changed the thing it was asked to describe",
                () -> assertThat(filesUnder(root, seeds))
                        .containsExactlyInAnyOrder(root.resolve("corpus.txt"), seeds.resolve("seed.txt")));
    }

    @Test
    @Story("The two files land where the operator will look for them")
    @DisplayName("The page reports the spread and the label file names the run, so the two can be matched later")
    void thePageAndTheLabelFileAgreeOnWhatTheyWereGeneratedFrom(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        String page = Files.readString(workingDirectory.resolve("relevance-labelling.html"));
        String labels = Files.readString(workingDirectory.resolve("relevance-labels.yaml"));
        claim(
                "the page shows the bands it cut, so a reader can see the shape rather than be handed a"
                        + " verdict about it",
                () -> assertThat(page).contains("Band 1"));
        claim(
                "the label file names the run it was generated under, which is what lets a completed file"
                        + " offered against a different sample be refused rather than partially matched",
                () -> assertThat(labels).contains("generatedUnderRun"));
        claim(
                "and it asks about the document that was scored, with the answer left blank for a person",
                () -> assertThat(labels).contains("corpus.txt").contains("relevant:"));
    }

    @Test
    @Story("The threshold is pointed at, never answered")
    @DisplayName("The profile gains a pointer to the page and no threshold value")
    void pointsTheThresholdKeyAtThePageWithoutAnsweringIt(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        Profile profile = profileStore.load();
        claim(
                "the threshold key names the page its value is meant to be read off, so an operator"
                        + " opening the profile is told where the number comes from",
                () -> assertThat(profile.relevanceScoreFloor().measurement().source())
                        .isEqualTo("relevance-labelling.html"));
        claim(
                "and the value itself is still unanswered: a threshold removes documents, and nothing"
                        + " here may choose one on a person's behalf",
                () -> assertThat(profile.relevanceScoreFloor().isSet()).isFalse());
    }

    /** Every file under either folder, so the claim about leaving nothing behind can be made. */
    private static java.util.List<Path> filesUnder(Path... folders) throws IOException {
        java.util.List<Path> found = new java.util.ArrayList<>();
        for (Path folder : folders) {
            try (java.util.stream.Stream<Path> walk = Files.walk(folder)) {
                walk.filter(Files::isRegularFile).forEach(found::add);
            }
        }
        return found;
    }

    /**
     * Stage 4's gate open and a model named, with no seed folder -- ADR-098's invocation 2 for an
     * operator who never took step zero.
     */
    private void theFloorAndTheModelButNoSeedFolder() {
        Profile loaded = profileStore.load();
        profileStore.save(new Profile(
                null,
                loaded.degenerateOutputConfidenceFloor(),
                new ProfileValue(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open", null),
                new ProfileValue(MODEL_NAME, "set by this test, so the model gate is open", null)));
    }

    /**
     * Clears the two files this step writes, so a claim about them is about this invocation.
     *
     * <p>The working directory is static, so it outlives each test method -- a page left by the test
     * that asserts it gets written would otherwise satisfy a claim that this invocation wrote none.
     */
    private void whateverAnotherTestLeftHere() throws IOException {
        Files.deleteIfExists(workingDirectory.resolve(RelevanceLabellingReport.FILE_NAME));
        Files.deleteIfExists(workingDirectory.resolve(RelevanceLabelFile.FILE_NAME));
    }

    /** Every operator-facing line this invocation wrote, in the order it wrote them. */
    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
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
}
