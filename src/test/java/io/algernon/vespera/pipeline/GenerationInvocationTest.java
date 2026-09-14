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
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.algernon.vespera.similarity.BoilerplateShingles;
import io.algernon.vespera.similarity.DocumentFrequency;
import io.algernon.vespera.similarity.RedundancyResolution;
import io.algernon.vespera.similarity.RedundancySignatures;
import io.algernon.vespera.similarity.Shingler;
import io.algernon.vespera.synthesis.Clusters;
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
 * Stage 6b's gate and its run, end to end (ADR-107, ADR-108, ADR-110, ADR-114, #179).
 *
 * <p>Nothing is generated yet. What is under test is the decision the step makes before anything
 * could be: whether the operator has approved the arrangement it would write over, and what is
 * recorded when they have.
 *
 * <p><b>The two shut states are one outcome and the ambiguous one is not.</b> Nothing approved, and
 * an approval naming no arrangement of this corpus, both end the invocation successfully having
 * written nothing — in each case nobody has approved anything, and a typo must not be able to behave
 * like an approval. An approval naming two arrangements stops instead, which is what this system
 * already does anywhere it would otherwise have to guess which of two runs was meant (ADR-099).
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
@Issue("179")
@Link(name = "ADR-107", url = Adr.THE_ARRANGEMENT_GATE_APPROVES_A_NAMED_RUN, type = "adr")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
@Link(name = "ADR-114", url = Adr.THE_GENERATION_MODEL_IS_CONFIGURATION_WITH_A_DEFAULT, type = "adr")
class GenerationInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the way every other invocation test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model this fixture names, so gate 3 and everything behind it open. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** An approval of the right shape that is nobody's arrangement: twelve hexadecimal characters. */
    private static final String NAMES_NOTHING = "0123456789ab";

    /** What one approval is worth: one opening, and not a second one over the same approval. */
    private static final int ONE_OPENING = 1;

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
    @Story("Nothing is written over the archive until a person approves what they read")
    @DisplayName("With the groups approved, the next invocation opens on exactly the groups that were read")
    void opensOnTheArrangementThatWasApproved(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement()));

        cli.run("run", root.toString());

        claim("the second invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "one opening was recorded and no more: an approval is spent on the arrangement it named,"
                        + " and a second opening over the same approval would be a second claim on work"
                        + " already accounted for",
                () -> assertThat(generationRuns()).hasSize(ONE_OPENING));
        claim(
                "and what it reads is the very arrangement the approval named, rather than whichever was"
                        + " arranged most recently -- an approval is of a particular shape of the archive,"
                        + " and writing over a later one would spend an approval nobody gave",
                () -> assertThat(upstreamOf(generationRuns().getFirst()))
                        .containsExactly(theApprovedArrangement().value()));
    }

    @Test
    @Story("Nothing is written over the archive until a person approves what they read")
    @DisplayName("With nothing approved, the invocation succeeds and nothing is opened")
    void opensNothingWhenNothingIsApproved(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        approve(null);

        cli.run("run", root.toString());

        claim(
                "the invocation succeeded rather than failing: a person who has not looked at the"
                        + " arrangement yet has done nothing wrong, and an invocation ending in red would"
                        + " be telling them they had",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and nothing at all was opened -- not an empty opening, no record of one: what is"
                        + " recorded is what was actually done, and nothing was",
                () -> assertThat(generationRuns()).isEmpty());
    }

    @Test
    @Story("Nothing is written over the archive until a person approves what they read")
    @DisplayName("An approval matching nothing is treated exactly as no approval at all")
    void opensNothingWhenTheApprovalMatchesNothing(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        approve(NAMES_NOTHING);

        cli.run("run", root.toString());

        claim(
                "a mistyped name leaves the archive exactly where a blank one does: the one outcome this"
                        + " must never have is quietly behaving like an approval, because a person who"
                        + " mistyped believes they approved something",
                () -> assertThat(generationRuns()).isEmpty());
        claim(
                "and the invocation still succeeded, because a name matching nothing is something to"
                        + " correct and run again, not a broken tool",
                () -> assertThat(cli.getExitCode()).isZero());
    }

    @Test
    @Story("An approval that names two things stops rather than guessing")
    @DisplayName("An approval matching two sets of groups stops the invocation instead of choosing one")
    void stopsWhenTheApprovalMatchesTwo(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        RunId arranged = theLatestArrangement();
        anotherArrangementSharingThePrefixOf(arranged);
        approve(ArrangementGate.shortNameOf(arranged));

        cli.run("run", root.toString());

        claim(
                "the invocation stopped rather than picking one of them: writing over the wrong one would"
                        + " be writing over something nobody read, and doing it without saying so",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "and it stopped before anything was opened, so there is no half-finished record of work"
                        + " nobody authorised",
                () -> assertThat(generationRuns()).isEmpty());
    }

    @Test
    @Story("Nothing is written over the archive until a person approves what they read")
    @DisplayName("Opening on the approved groups records no judgement against any document")
    void recordsNoJudgementAgainstAnyDocument(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement()));

        cli.run("run", root.toString());

        claim(
                "no judgement was recorded: every judgement this system records exists to take a document"
                        + " out of what gets published, and writing connecting text over what survived"
                        + " takes nothing out of anything",
                () -> assertThat(verdictCountUnder(generationRuns().getFirst())).isZero());
    }

    /** One corpus document and one exemplar, with every gate before this one open. */
    private void aCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(new Profile(
                new ProfileValue(seeds.toString(), "set by this test", null),
                profile.degenerateOutputConfidenceFloor(),
                new ProfileValue(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open", null),
                new ProfileValue(MODEL_NAME, "set by this test, so gate 3 is open", null)));
    }

    /** Writes the approval, leaving every other key as the fixture left it. */
    private void approve(String approval) {
        Profile loaded = profileStore.load();
        profileStore.save(new Profile(
                loaded.seedFolder(),
                loaded.degenerateOutputConfidenceFloor(),
                loaded.boilerplateDocumentFrequencyFloor(),
                loaded.embeddingModel(),
                loaded.relevanceScoreFloor(),
                new ProfileValue(approval, approval == null ? null : "read by this test", null)));
    }

    /** The arrangement the most recent invocation recorded — the one its page names. */
    private RunId theLatestArrangement() {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT id FROM run WHERE stage = ? ORDER BY rowid DESC LIMIT 1", String.class, ArrangementRun.STAGE));
    }

    /** The arrangement the profile's approval actually names, whichever invocation recorded it. */
    private RunId theApprovedArrangement() {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT id FROM run WHERE stage = ? AND id LIKE ? ORDER BY rowid LIMIT 1",
                String.class,
                ArrangementRun.STAGE,
                profileStore.load().arrangementApproved().value() + "%"));
    }

    /**
     * A second arrangement under the same walk whose id opens with the first's twelve characters,
     * written straight into the table because a content-derived id cannot be steered into a collision.
     * What is under test is what the invocation does when it meets one, not how likely that is.
     */
    private void anotherArrangementSharingThePrefixOf(RunId first) {
        String colliding = ArrangementGate.shortNameOf(first)
                + "f".repeat(first.value().length() - ArrangementGate.APPROVAL_LENGTH);
        jdbcTemplate.update(
                "INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id)"
                        + " SELECT ?, stage, 'v-collision', '{}', walk_id FROM run WHERE id = ?",
                colliding,
                first.value());
    }

    /** Every opening this corpus has recorded, oldest first. */
    private List<String> generationRuns() {
        return jdbcTemplate.queryForList(
                "SELECT id FROM run WHERE stage = ? ORDER BY rowid", String.class, GenerationRun.STAGE);
    }

    private List<String> upstreamOf(String runId) {
        return jdbcTemplate.queryForList(
                "SELECT upstream_run_id FROM run_upstream WHERE run_id = ?", String.class, runId);
    }

    private int verdictCountUnder(String runId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM verdict WHERE run_id = ?", Integer.class, runId);
    }
}
