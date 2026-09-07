package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.embedding.UnusableSeeds;
import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.HybridChunkerBeans;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
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
import java.util.Arrays;
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
 * What one invocation does with the seed folder (ADR-083), and — the half that matters more — what it
 * refuses to leave behind when there is nothing to work with.
 *
 * <p>A sibling of {@link CensusInvocationTest} rather than more tests inside it, for the reason
 * {@link RunUpstreamChainTest} is one: that class asserts the behaviour of a profile with everything
 * <em>unset</em>, and every claim here needs a seed folder named and stage 4's gate open. Different
 * fixture, so its own working directory and its own database.
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
    SeedMeasurementRun.class,
    SeedGate.class,
    RedundancySignatures.class,
    RedundancyResolution.class,
    BoilerplateShingles.class,
    DocumentFrequency.class,
    ConfidenceDistribution.class,
    UnusableSeeds.class,
    Shingler.class,
    HybridChunkerBeans.class,
    SeedScriptedExtractionBeans.class,
    ExtractionMetrics.class,
    LanguageDetection.class,
    ContentIdentity.class,
    WalkRecorder.class,
    AnomalyLog.class,
    Ledger.class,
    ImplementationVersions.class,
    ProfileStore.class,
    VesperaCommand.class,
    VesperaCommand.Run.class,
    VesperaCommand.Publish.class,
    VesperaCli.class
})
@Epic("Relevance")
@Feature("Seed set")
@Issue("104")
@Link(name = "ADR-083", url = Adr.THE_SEED_SET_IS_EXTRACTED_BY_STAGE_5, type = "adr")
class SeedExtractionInvocationTest {

    @TempDir
    static Path workingDirectory;

    /**
     * A floor of 1.0: a shingle is boilerplate only when every single document carries it. The least
     * aggressive value that still opens stage 4's gate, because nothing here is about what stage 4
     * concludes — only about stage 5 having a stage-4 run to name upstream.
     */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /**
     * The four stages ahead of stage 5 that mint a run: the byte-level reduction, extraction, the
     * content census, and redundancy - whose two steps share one run (ADR-080). Census mints none at
     * all, because it writes no verdicts.
     */
    private static final int STAGES_THAT_MINT_A_RUN_BEFORE_STAGE_5 = 4;

    /**
     * Stage 5 mints exactly one measurement run over a seed folder, and only if a seed produced text
     * (ADR-083's gate) — which is what makes its existence evidence that the pass reached the seed
     * after the unusable one.
     */
    private static final int ONE_MEASUREMENT_RUN = 1;

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
    private UnusableSeeds unusableSeeds;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("The seed set is extracted under its own measurement run")
    @DisplayName("A named seed folder is extracted, and the run names stage 4's run upstream")
    @Link(name = "ADR-089", url = Adr.A_RUN_NAMES_ITS_IMMEDIATE_PREDECESSOR_UPSTREAM, type = "adr")
    void extractsTheSeedSetAndNamesStageFourUpstream(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reported success, so what follows is what a completed pass left rather"
                        + " than the wreckage of a failed one",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "exactly one measurement run was minted: the seed folder was named, its walk had"
                        + " finished, and at least one seed produced text -- the three conditions that have"
                        + " to hold before a run row exists at all",
                () -> assertThat(runIdsFor("seed-measurement", root)).hasSize(1));
        claim(
                "and that run names the redundancy run as the one before it, rather than reaching"
                        + " further back to extraction. What this stage goes on to read is the documents"
                        + " still standing, and a document stops standing the moment any earlier pass rules"
                        + " it out -- so a run that skipped the pass before it would carry an identity that"
                        + " two different sets of surviving documents could share",
                () -> assertThat(upstreamStagesOf(runIdsFor("seed-measurement", root).getFirst()))
                        .containsExactly("content-redundancy"));
        claim(
                "no seed was recorded unusable, because the one seed converted with text in it",
                () -> assertThat(unusableSeeds.forRun(
                                runIdsFor("seed-measurement", root).getFirst()))
                        .isEmpty());
    }

    @Test
    @Story("A seed is never judged")
    @DisplayName("No verdict of any kind stands against a seed occurrence, usable or not")
    void writesNoVerdictAgainstAnySeedOccurrence(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Files.writeString(seeds.resolve(SeedScriptedExtractionBeans.EMPTY_SEED), "not a document");
        profile(seeds);

        cli.run("run", root.toString());

        WalkId seedWalk = theSeedWalkOf(seeds);

        claim(
                "the invocation reported success: an unusable seed is recorded, not a failure, and"
                        + " scoring proceeds against the seeds that survived extraction",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the seed that produced no text is recorded as unusable, with the reason -- an operator"
                        + " fixing a seed folder needs to know which file and what was wrong with it",
                () -> assertThat(unusableSeeds
                                .forRun(runIdsFor("seed-measurement", root).getFirst())
                                .stream()
                                .map(seed -> ledger.factsFor(seed.occurrenceId())
                                        .orElseThrow()
                                        .path()
                                        .value())
                                .toList())
                        .containsExactly(SeedScriptedExtractionBeans.EMPTY_SEED));
        claim(
                "and not one verdict of any kind stands against any seed document -- asserted against"
                        + " the whole fixed vocabulary rather than the likely-looking kinds, because every"
                        + " word in it exists to remove a document from what gets published, and a seed is"
                        + " never published",
                () -> assertThat(verdictKindsAgainstOccurrencesOf(seedWalk)).isEmpty());
        claim(
                "and stage 5's run exists, which is what says the pass carried on past the unusable"
                        + " seed rather than stopping at it: the run is minted only once a seed has"
                        + " produced text, so a pass that gave up at the first empty document would"
                        + " leave the gate shut, no run, and — since an unusable-seed row carries the run"
                        + " that found it — not even the row claimed above",
                () -> assertThat(runIdsFor("seed-measurement", root)).hasSize(ONE_MEASUREMENT_RUN));
    }

    @Test
    @Story("A gate ends the invocation rather than failing it")
    @DisplayName("With no seed producing text, stage 5 mints no run at all and the command still succeeds")
    void mintsNoRunWhenNoSeedIsUsable(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve(SeedScriptedExtractionBeans.EMPTY_SEED), "not a document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success: a required value that nobody has supplied is not an"
                        + " error, and the run ends there having recorded what the earlier passes learned",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and no measurement run was minted at all. A relevance score is the highest score"
                        + " against any seed document, and over no seed documents there is no such value --"
                        + " so a run row here would claim a measurement that cannot exist",
                () -> assertThat(runIdsFor("seed-measurement", root)).isEmpty());
        claim(
                "no unusable-seed row was written either, because those rows carry the run that found"
                        + " them and there is no run -- what the operator gets instead is a successful"
                        + " invocation that removed nothing, and a seed folder to fix",
                () -> assertThat(unusableSeedRowsAgainst(theSeedWalkOf(seeds))).isZero());
        claim(
                "and the four earlier stages each still minted their run over this corpus: stage 5's gate"
                        + " ends stage 5, not the invocation, so nothing the cheaper passes learned is lost",
                () -> assertThat(runCountOver(root)).isEqualTo(STAGES_THAT_MINT_A_RUN_BEFORE_STAGE_5));
    }

    @Test
    @Story("A gate ends the invocation rather than failing it")
    @DisplayName("With no seed folder named, stage 5 mints no run and converts nothing")
    @Link(name = "ADR-064", url = Adr.THE_WALK_INSTRUMENT_GENERALIZES, type = "adr")
    void mintsNoRunWhenNoSeedFolderIsNamed(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        openTheBoilerplateGateOnly();

        cli.run("run", root.toString());

        claim(
                "the invocation reports success -- a corpus can be censused, reduced and deduplicated"
                        + " before anybody has decided what the seed set is",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and stage 5 minted no run, because there is no seed folder to have measured anything"
                        + " against",
                () -> assertThat(runIdsFor("seed-measurement", root)).isEmpty());
    }

    /** The seed folder named and stage 4's gate open — the fixture every claim above the last needs. */
    private void profile(Path seeds) {
        Profile profile = profileStore.load();
        profileStore.save(new Profile(
                new ProfileValue(seeds.toString(), "set by this test", null),
                profile.degenerateOutputConfidenceFloor(),
                new ProfileValue(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open", null)));
    }

    /** Stage 4's gate open, and deliberately no seed folder. */
    private void openTheBoilerplateGateOnly() {
        Profile profile = profileStore.load();
        profileStore.save(new Profile(
                new ProfileValue(null, null, null),
                profile.degenerateOutputConfidenceFloor(),
                new ProfileValue(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open", null)));
    }

    /**
     * The runs one stage minted <em>over this test's own corpus walk</em>.
     *
     * <p>Scoped to the walk rather than counted globally, because the whole class shares one database:
     * an unscoped query would read the run another test's fixture left behind, and two of the claims
     * below are about the absence of a run - exactly the shape that passes or fails on somebody
     * else's rows.
     */
    private List<RunId> runIdsFor(String stage, Path root) {
        return jdbcTemplate
                .queryForList(
                        "SELECT id FROM run WHERE stage = ? AND walk_id = ?",
                        String.class,
                        stage,
                        theCorpusWalkOf(root).value())
                .stream()
                .map(RunId::new)
                .toList();
    }

    private WalkId theSeedWalkOf(Path seeds) {
        return ledger.finishedWalkFor(Walk.canonicalRoot(seeds))
                .orElseThrow(() -> new IllegalStateException("census recorded no finished walk of " + seeds));
    }

    private WalkId theCorpusWalkOf(Path root) {
        return ledger.finishedWalkFor(Walk.canonicalRoot(root))
                .orElseThrow(() -> new IllegalStateException("census recorded no finished walk of " + root));
    }

    /** Which stages the runs named upstream of {@code runId} were minted by. */
    private List<String> upstreamStagesOf(RunId runId) {
        return jdbcTemplate.queryForList(
                "SELECT r.stage FROM run_upstream u JOIN run r ON r.id = u.upstream_run_id WHERE u.run_id = ?",
                String.class,
                runId.value());
    }

    /** How many runs of any stage stand over this test's own corpus walk. */
    private long runCountOver(Path root) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM run WHERE walk_id = ?", Long.class, theCorpusWalkOf(root).value());
    }

    /** Unusable-seed rows against occurrences of this test's own seed walk. */
    private long unusableSeedRowsAgainst(WalkId seedWalk) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM unusable_seed u JOIN file_occurrence o ON o.id = u.occurrence_id"
                        + " WHERE o.walk_id = ?",
                Long.class,
                seedWalk.value());
    }

    /** Every verdict kind standing against any occurrence of one walk, whatever it says. */
    private List<String> verdictKindsAgainstOccurrencesOf(WalkId walkId) {
        List<String> kinds = jdbcTemplate.queryForList(
                "SELECT v.kind FROM verdict v JOIN file_occurrence o ON o.id = v.occurrence_id WHERE o.walk_id = ?",
                String.class,
                walkId.value());
        List<String> vocabulary = Arrays.stream(VerdictKind.values()).map(Enum::name).toList();
        if (!vocabulary.containsAll(kinds)) {
            throw new IllegalStateException("a verdict row carries a kind outside the closed vocabulary: " + kinds);
        }
        return kinds;
    }
}
