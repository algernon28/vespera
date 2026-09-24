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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
 * Both ways stage 2 can finish with an occurrence and produce no text for it, driven through the whole
 * job rather than through the step that decides them (ADR-139, #265).
 *
 * <p>They are two cases and not one, and a fix that closed either alone would leave the other open --
 * which is what this class exists to keep apart. In the first the converter refuses the call: nothing
 * is measured, the failure is a Spring Batch skip, and whatever the processor wrote about that
 * occurrence rolls back with the chunk. In the second the converter answers and the answer carries no
 * text: a metric row is written and ADR-070's tier-1 floor reads it. Only the first was ever able to
 * leave an occurrence no stage examined sitting in the survivor set, and the second is measured to
 * have removed all 22 of the occurrences it applied to on the corpus #265 was found against.
 *
 * <p>The refusal is scripted rather than genuine, because the real one comes from a converter this
 * suite never starts, over a legacy compound container this repository does not hold. What the fixture
 * reproduces is the response that was actually measured -- {@code failure}, one error categorised
 * {@code unknown}, and the message naming the canonical extension ADR-100 sends. Since ADR-143 that
 * response is a verdict the moment it arrives, and a folder of such files, one after another, no longer
 * stops the step. What still reaches ADR-139's fault row is a failure the converter blames on itself,
 * scripted here as {@code internal}, and that is the case this class drives to the row and to the
 * verdict the end of the step resolves it into.
 *
 * <p>A sibling of {@link RelevanceScoringInvocationTest} in shape, and deliberately: an occurrence that
 * reaches stage 5 with no stored chunk vectors is what fails the invocation, so nothing short of
 * running the scoring step proves the case is closed.
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
    StepCompletionOrderProbe.class,
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
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("265")
@Link(name = "ADR-139", url = Adr.A_REFUSED_CONVERSION_LEAVES_A_FAULT_ROW, type = "adr")
class ExtractionFaultInvocationTest {

    /** A floor of 1.0 opens stage 4's gate, as every invocation test reaching stage 5 sets it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model this fixture names, so gate 3 -- and the scoring step past it -- open too. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** The corpus file the scripted converter refuses to open, whatever its bytes say. */
    private static final String REFUSED = SeedScriptedExtractionBeans.REFUSED_CONVERSION;

    /** Files the scripted converter refuses the same way, walked one after another. */
    private static final List<String> REFUSED_ONE_AFTER_ANOTHER = SeedScriptedExtractionBeans.REFUSED_ONE_AFTER_ANOTHER;

    /** The corpus file the scripted converter opens and reports no text for. */
    private static final String WITHOUT_TEXT = SeedScriptedExtractionBeans.EMPTY_SEED;

    /** The one file in each case that converts normally, so the scoring pass has something to score. */
    private static final String READABLE = "readable.txt";

    /** The corpus file the scripted converter fails on while blaming itself. */
    private static final String FAULTED = SeedScriptedExtractionBeans.CONVERTER_FAULT;

    /** One row, for the one file the converter could not answer for: the record that it was never judged. */
    private static final long ONE_FAULT_ROW = 1;

    /** No row at all, for a file the converter did answer about. */
    private static final long NO_FAULT_ROW = 0;

    /** The category Docling gives an error it did not classify. */
    private static final String UNCATEGORISED = "unknown";

    /** The whole reason the refusal earns: the reported category, then the converter's own message. */
    private static final String THE_WHOLE_REASON =
            UNCATEGORISED + ": " + SeedScriptedExtractionBeans.REFUSAL_MESSAGE;

    /** The whole reason the converter's own fault earns, composed the same way at the end of the step. */
    private static final String THE_WHOLE_FAULT_REASON =
            "internal: " + SeedScriptedExtractionBeans.CONVERTER_FAULT_MESSAGE;

    /** One invocation records stage 2's completion once, which is what makes a single reading readable. */
    private static final int ONE_RECORDING = 1;

    /** One score, the readable file's -- and therefore proof the pass ran to the end rather than stopping. */
    private static final long ONE_SCORE = 1;

    /** No score at all, for a file that reached no stage able to score it. */
    private static final long NO_SCORE = 0;

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
    @Story("A file the converter will not open")
    @DisplayName("A file the converter refuses is recorded and removed, and the invocation still finishes")
    @Link(name = "ADR-143", url = Adr.AN_UNCATEGORISED_FAILURE_IS_A_VERDICT, type = "adr")
    void aRefusedConversionIsRecordedAndTheInvocationFinishes(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.writeString(root.resolve(REFUSED), "stands in for a container the converter cannot open");
        Files.writeString(root.resolve(READABLE), "a corpus document with real text in it");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success -- one file the converter would not open is a fact about"
                        + " that file, never a reason to abandon the corpus around it",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the refused file carries the extraction-failed verdict, which keeps it from reading, four"
                        + " stages later, as a document every stage approved of",
                () -> assertThat(verdictKindsFor(root, REFUSED)).containsExactly("EXTRACTION_FAILED"));
        claim(
                "and the reason that verdict carries is the reported category followed by the converter's"
                        + " own message, so an operator looking at a removal they did not expect can see the"
                        + " converter gave no kind of failure for it. Asserted entire rather than by a word,"
                        + " because a reason naming only the category and one naming it twice both contain"
                        + " that word",
                () -> assertThat(verdictReasonsFor(root, REFUSED)).containsExactly(THE_WHOLE_REASON));
        claim(
                "and it left no fault row: the converter answered about this file, so it was judged when the"
                        + " answer came back rather than set aside for the end of the step to judge",
                () -> assertThat(faultRowsFor(root, REFUSED)).isEqualTo(NO_FAULT_ROW));
        claim(
                "the readable file beside it was scored all the same, so the pass reached its end rather"
                        + " than stopping at the refusal",
                () -> assertThat(scoreRowsFor(root, READABLE)).isEqualTo(ONE_SCORE));
    }

    /**
     * The case measured on 2026-09-24 against a real sidecar with no LibreOffice: five legacy
     * spreadsheets in one folder, each refused this way, stopped stage 2 and failed the invocation.
     */
    @Test
    @Story("A file the converter will not open")
    @DisplayName("Files the converter refuses one after another are each recorded, and the invocation still finishes")
    @Link(name = "ADR-143", url = Adr.AN_UNCATEGORISED_FAILURE_IS_A_VERDICT, type = "adr")
    void refusedConversionsInARowDoNotStopTheInvocation(@TempDir Path root, @TempDir Path seeds) throws IOException {
        for (String name : REFUSED_ONE_AFTER_ANOTHER) {
            Files.writeString(root.resolve(name), "stands in for the container " + name);
        }
        Files.writeString(root.resolve(READABLE), "a corpus document with real text in it");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success -- " + REFUSED_ONE_AFTER_ANOTHER.size() + " files the"
                        + " converter would not open, one after another, are more in a row than the "
                        + ExtractionCircuitBreaker.CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT + " that stop the"
                        + " step when the converter itself stops answering, and none of them is that",
                () -> assertThat(cli.getExitCode()).isZero());
        for (String name : REFUSED_ONE_AFTER_ANOTHER) {
            claim(
                    name + " carries the extraction-failed verdict",
                    () -> assertThat(verdictKindsFor(root, name)).containsExactly("EXTRACTION_FAILED"));
        }
        claim(
                "and the readable file beside them was scored, so the pass reached its end",
                () -> assertThat(scoreRowsFor(root, READABLE)).isEqualTo(ONE_SCORE));
    }

    @Test
    @Story("A file the converter could not answer for")
    @DisplayName("A file the converter fails on while blaming itself is recorded, and removed once the step completes")
    void aConverterFaultIsRecordedAndTheInvocationFinishes(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.writeString(root.resolve(FAULTED), "a file the converter faults on");
        Files.writeString(root.resolve(READABLE), "a corpus document with real text in it");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success -- one fault among answered files does not stop the step",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the file left a row of its own carrying what the converter reported, so that it was never"
                        + " judged at the time is something recorded rather than something absent",
                () -> assertThat(faultRowsFor(root, FAULTED)).isEqualTo(ONE_FAULT_ROW));
        claim(
                "and the step, having completed, turned that row into the extraction-failed verdict, which is"
                        + " the only thing that keeps an occurrence nothing examined from reading, four stages"
                        + " later, as one every stage approved of",
                () -> assertThat(verdictKindsFor(root, FAULTED)).containsExactly("EXTRACTION_FAILED"));
        claim(
                "and its reason is the reported category followed by the converter's own message, composed"
                        + " at the end of the step the same way the processor composes one",
                () -> assertThat(verdictReasonsFor(root, FAULTED)).containsExactly(THE_WHOLE_FAULT_REASON));
        claim(
                "the readable file beside it was scored all the same",
                () -> assertThat(scoreRowsFor(root, READABLE)).isEqualTo(ONE_SCORE));
    }

    /**
     * The probe is static and this context is shared by every method here, so a reading left by one
     * invocation would otherwise be read as the next one's. Cleared before each test rather than only
     * after one, because which class ran last is not something a test may depend on.
     */
    @BeforeEach
    void forgetWhatEarlierInvocationsRecorded() {
        StepCompletionOrderProbe.forget();
    }

    /**
     * The pin for ADR-139 section 4, and it exists because nothing else in this suite can fail on it.
     * Both writes happen inside one {@code afterStep} pass and both are committed by the time the job
     * ends, so every other assertion in this class passes under either order; nothing reads {@code
     * finished_step} a second time within one step execution either. What is at stake is a crash
     * window rather than a result, so the only way to fail on it is to watch the two writes happen.
     */
    @Test
    @Story("A file the converter could not answer for")
    @DisplayName("The fault and its verdict are committed before stage 2 is recorded as holding all of its work")
    void theVerdictIsCommittedBeforeTheStepIsRecordedAsComplete(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.writeString(root.resolve(FAULTED), "a file the converter faults on");
        Files.writeString(root.resolve(READABLE), "a corpus document with real text in it");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "stage 2's completion was recorded exactly once, so what the probe saw is this"
                        + " invocation's own ordering and not an average over several",
                () -> assertThat(StepCompletionOrderProbe.FAULT_ROWS_VISIBLE_WHEN_STAGE_2_WAS_RECORDED_COMPLETE)
                        .hasSize(ONE_RECORDING));
        claim(
                "and the fault was already a row when that happened. The two writes share one afterStep"
                        + " pass, and Spring Batch runs step-execution listeners in reverse registration"
                        + " order, so which one goes first is decided by one line of wiring -- get it the"
                        + " other way round and an invocation killed between them leaves stage 2 recorded"
                        + " as complete with nothing faulted under it, which every later invocation then"
                        + " trusts and skips forever",
                () -> assertThat(StepCompletionOrderProbe.FAULT_ROWS_VISIBLE_WHEN_STAGE_2_WAS_RECORDED_COMPLETE)
                        .containsExactly(ONE_FAULT_ROW));
    }

    @Test
    @Story("A file the converter opens and finds nothing in")
    @DisplayName("A conversion that comes back with no text is removed by the floor, and the invocation still finishes")
    void aConversionWithNoTextIsRecordedAndTheInvocationFinishes(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        Files.writeString(root.resolve(WITHOUT_TEXT), "an intact file the converter finds no text in");
        Files.writeString(root.resolve(READABLE), "a corpus document with real text in it");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success -- the other way a file can end stage 2 with no text does"
                        + " not stop it either",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the file the converter answered about earns the no-content verdict rather than the"
                        + " failed-conversion one: the call succeeded and what came back was measured, which"
                        + " is what separates this case from the refusal",
                () -> assertThat(verdictKindsFor(root, WITHOUT_TEXT)).containsExactly("DEGENERATE_OUTPUT"));
        claim(
                "so it is not scored, having been removed two stages before anything asks it for the text"
                        + " it does not have",
                () -> assertThat(scoreRowsFor(root, WITHOUT_TEXT)).isEqualTo(NO_SCORE));
        claim(
                "and the readable file beside it was scored, so this case too leaves the pass able to"
                        + " finish rather than merely able to start",
                () -> assertThat(scoreRowsFor(root, READABLE)).isEqualTo(ONE_SCORE));
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

    /**
     * The spelling the walk recorded for {@code root}, which is the walk's own canonicalisation of it
     * (ADR-055) rather than the path this test handed the CLI -- the two differ wherever the temporary
     * directory is reached through a link or a short name, and a query joining on the un-canonicalised
     * spelling would match no row and read as an empty result rather than as a mismatch.
     */
    private String walkRoot(Path root) {
        return Walk.canonicalRoot(root).toString();
    }

    /**
     * Every query below joins back to {@code root}'s own walk and to one file within it: this database
     * is shared across both methods of this class, so a count read over a whole table would silently
     * include the other case's rows.
     */
    private long faultRowsFor(Path root, String fileName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM extraction_fault e JOIN file_occurrence f ON f.id = e.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ? AND f.path = ?",
                Long.class,
                walkRoot(root),
                fileName);
        return count == null ? 0 : count;
    }

    private List<String> verdictKindsFor(Path root, String fileName) {
        return jdbcTemplate.queryForList(
                "SELECT v.kind FROM verdict v JOIN file_occurrence f ON f.id = v.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ? AND f.path = ?",
                String.class,
                walkRoot(root),
                fileName);
    }

    private List<String> verdictReasonsFor(Path root, String fileName) {
        return jdbcTemplate.queryForList(
                "SELECT v.reason FROM verdict v JOIN file_occurrence f ON f.id = v.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ? AND f.path = ?",
                String.class,
                walkRoot(root),
                fileName);
    }

    private long scoreRowsFor(Path root, String fileName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relevance_score r JOIN file_occurrence f ON f.id = r.occurrence_id"
                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ? AND f.path = ?",
                Long.class,
                walkRoot(root),
                fileName);
        return count == null ? 0 : count;
    }
}
