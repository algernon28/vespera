package io.algernon.vespera.pipeline;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileValue;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.embedding.SeedCorpusComparison;
import io.algernon.vespera.embedding.UnusableSeeds;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.embedding.ChunkEmbedderBeans;
import io.algernon.vespera.embedding.ClusteringBeans;
import io.algernon.vespera.embedding.DocumentClusters;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.embedding.RelevanceScoringBeans;
import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.HybridChunkerBeans;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * The closing line, reached through a real invocation (ADR-098, #136).
 *
 * <p>{@link NextActionTest} pins what the line says in each state. What cannot be settled there is
 * whether the line is reached at all, whether it is reached once, and whether it is the last thing
 * an operator reads — which is the whole point of it. With nothing set, the invocation it ends emits
 * nine locally-correct gated lines, and a summary printed anywhere but last is a tenth.
 *
 * <p>The two commands report on different channels, and deliberately: {@code run} is unattended and
 * its line belongs with the gate lines it summarises, in the log. {@code label} is a person's own
 * act, and it already answers them on stdout.
 */
@Epic("Pipeline")
@Feature("The operator is told the next value")
@Link(name = "ADR-098", url = Adr.FOUR_INVOCATIONS_AND_THE_NEXT_VALUE, type = "adr")
@Issue("136")
@ExtendWith(OutputCaptureExtension.class)
class ClosingLineInvocationTest {

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /** What every closing line that asks for something says before naming it. */
    private static final String THE_ACTION = "Next:";

    /** How {@link LabelIngestion} opens its own report, and so what the closing line follows. */
    private static final String WHAT_WAS_RECORDED = "recorded ";

    /** Above every measured document frequency here, so stage 4's gate is open. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for. */
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
    void forgetWhatAnotherTestAnswered() {
        // The working directory is static, so profile.yaml outlives each test method. A test whose
        // claim is about an unanswered profile has to say so rather than inherit one.
        profileStore.save(new Profile(null, null, null, null, null));
    }

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
    @Story("An invocation ends by naming the next value, once, after everything else it has to say")
    @DisplayName("A run with nothing set ends on one line naming the next value")
    void aRunEndsOnTheClosingLine(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation ends on the closing line: every gate line it summarises is behind it,"
                        + " which is what makes it the one an operator acts on",
                () -> assertThat(operatorLines().getLast()).contains(THE_ACTION));
        claim(
                "and there is exactly one of them -- a summary emitted per step would be a tenth line"
                        + " saying what the nine already said",
                () -> assertThat(operatorLines().stream().filter(line -> line.contains(THE_ACTION)))
                        .hasSize(1));
        claim(
                "it names the seed folder, which is the value this operator needs first and the one the"
                        + " nine gate lines mention twice without ever asking for",
                () -> assertThat(operatorLines().getLast()).contains("seedFolder"));
        claim(
                "and the nine gate lines are untouched: each is correct about its own step, and none of"
                        + " them is the defect this line fixes",
                () -> assertThat(operatorLines().stream().filter(line -> line.contains("is gated:")))
                        .isNotEmpty());
    }

    @Test
    @Story("A refused invocation says what is wrong with the invocation, and nothing about the profile")
    @DisplayName("An invocation that refuses to run ends on no closing line")
    void aRefusedInvocationEndsOnNoClosingLine() {
        cli.run("run");

        claim(
                "the invocation was refused, having no root to walk",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "and no closing line was written: the next action is to name a root, which the refusal"
                        + " itself already says. A profile value named here would be answering a question"
                        + " nobody reached",
                () -> assertThat(operatorLines()).noneMatch(line -> line.contains(THE_ACTION)));
    }

    @Test
    @Story("The other invocation on the path ends the same way, on the channel it already answers on")
    @DisplayName("vespera label ends on the closing line, on stdout, after what it recorded")
    void labelEndsOnTheClosingLine(CapturedOutput output, @TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aScoredCorpus(root, seeds);
        answerEveryQuestion();

        cli.run("label");

        claim(
                "the answers were recorded, so this is the third invocation on the path and not a"
                        + " refusal -- a refused label command prints nothing to stdout at all, and a"
                        + " test that accepted one would pass with this line deleted",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the closing line reaches stdout, where this command already answers the person who"
                        + " invoked it -- an act someone waits on reports where they are looking",
                () -> assertThat(stdoutAfterWhatWasRecorded(output)).contains(THE_ACTION));
        claim(
                "and it is the last thing printed, after the count of what was recorded",
                () -> assertThat(stdoutAfterWhatWasRecorded(output).trim().lines()).hasSize(1));
        claim(
                "it names the threshold, which is what this operator has to choose next and the reason"
                        + " they were answering questions at all",
                () -> assertThat(stdoutAfterWhatWasRecorded(output)).contains("relevanceScoreFloor"));
    }

    @Test
    @Story("A seed folder naming nothing ends the invocation it is read in, not the invocation itself")
    @DisplayName("A seed folder that is not there leaves the run successful and still closing on a line")
    void aSeedFolderThatIsNotThereIsNotFatal(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        theOnlyAnsweredValueIsASeedFolderThatIsNotThere(seeds.resolve("not-here"));

        cli.run("run", root.toString());

        claim(
                "the invocation still reports success: census records why it could not walk the folder"
                        + " and carries on (ADR-064), and a closing line is the last thing that should"
                        + " turn that into a failed invocation over a typo",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and it still ends on a closing line, rather than on a stack trace: the operator who"
                        + " mistyped a path is exactly the one who needs telling what to do next",
                () -> assertThat(operatorLines().getLast()).contains(THE_ACTION));
    }

    /** A corpus and a seed folder, neither of them named in the profile yet. */
    private void aCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus-0.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /** Runs the pipeline far enough that a sample exists and a label file has been written. */
    private void aScoredCorpus(Path root, Path seeds) throws IOException {
        aCorpus(root, seeds);
        theProfileSaying(seeds.toString());
        cli.run("run", root.toString());
    }

    /** Fills in every blank answer, the way a person working through the file would. */
    private void answerEveryQuestion() throws IOException {
        Path labels = workingDirectory.resolve(RelevanceLabelFile.FILE_NAME);
        Files.writeString(labels, Files.readString(labels).replace("relevant: null", "relevant: true"));
    }

    /**
     * A mistyped seed folder and nothing else answered — the state an operator reaches by taking step
     * zero and getting the path wrong.
     *
     * <p>The other two run values stay unset because the seed folder is the only one this claim is
     * about: what is pinned here is that reading an unwalkable folder does not end a successful
     * invocation, and naming a model as well would add a scoring run to a claim that wants none.
     *
     * <p>It also used to be the only state that could be pinned. A model named alongside an
     * unwalkable seed folder failed the job outright until #141 — the relevance-report step resolved
     * a scoring run while the seed gate was shut — which is a different defect, fixed separately, and
     * {@code RelevanceReportInvocationTest} is where it is now pinned.
     */
    private void theOnlyAnsweredValueIsASeedFolderThatIsNotThere(Path seeds) {
        Profile profile = profileStore.load();
        profileStore.save(new Profile(
                new ProfileValue(seeds.toString(), "set by this test", null),
                profile.degenerateOutputConfidenceFloor(),
                null,
                null,
                null));
    }

    /** Stage 4's gate open and a model named, so the only thing left to vary is the seed folder. */
    private void theProfileSaying(String seedFolder) {
        Profile profile = profileStore.load();
        profileStore.save(new Profile(
                new ProfileValue(seedFolder, "set by this test", null),
                profile.degenerateOutputConfidenceFloor(),
                new ProfileValue(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open", null),
                new ProfileValue(MODEL_NAME, "set by this test, so the scoring run is minted", null)));
    }

    /**
     * What {@code vespera label} printed after the count of what it recorded.
     *
     * <p>Read as what follows that count rather than as the whole of stdout, because the test
     * configuration's console appender writes the log there too — so the run command's own closing
     * line is already on stdout before this one is invoked, and asserting over everything would pass
     * with this command printing nothing at all.
     */
    private String stdoutAfterWhatWasRecorded(CapturedOutput output) {
        String out = output.getOut();
        int recorded = out.lastIndexOf(WHAT_WAS_RECORDED);
        assertThat(recorded).as("vespera label reported what it recorded").isNotNegative();
        return out.substring(out.indexOf('\n', recorded) + 1);
    }

    /** Every line this application wrote for an operator, in the order they were written. */
    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
