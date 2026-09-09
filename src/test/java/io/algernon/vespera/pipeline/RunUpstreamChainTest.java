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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * The shape of the run chain one full invocation leaves behind (ADR-089).
 *
 * <p>A sibling of {@link CensusInvocationTest} rather than another test inside it, and for one
 * reason: that class runs with the boilerplate floor <em>unset</em>, which is itself an asserted
 * behaviour there — stage 4's gate is closed, so no {@code content-redundancy} run is ever minted.
 * A chain covering every stage needs the opposite fixture, so it needs its own working directory and
 * its own database.
 *
 * <p>What this exists to catch is the defect ADR-089 records: a run whose upstream skips a stage.
 * That was found by a person reading {@link Ledger#survivors}'s javadoc while writing a spec, not by
 * the build, and it produced two run ids that would each have claimed a different corpus was the
 * same one. One query over two tables is a cheap place to make the next one fail loudly.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(BatchAutoConfiguration.class)
@Import({
    CensusJobConfiguration.class,
    SeedExtractionJobConfiguration.class,
    SeedExtractionItemProcessor.class,
    SeedExtractionItemWriter.class,
    SeedCorpusComparisonJobConfiguration.class,
    SeedCorpusComparisonTasklet.class,
    EmbeddingModelJobConfiguration.class,
    EmbeddingScoringTasklet.class,
    RelevanceScoringJobConfiguration.class,
    RelevanceScoringTasklet.class,
    RelevanceReportJobConfiguration.class,
    RelevanceReportTasklet.class,
    RelevanceDistribution.class,
    EmbeddingModelGate.class,
    ScoringRun.class,
    ChunkEmbedderBeans.class,
    RelevanceScoringBeans.class,
    EmbeddingScriptedBeans.class,
    SeedMeasurementRun.class,
    SeedGate.class,
    UsableSeedGate.class,
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
    RedundancySignatures.class,
    RedundancyResolution.class,
    BoilerplateShingles.class,
    DocumentFrequency.class,
    ConfidenceDistribution.class,
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
    SeedCorpusComparison.class,
    UnusableSeeds.class,
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
@Issue("97")
@Link(name = "ADR-089", url = Adr.A_RUN_NAMES_ITS_IMMEDIATE_PREDECESSOR_UPSTREAM, type = "adr")
@Link(name = "ADR-048", url = Adr.WALK_AND_RUN_IDENTITY, type = "adr")
class RunUpstreamChainTest {

    /** Where the profile is written, so the floor can be set before the job ever reads it. */
    @TempDir
    static Path workingDirectory;

    /**
     * The stages that mint a run, in the order {@code vesperaJob} runs them. Census writes no
     * verdicts and mints no run at all, so it is deliberately absent; {@code redundancy-signature}
     * and {@code content-redundancy} are two steps sharing one run (ADR-080), so the run appears once.
     */
    private static final List<String> STAGES_THAT_MINT_A_RUN =
            List.of("byte-level-reduction", "extraction", "content-census", "content-redundancy");

    /**
     * A floor of 1.0: a shingle counts as boilerplate only when it appears in every single document.
     * The value is deliberately the least aggressive one that still opens the gate, because this test
     * is about the run rows and not about what stage 4 concludes.
     */
    private static final String BOILERPLATE_FLOOR = "1.0";

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
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Every run names the run before it")
    @DisplayName("One invocation past the boilerplate gate leaves an unbroken chain of runs, one per stage")
    void mintsOneRunPerStageEachNamingTheOneBefore(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "the first document");
        Files.writeString(root.resolve("b.txt"), "the second document");
        openTheBoilerplateGate();

        cli.run("run", root.toString());

        Map<String, List<String>> upstreamByStage = upstreamByStageFor(theWalkOf(root));
        List<String> stagesInChainOrder = new ArrayList<>(upstreamByStage.keySet());

        claim(
                "the invocation reported success, so the chain below is the one a completed run leaves"
                        + " rather than the wreckage of a failed one",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "every stage that judges documents minted exactly one run -- the byte-level reduction,"
                        + " extraction, the content census and redundancy -- and redundancy's two steps share"
                        + " a single run rather than minting one each",
                () -> assertThat(stagesInChainOrder).containsExactlyElementsOf(STAGES_THAT_MINT_A_RUN));
        claim(
                "the first stage names no upstream, because there is nothing before it: it is the root of"
                        + " the chain, and a row claiming otherwise would be pointing at a run that never ran",
                () -> assertThat(upstreamByStage.get("byte-level-reduction")).isEmpty());
        claim(
                "and every later stage names exactly one upstream: a run reads the corpus one stage before"
                        + " it left standing, and naming two would say it read two different corpora",
                () -> assertThat(upstreamByStage.values().stream()
                                .skip(1)
                                .filter(upstream -> upstream.size() != 1)
                                .toList())
                        .isEmpty());
    }

    @Test
    @Story("Every run names the run before it")
    @DisplayName("No run skips a stage, including the one stage that writes no verdicts")
    void namesTheImmediatelyPrecedingStageAndNeverReachesPastIt(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "the first document");
        Files.writeString(root.resolve("b.txt"), "the second document");
        openTheBoilerplateGate();

        cli.run("run", root.toString());

        WalkId walkId = theWalkOf(root);
        Map<String, List<String>> upstreamByStage = upstreamByStageFor(walkId);
        Map<String, String> stageByRunId = stageByRunIdFor(walkId);

        List<String> namedUpstreamStages = new ArrayList<>();
        List<String> expectedUpstreamStages = new ArrayList<>();
        for (int stage = 1; stage < STAGES_THAT_MINT_A_RUN.size(); stage++) {
            namedUpstreamStages.add(stageByRunId.get(
                    upstreamByStage.get(STAGES_THAT_MINT_A_RUN.get(stage)).getFirst()));
            expectedUpstreamStages.add(STAGES_THAT_MINT_A_RUN.get(stage - 1));
        }

        claim(
                "each run names the stage immediately before it and never reaches back past one -- so"
                        + " extraction names the byte-level reduction, the content census names extraction, and"
                        + " redundancy names the content census",
                () -> assertThat(namedUpstreamStages).containsExactlyElementsOf(expectedUpstreamStages));
        claim(
                "and the content census stays in the chain even though it renders no verdict of its own,"
                        + " because what it measures is what the stage after it acts on -- a chain filtered to"
                        + " the stages that judge would leave that dependency unrecorded",
                () -> assertThat(namedUpstreamStages).contains("content-census"));
        claim(
                "so following the chain from the last stage reaches every earlier one exactly once, with"
                        + " none skipped: a run id is derived from everything that determines what it would"
                        + " produce, and a skipped stage is a run claiming two different corpora are the same",
                () -> assertThat(walkTheChainBackFrom("content-redundancy", upstreamByStage, stageByRunId))
                        .containsExactlyElementsOf(STAGES_THAT_MINT_A_RUN.reversed()));
    }

    /**
     * Sets the boilerplate floor before the job runs. Census merges new keys and never touches a value
     * already in the file (ADR-062), so a floor written here survives the invocation that reads it.
     */
    private void openTheBoilerplateGate() {
        Profile profile = profileStore.load();
        profileStore.save(new Profile(
                profile.seedFolder(),
                profile.degenerateOutputConfidenceFloor(),
                new ProfileValue(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open", null)));
    }

    /** Each stage's upstream run ids, in the order the stages minted their runs. */
    private Map<String, List<String>> upstreamByStageFor(WalkId walkId) {
        Map<String, List<String>> upstreamByStage = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT r.stage AS stage, u.upstream_run_id AS upstream"
                        + " FROM run r LEFT JOIN run_upstream u ON u.run_id = r.id"
                        + " WHERE r.walk_id = ? ORDER BY r.rowid",
                resultSet -> {
                    String upstream = resultSet.getString("upstream");
                    List<String> upstreams =
                            upstreamByStage.computeIfAbsent(resultSet.getString("stage"), stage -> new ArrayList<>());
                    if (upstream != null) {
                        upstreams.add(upstream);
                    }
                },
                walkId.value());
        return upstreamByStage;
    }

    /** Which stage minted each run of this walk, so an upstream id can be read back as a stage name. */
    private Map<String, String> stageByRunIdFor(WalkId walkId) {
        Map<String, String> stageByRunId = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT id, stage FROM run WHERE walk_id = ?",
                resultSet -> {
                    stageByRunId.put(resultSet.getString("id"), resultSet.getString("stage"));
                },
                walkId.value());
        return stageByRunId;
    }

    /** The stages reached by following {@code run_upstream} back from one stage, that stage first. */
    private static List<String> walkTheChainBackFrom(
            String stage, Map<String, List<String>> upstreamByStage, Map<String, String> stageByRunId) {
        List<String> chain = new ArrayList<>();
        String current = stage;
        while (current != null && !chain.contains(current)) {
            chain.add(current);
            List<String> upstreams = upstreamByStage.getOrDefault(current, List.of());
            current = upstreams.isEmpty() ? null : stageByRunId.get(upstreams.getFirst());
        }
        return chain;
    }

    private WalkId theWalkOf(Path root) {
        return ledger.finishedWalkFor(Walk.canonicalRoot(root))
                .orElseThrow(() -> new IllegalStateException("no finished walk was recorded for " + root));
    }
}
