package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
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
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
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
    GenerationJobConfiguration.class,
    GenerationTasklet.class,
    ClusterSynthesis.class,
    SynthesisDocs.class,
    LeadingChunks.class,
    GenerationScriptedBeans.class,
    GenerationRun.class,
    GenerationModel.class,
    GenerationContextWindow.class,
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

    /**
     * The logger a failed step's exception is written through: Spring Batch logs it, with its cause
     * chain, when a step ends in error, and the application does not log it a second time.
     */
    private static final String BATCH_LOGGER = "org.springframework.batch";

    /** A floor of 1.0 opens stage 4's gate, the way the sibling invocation tests do. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model this fixture names, so gate 3 and the steps behind it open. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** The corpus document the tests below keep from being opened, or change underneath the run. */
    private static final String HELD_DOCUMENT = "held-document.txt";

    /** The corpus document left alone, so the page has one preview that should still be shown. */
    private static final String OPEN_DOCUMENT = "open-document.txt";

    /**
     * What the page shows in place of a document's opening when its file will not open (ADR-152 §1),
     * word for word: the page is what a person reads, so the wording is the contract.
     */
    private static final String COULD_NOT_BE_OPENED =
            "(the file could not be opened when this page was written, so its opening is not shown)";

    /**
     * What the page shows in place of a document's opening when no conversion is on record for its
     * file as it is now (ADR-152 §3), word for word, for the same reason.
     */
    private static final String NO_CONVERSION_ON_RECORD =
            "(no conversion is on record for the file as it is now, so its opening is not shown)";

    /**
     * The words every scripted conversion carries after its title, and nothing else on the page does,
     * so finding them means a document's opening was shown.
     */
    private static final String A_SHOWN_OPENING = "stubbed but real content";

    /** One walk of one corpus folder: the archive looked the same to the second invocation as to the first. */
    private static final int ONE_WALK = 1;

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

    /**
     * One sampled document's file will not open on the invocation after the one that scored it
     * (ADR-152, #289).
     *
     * <p>The walk lists files without opening them, so a held file is the same observation as before:
     * the earlier walk is kept, every step that recorded its completion is walked past, and the page
     * -- which records none (ADR-118) -- is the first thing to read the file again. That is the state
     * this reproduces, and the claim about one walk says so rather than assuming it.
     *
     * <p><b>Guarded, and it says so.</b> On Windows the file is held by a lock over all of it, which
     * Windows enforces against every other handle, this process's included. Elsewhere every
     * permission is taken off it, which a superuser reads through, so where the file can still be
     * read the test aborts by assumption rather than claiming something it did not show.
     */
    @Test
    @Issue("289")
    @Story("One document that cannot be opened costs its own preview, not the page")
    @DisplayName("A sampled document whose file cannot be opened is still asked about, and both files are written")
    @Link(name = "ADR-152", url = Adr.A_SURVIVOR_WHOSE_FILE_WILL_NOT_OPEN_IS_STILL_ASKED_ABOUT, type = "adr")
    void aSampledDocumentWhoseFileWillNotOpenIsStillAskedAbout(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        profile(seeds);
        cli.run("run", root.toString());
        claim(
                "before anything is held, the first run succeeds and asks about both documents, so what"
                        + " follows is about the second run alone",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(labelFile()).contains(HELD_DOCUMENT).contains(OPEN_DOCUMENT);
                });
        whateverAnotherTestLeftHere();
        logged.list.clear();

        try (FileThatWillNotOpen held = FileThatWillNotOpen.hold(root.resolve(HELD_DOCUMENT))) {
            assumeTrue(
                    held.refusesToOpen(),
                    "this environment reads a file with every permission taken off it, as a superuser"
                            + " does, so no file can be kept from opening here");
            cli.run("run", root.toString());
        }

        claim(
                "the archive looked the same to the second run as to the first: a file that will not open"
                        + " is still listed with the same size and time, so nothing was measured again and"
                        + " the page was the first thing to reach for the file",
                () -> assertThat(walksOf(root)).isEqualTo(ONE_WALK));
        claim(
                "the run reports success: one document that cannot be opened today is a fact about that"
                        + " document, and the question about it can still be answered",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the page and the answer file were both written by this run, not left over from the last",
                () -> {
                    assertThat(workingDirectory.resolve(RelevanceLabellingReport.FILE_NAME)).exists();
                    assertThat(workingDirectory.resolve(RelevanceLabelFile.FILE_NAME)).exists();
                });
        claim(
                "the document is still asked about: its score came from the text read when it was first"
                        + " converted, and a person's answer is about the document, not about today's file",
                () -> assertThat(labelFile()).contains(HELD_DOCUMENT));
        claim(
                "the page says why that document's opening is missing, in words that cannot be mistaken"
                        + " for a document that had no text",
                () -> assertThat(page()).contains(COULD_NOT_BE_OPENED));
        claim(
                "and the other document's opening is still shown: one file that will not open costs its"
                        + " own preview and nobody else's",
                () -> assertThat(page()).contains(A_SHOWN_OPENING));
        claim(
                "a warning names the file, so the operator knows which one to release before looking again",
                () -> assertThat(logged.list)
                        .anyMatch(event -> event.getLevel() == Level.WARN
                                && event.getFormattedMessage().contains(HELD_DOCUMENT)));
        claim(
                "and the run went on past the page to the arrangement step, rather than ending there",
                () -> assertThat(operatorLines()).anyMatch(line -> line.contains("the arrangement step")));
    }

    /**
     * A sampled document's bytes change after it was converted, and nothing a directory listing shows
     * changes with them (ADR-152 §3).
     *
     * <p>The page reads the conversions already on record and makes none. A conversion here would be
     * a call to the document converter to fill a preview, of bytes no score was computed from. Unlike
     * the test above this needs nothing the environment can refuse, so it runs everywhere.
     */
    @Test
    @Issue("289")
    @Story("The labelling page reads what was converted and converts nothing itself")
    @DisplayName("A document whose bytes changed since it was converted is not converted again for the page")
    @Link(name = "ADR-152", url = Adr.A_SURVIVOR_WHOSE_FILE_WILL_NOT_OPEN_IS_STILL_ASKED_ABOUT, type = "adr")
    void aDocumentWhoseBytesChangedIsNotConvertedAgainForThePage(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        profile(seeds);
        cli.run("run", root.toString());
        int conversionsBefore = conversionsOnRecord();
        whateverAnotherTestLeftHere();
        logged.list.clear();

        theBytesChangeAndTheListingDoesNot(root.resolve(HELD_DOCUMENT));
        cli.run("run", root.toString());

        claim(
                "the archive looked the same to the second run as to the first: the file kept its size and"
                        + " its time, so nothing was measured again and only the page read it",
                () -> assertThat(walksOf(root)).isEqualTo(ONE_WALK));
        claim("the run reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "no conversion was added: the page shows what was converted, and a changed file is not"
                        + " sent to the converter to fill a preview",
                () -> assertThat(conversionsOnRecord()).isEqualTo(conversionsBefore));
        claim(
                "and the document is still asked about, because the sample is the same whatever the file"
                        + " holds today",
                () -> assertThat(labelFile()).contains(HELD_DOCUMENT));
        claim(
                "the page says why that document's opening is missing: nothing converted from the file as"
                        + " it is now is on record, which is a different reason from a document that had no"
                        + " text",
                () -> assertThat(page()).contains(NO_CONVERSION_ON_RECORD));
        claim(
                "a warning names the file, so the operator knows which document the page could not show",
                () -> assertThat(logged.list)
                        .anyMatch(event -> event.getLevel() == Level.WARN
                                && event.getFormattedMessage().contains(HELD_DOCUMENT)));
    }

    /**
     * A step that records its completion stops the run on a file that will not open, and the next
     * invocation after the file is released finishes the job (ADR-152 §4).
     *
     * <p>The first invocation names no embedding model, so every step of stage 5 that reads the archive
     * is gated and still unfinished. The second names one with the file held: embedding meets it first.
     * The third releases it. Guarded as the first test here is, and for the same reason.
     */
    @Test
    @Issue("289")
    @Story("A step whose finished work is kept stops rather than keep a gap for ever")
    @DisplayName("Scoring stops the run on a file that cannot be opened, and the next run after it is released finishes")
    @Link(name = "ADR-152", url = Adr.A_SURVIVOR_WHOSE_FILE_WILL_NOT_OPEN_IS_STILL_ASKED_ABOUT, type = "adr")
    void aStepThatRecordsItsCompletionStopsOnAFileThatWillNotOpen(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpusOfTwoDocuments(root, seeds);
        theFloorAndTheSeedFolderButNoModel(seeds);
        cli.run("run", root.toString());
        whateverAnotherTestLeftHere();

        String heldFileNamed = "could not hash " + Walk.canonicalRoot(root).resolve(HELD_DOCUMENT);
        int heldExitCode;
        ListAppender<ILoggingEvent> stepFailures = new ListAppender<>();
        ch.qos.logback.classic.Logger batchLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BATCH_LOGGER);
        stepFailures.start();
        batchLogger.addAppender(stepFailures);
        try (FileThatWillNotOpen held = FileThatWillNotOpen.hold(root.resolve(HELD_DOCUMENT))) {
            assumeTrue(
                    held.refusesToOpen(),
                    "this environment reads a file with every permission taken off it, as a superuser"
                            + " does, so no file can be kept from opening here");
            profile(seeds);
            cli.run("run", root.toString());
            heldExitCode = cli.getExitCode();
        } finally {
            batchLogger.detachAppender(stepFailures);
            stepFailures.stop();
        }
        boolean pageWrittenWhileHeld = Files.exists(workingDirectory.resolve(RelevanceLabellingReport.FILE_NAME));

        cli.run("run", root.toString());

        claim(
                "while the file was held the run failed: scoring keeps what it finished for good, so going"
                        + " on without the document would leave it unscored under work recorded as complete,"
                        + " where no later run would ever look for it again",
                () -> assertThat(heldExitCode).isNotZero());
        claim(
                "and it failed on that file and said so: the error names the file it could not read, so"
                        + " the operator knows which one to release before running the same command again",
                () -> assertThat(stepFailures.list)
                        .anyMatch(event -> event.getLevel() == Level.ERROR
                                && anyCauseSays(event.getThrowableProxy(), heldFileNamed)));
        claim(
                "and no page was written from that run, since the scores it would have shown were never"
                        + " finished",
                () -> assertThat(pageWrittenWhileHeld).isFalse());
        claim(
                "once the file is released, the same command finishes: the stop cost a second run and"
                        + " nothing else",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the page asks about the document the first attempt stopped on",
                () -> assertThat(labelFile()).contains(HELD_DOCUMENT));
    }

    /** Two corpus documents that convert alike and a seed, so the page has two previews to show. */
    private static void aCorpusOfTwoDocuments(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve(HELD_DOCUMENT), "a corpus document");
        Files.writeString(root.resolve(OPEN_DOCUMENT), "a second corpus document, worded differently");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
    }

    /**
     * Rewrites a file's bytes in place with the same length, and puts its modified time back, so a
     * directory listing shows exactly what it showed before. Its creation time is untouched by a
     * rewrite in place.
     */
    private static void theBytesChangeAndTheListingDoesNot(Path file) throws IOException {
        FileTime modified = Files.getLastModifiedTime(file);
        String before = Files.readString(file, StandardCharsets.UTF_8);
        Files.writeString(file, before.toUpperCase(Locale.ROOT), StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, modified);
    }

    /** How many walks of {@code root} are on record. */
    private int walksOf(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk WHERE root = ?", Integer.class, Walk.canonicalRoot(root).toString());
    }

    /** How many conversions the extraction cache holds, under any instrument. */
    private int conversionsOnRecord() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM extraction_cache", Integer.class);
    }

    /** The labelling page as this run wrote it, or nothing where it wrote none. */
    private String page() throws IOException {
        Path page = workingDirectory.resolve(RelevanceLabellingReport.FILE_NAME);
        return Files.exists(page) ? Files.readString(page) : "";
    }

    /** The answer file as this run wrote it, or nothing where it wrote none. */
    private String labelFile() throws IOException {
        Path labels = workingDirectory.resolve(RelevanceLabelFile.FILE_NAME);
        return Files.exists(labels) ? Files.readString(labels) : "";
    }

    /** The seed folder named and stage 4's gate open, with no embedding model, so stage 5 is gated. */
    private void theFloorAndTheSeedFolderButNoModel(Path seeds) {
        Profile loaded = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(loaded.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .build());
    }

    /**
     * Keeps one file from being opened for as long as it is held, the way a program the archive's owner
     * has open can.
     *
     * <p>On Windows, an exclusive lock over the whole file through a handle of its own: Windows refuses
     * a read through any other handle, this process's included. Elsewhere, every permission is taken
     * off the file and put back on release, which refuses everyone but a superuser. {@link
     * #refusesToOpen} is what the caller checks before claiming anything.
     */
    private static final class FileThatWillNotOpen implements AutoCloseable {

        private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

        private final Path file;
        private final FileChannel lockingChannel;
        private final Set<PosixFilePermission> permissionsBefore;

        private FileThatWillNotOpen(
                Path file, FileChannel lockingChannel, Set<PosixFilePermission> permissionsBefore) {
            this.file = file;
            this.lockingChannel = lockingChannel;
            this.permissionsBefore = permissionsBefore;
        }

        static FileThatWillNotOpen hold(Path file) throws IOException {
            if (WINDOWS) {
                FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
                channel.lock();
                return new FileThatWillNotOpen(file, channel, null);
            }
            Set<PosixFilePermission> before = Files.getPosixFilePermissions(file);
            Files.setPosixFilePermissions(file, EnumSet.noneOf(PosixFilePermission.class));
            return new FileThatWillNotOpen(file, null, before);
        }

        /** Whether reading the file actually fails here, which is the premise every claim rests on. */
        boolean refusesToOpen() {
            try (InputStream in = Files.newInputStream(file)) {
                in.read();
                return false;
            } catch (IOException refused) {
                return true;
            }
        }

        /** Releases the lock, or puts the permissions back, so the file can be read and cleaned up. */
        @Override
        public void close() throws IOException {
            if (lockingChannel != null) {
                lockingChannel.close();
            } else {
                Files.setPosixFilePermissions(file, permissionsBefore);
            }
        }
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
        profileStore.save(ProfileFixture.profile()
                .degenerateOutputConfidenceFloor(loaded.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so the model gate is open")
                .build());
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

    /** Whether {@code thrown}, or anything in the chain of causes beneath it, carries exactly {@code message}. */
    private static boolean anyCauseSays(IThrowableProxy thrown, String message) {
        for (IThrowableProxy cause = thrown; cause != null; cause = cause.getCause()) {
            if (message.equals(cause.getMessage())) {
                return true;
            }
        }
        return false;
    }

    /** Every operator-facing line this invocation wrote, in the order it wrote them. */
    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** The seed folder named, stage 4's gate open, and gate 3 open too. */
    private void profile(Path seeds) {
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }
}
