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
import io.algernon.vespera.extraction.HybridChunkerBeans;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.extraction.LeadingChunks;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
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
import org.junit.jupiter.api.AfterEach;
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
 * How the record of stage 6b's work is composed, and what it takes to stop one being written at all
 * (ADR-110, ADR-114, ADR-126, #196).
 *
 * <p><b>Why these claims are made against the database rather than against a method.</b> {@code
 * GenerationRunTest} already says what a generator identity is made of: it hands {@code
 * GenerationRun.configConsumed} a digest of its own and claims that digest comes back. That is a true
 * claim about a string builder and says nothing about the call site — replace the reading of the
 * serving engine with the model's own name in {@link GenerationRun}'s constructor and every one of
 * those claims still holds, over an identity now carrying the name twice and no digest at all. So
 * these claims are made against the row an invocation actually wrote (ADR-126).
 *
 * <p><b>The order the two things happen in is claimed through what the record holds</b>, not through
 * what is left lying about afterwards (ADR-126). ADR-114 puts its stops where the name is resolved and
 * before the run is minted; what the serving runtime reported is an input to the name of the work, so
 * a wiring that wrote the name down first cannot have put that report in it. The first claim below is
 * therefore the one that sees the order, and it sees it in the only place the order leaves a mark.
 *
 * <p>The row-level claim underneath — that a refused invocation leaves nothing half written down — is
 * ADR-080's property rather than the order's, and is worth its own test for a different reason: a step
 * that stopped takes back what it had begun, and that only stays true while the writing down happens
 * inside the same piece of work the stop ends.
 *
 * <p><b>A class of its own, for the working directory.</b> One temporary directory serves a whole
 * class of these tests and the database outlives each method, so scripting a serving runtime that
 * refuses would be scripting it for every method that shares the class. {@link
 * GenerationInvocationTest} is about what happens when everything answers; this is about what happens
 * when something does not.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122), for the
 * reason {@link GenerationInvocationTest} states at length. Do not reconcile the two by changing
 * either side.
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
    RelevanceReportJobConfiguration.class,
    RelevanceReportTasklet.class,
    ArrangementJobConfiguration.class,
    ArrangementTasklet.class,
    io.algernon.vespera.extraction.DocumentTitles.class,
    ArrangementRun.class,
    ArrangementGate.class,
    Clusters.class,
    SynthesisDocs.class,
    ClusterSynthesis.class,
    LeadingChunks.class,
    GenerationScriptedBeans.class,
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
    NextAction.class,
    VesperaCommand.class,
    VesperaCommand.Run.class,
    VesperaCommand.Label.class,
    LabelIngestion.class,
    RelevanceLabels.class,
    VesperaCli.class
})
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("196")
@Link(name = "ADR-110", url = Adr.PIPELINE_HANDS_SYNTHESIS_ITS_INPUTS, type = "adr")
@Link(name = "ADR-114", url = Adr.THE_GENERATION_MODEL_IS_CONFIGURATION_WITH_A_DEFAULT, type = "adr")
@Link(name = "ADR-126", url = Adr.THE_GENERATOR_IDENTITYS_WIRING_IS_PINNED_AT_THE_INVOCATION, type = "adr")
class GenerationIdentityInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the way every other invocation test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model this fixture names, so gate 3 and everything behind it open. */
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    /**
     * The model this archive is written under, named in the profile so the claims below rest on a name
     * this test chose rather than on whichever one the application happens to ship with.
     */
    private static final String THE_WRITING_MODEL = "a-named-writing-model:8b";

    /** A model the serving runtime in this fixture has never been given, so it cannot say what it is. */
    private static final String A_MODEL_NOTHING_HAS_EVER_BEEN_GIVEN = "a-model-nothing-holds:8b";

    /**
     * The port a serving runtime listens on by default, which is the whole of where it is reachable
     * that the record could have picked up. Named so the claim about its absence can say which number
     * it means and why that number is the one to look for.
     */
    private static final String THE_PORT_A_RUNTIME_LISTENS_ON = "11434";

    /** What a refused invocation is worth: nothing written down at all, not a record of work with a hole in it. */
    private static final int NO_RECORD_AT_ALL = 0;

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

    /**
     * Drops any scripted refusal before each test as well as after it.
     *
     * <p>Twice rather than once because a method that dies before its {@code @AfterEach} would
     * otherwise hand the next class a runtime that refuses a name it never asked about, and the order
     * classes run in is decided per machine.
     */
    @BeforeEach
    @AfterEach
    void theRuntimeServesEverythingAgain() {
        EmbeddingScriptedBeans.servesEveryModel();
    }

    @Test
    @Story("The record of a piece of work says which weights actually did it")
    @DisplayName("What is written down names the weights the serving runtime reported, not the model's name twice")
    void recordsTheWeightsTheRuntimeReported(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        approveGeneratingUnder(ArrangementGate.shortNameOf(theLatestArrangement(root)), THE_WRITING_MODEL);

        cli.run("run", root.toString());

        claim(
                "the work was recorded at all, which is what the claims below are about -- asserted first"
                        + " and separately, so a step that never ran fails saying so rather than failing"
                        + " while reaching for something that is not there",
                () -> assertThat(theRecordOfTheWork(root)).isNotEmpty());
        claim(
                "the model's name is in what was written down, because two models write different"
                        + " documents over one group and a record that cannot tell them apart claims one"
                        + " wrote the other's",
                () -> assertThat(theRecordOfTheWork(root)).contains(THE_WRITING_MODEL));
        claim(
                "and beside it, exactly what the serving runtime said it was holding under that name --"
                        + " a name can be re-used over different weights, so a record composed from the"
                        + " name alone would file two different pieces of work under one heading and"
                        + " nobody could tell afterwards which weights wrote which",
                () -> assertThat(theRecordOfTheWork(root)).contains(EmbeddingScriptedBeans.DIGEST));
    }

    @Test
    @Story("The record of a piece of work says which weights actually did it")
    @DisplayName("What is written down carries no trace of where the model was reached")
    void recordsNoTraceOfWhereTheModelWasReached(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        approveGeneratingUnder(ArrangementGate.shortNameOf(theLatestArrangement(root)), THE_WRITING_MODEL);

        cli.run("run", root.toString());

        claim(
                "the work was recorded at all, so the claim below is about something that exists",
                () -> assertThat(theRecordOfTheWork(root)).isNotEmpty());
        claim(
                "and nothing in it says where the model was reached -- not the address and not the port "
                        + THE_PORT_A_RUNTIME_LISTENS_ON + " a runtime listens on by default. Two"
                        + " deployments answering alike are one instrument and moving a port is not a"
                        + " change, so a record carrying the address would count the same work twice",
                () -> assertThat(theRecordOfTheWork(root))
                        .doesNotContain(THE_PORT_A_RUNTIME_LISTENS_ON)
                        .doesNotContain("localhost"));
    }

    @Test
    @Story("Nothing is written over the archive under weights nobody can name")
    @DisplayName("With the model named never having been given to the runtime, the invocation stops")
    void stopsWhenTheRuntimeCannotSayWhatItHoldsUnderThatName(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        approveGeneratingUnder(
                ArrangementGate.shortNameOf(theLatestArrangement(root)), A_MODEL_NOTHING_HAS_EVER_BEEN_GIVEN);
        EmbeddingScriptedBeans.hasNeverPulled(A_MODEL_NOTHING_HAS_EVER_BEEN_GIVEN);

        cli.run("run", root.toString());

        claim(
                "the invocation stopped rather than writing anything: a model the runtime has never been"
                        + " given cannot be described, and writing an archive under a description nobody"
                        + " can check is worse than not writing it",
                () -> assertThat(cli.getExitCode()).isNotZero());
    }

    @Test
    @Story("Nothing is written over the archive under weights nobody can name")
    @DisplayName("Stopping for that reason leaves nothing half written down")
    void leavesNothingHalfWrittenDownWhenItStopsForThatReason(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        approveGeneratingUnder(
                ArrangementGate.shortNameOf(theLatestArrangement(root)), A_MODEL_NOTHING_HAS_EVER_BEEN_GIVEN);
        EmbeddingScriptedBeans.hasNeverPulled(A_MODEL_NOTHING_HAS_EVER_BEEN_GIVEN);

        cli.run("run", root.toString());

        claim(
                "there are " + NO_RECORD_AT_ALL + " records of this work: a step that stopped takes back"
                        + " whatever it had begun writing down, so nothing survives of an attempt that"
                        + " got as far as naming the work and no further -- a record left behind by one"
                        + " reads afterwards as a pass that found nothing rather than as work that never"
                        + " happened",
                () -> assertThat(theRecordsOfTheWork(root)).hasSize(NO_RECORD_AT_ALL));
    }

    /** One corpus document and one exemplar, with every gate before this one open. */
    private void aCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }

    /** Writes the approval and the model to write under, leaving every other key as the fixture left it. */
    private void approveGeneratingUnder(String approval, String model) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(approval, "read by this test")
                .generationModel(model, "set by this test")
                .build());
    }

    /** The arrangement the most recent invocation over {@code root} recorded — the one its page names. */
    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                ArrangementRun.STAGE,
                Walk.canonicalRoot(root).toString()));
    }

    /**
     * What this step wrote down about itself over {@code root}: the configuration its record of the
     * work was derived from, which is where the generator identity is kept.
     *
     * <p>Scoped to the walk of this corpus rather than read off the table, for {@link
     * GenerationInvocationTest}'s reason: one working directory serves the whole class and the database
     * outlives each method, so anything unscoped would be a claim about every corpus any method here
     * ever walked.
     */
    private String theRecordOfTheWork(Path root) {
        List<String> records = theRecordsOfTheWork(root);
        return records.isEmpty() ? "" : records.getFirst();
    }

    /** The same, as many as there are, oldest first — so a step that never ran answers with none. */
    private List<String> theRecordsOfTheWork(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.config_consumed FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                GenerationRun.STAGE,
                Walk.canonicalRoot(root).toString());
    }
}
