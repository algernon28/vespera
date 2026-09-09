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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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
 * The end state an invocation reaches while no embedding model has been named (ADR-086): it walks,
 * extracts the seed set, measures how far that set resembles the survivors it would be scored
 * against, writes the report — and stops there, having recorded what it learned.
 *
 * <p>A sibling of {@link SeedExtractionInvocationTest} rather than more tests inside it, for the same
 * reason that class is a sibling of {@link CensusInvocationTest}: every claim here needs the report to
 * have been written, so the fixture is a seed folder named, stage 4's gate open, and a working
 * directory whose contents are this class's own.
 *
 * <p>What the figures in the report are is pinned by {@link
 * io.algernon.vespera.embedding.SeedCorpusComparisonTest} against fixtures that can state them
 * exactly. What this class pins is where the two outputs go, that the file stands alone, and the three
 * things the invocation must <em>not</em> leave behind: a verdict, a profile key, and a repointed seed
 * folder (ADR-054 for the placement, ADR-086 for the rest).
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
    SeedMeasurementRun.class,
    SeedGate.class,
    UsableSeedGate.class,
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
@Feature("Seed set")
@Issue("106")
@Link(name = "ADR-086", url = Adr.SEED_CORPUS_MISMATCH_IS_MEASURED_AND_REPORTED, type = "adr")
@Link(name = "ADR-054", url = Adr.CORPUS_IS_ITS_ROOT_PATH, type = "adr")
class SeedCorpusComparisonInvocationTest {

    @TempDir
    static Path workingDirectory;

    /**
     * A floor of 1.0: a shingle is boilerplate only when every document carries it. The least
     * aggressive value that still opens stage 4's gate, because nothing here is about what stage 4
     * concludes — only about stage 5 having a stage-4 run to name upstream.
     */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The keys {@code profile.yaml} carries, and every one of them predates this measurement. */
    private static final List<String> THE_PROFILE_KEYS = List.of(
            "seedFolder",
            "degenerateOutputConfidenceFloor",
            "boilerplateDocumentFrequencyFloor",
            "embeddingModel",
            "relevanceScoreFloor");

    /**
     * What census writes against the seed-folder key: the walk it took of that folder. It answers
     * "were your seeds found", and this measurement answers something else, so it leaves it alone.
     */
    private static final String CENSUS_ANSWER_PREFIX = "walk ";

    /**
     * Wording that would make the report into a judgement or into one summarising figure, either of
     * which ADR-086 refuses: the operator judges, and the actionable fact is which signal diverged.
     */
    private static final List<String> WORDING_THE_REPORT_REFUSES =
            List.of("mismatch score", "overall score", "verdict", "acceptable", "recommend", "warning");

    /** A top-level key in the profile file: a name at the start of a line, followed by a colon. */
    private static final Pattern PROFILE_KEY = Pattern.compile("(?m)^(\\w+):");

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
    @Story("The measurement arrives before the money is spent")
    @DisplayName("With no embedding model named, the invocation still measures the seed set and writes the report")
    void measuresAndReportsWithNoModelNamed(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        RunId measurementRun = theMeasurementRunOver(root);
        claim(
                "the invocation reported success: nobody has named a model to score with, and a value the"
                        + " pipeline needs and does not have ends the run rather than failing it",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the comparison was written as data, keyed by the run that measured it, so it can be"
                        + " queried again rather than only read",
                () -> assertThat(comparisonRowsFor(measurementRun)).isPositive());
        claim(
                "and the report was written as a file too, which is the half an operator deciding whether"
                        + " to spend a day of compute actually reads",
                () -> assertThat(Files.exists(reportFile())).isTrue());
        claim(
                "and it sits in the working directory beside the database and the profile, not inside the"
                        + " corpus -- the pipeline writes nothing into the archive it is curating",
                () -> assertThat(reportFile().getParent()).isEqualTo(profileStore.file().getParent()));
    }

    @Test
    @Story("The report opens on its own")
    @DisplayName("The report is a whole HTML document that names no other file and fetches nothing")
    void writesASelfContainedReport(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        String html = Files.readString(reportFile());
        claim(
                "the file is a whole, standalone HTML document, openable without any other file present",
                () -> assertThat(html).contains("<!DOCTYPE html>").contains("<html").contains("</html>"));
        claim(
                "it names no other file and reaches for nothing over the network, so it opens on a laptop"
                        + " with no connection to the machine that wrote it",
                () -> assertThat(html)
                        .doesNotContain("<link")
                        .doesNotContain("<script")
                        .doesNotContain("src=")
                        .doesNotContain("href=\"http"));
        claim(
                "and nothing was written into the corpus or the seed folder: both hold exactly the files"
                        + " this test put in them, and no page of any kind was left among the documents",
                () -> assertThat(filesUnder(root, seeds))
                        .containsExactlyInAnyOrder(root.resolve("corpus.txt"), seeds.resolve("seed.txt")));
    }

    @Test
    @Story("A measurement, never a judgement")
    @DisplayName("The report states no single figure for the whole comparison and judges none of it")
    void statesNoSummarisingFigureAndNoJudgement(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        String html = Files.readString(reportFile());
        claim(
                "the report carries no single figure standing for the whole comparison, and does not say"
                        + " whether what it reports is close enough: a summarising figure would be a"
                        + " threshold nobody has measured, and it would flatten the one fact anybody can"
                        + " act on -- which of the four signals diverged -- into a number that cannot be"
                        + " acted on at all",
                () -> assertThat(html)
                        .doesNotContainIgnoringCase(WORDING_THE_REPORT_REFUSES.toArray(String[]::new)));
        claim(
                "and no verdict of any kind stands against any document, on either side. Every word in"
                        + " the fixed vocabulary exists to remove a document from what gets published, and"
                        + " this measurement removes nothing: the seeds were readable, the corpus document"
                        + " converted, and a difference in form between the two is not grounds for"
                        + " discarding either",
                () -> assertThat(verdictKindsAgainstOccurrencesOf(theCorpusWalkOf(root), theSeedWalkOf(seeds)))
                        .isEmpty());
    }

    @Test
    @Story("Nothing here is a threshold")
    @DisplayName("The profile gains no key, and the seed-folder key still answers the question census asked")
    void addsNoProfileKeyAndRepointsNothing(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim(
                "the profile carries exactly the keys it carried before this measurement ran, and none"
                        + " of its own pointing at this report: a key exists to hold a judgement the"
                        + " engine cannot make, and this measurement asks for none -- nothing reads it as"
                        + " a threshold",
                () -> assertThat(profileKeys()).containsExactlyInAnyOrderElementsOf(THE_PROFILE_KEYS));
        Profile profile = profileStore.load();
        claim(
                "and the seed-folder key still names the walk census took of that folder, which answers"
                        + " whether the seeds were found -- this measurement answers a different question,"
                        + " and repointing the key at it every time would leave its timestamp meaning"
                        + " nothing once census pointed it back on the next invocation",
                () -> assertThat(profile.seedFolder().measurement().source()).startsWith(CENSUS_ANSWER_PREFIX));
    }

    /** The seed folder named and stage 4's gate open — the fixture every claim above needs. */
    private void profile(Path seeds) {
        Profile profile = profileStore.load();
        profileStore.save(new Profile(
                new ProfileValue(seeds.toString(), "set by this test", null),
                profile.degenerateOutputConfidenceFloor(),
                new ProfileValue(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open", null)));
    }

    /** The report's own file, beside the database and the profile in the working directory. */
    private Path reportFile() {
        return workingDirectory.resolve(SeedCorpusComparisonTasklet.SEED_CORPUS_COMPARISON_FILE_NAME);
    }

    /**
     * Stage 5's measurement run over this test's own corpus walk, scoped to that walk because the
     * whole class shares one database and another test's fixture minted one too.
     */
    private RunId theMeasurementRunOver(Path root) {
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT id FROM run WHERE stage = ? AND walk_id = ?",
                String.class,
                "seed-measurement",
                theCorpusWalkOf(root).value());
        if (ids.size() != 1) {
            throw new IllegalStateException("this invocation minted " + ids.size() + " measurement runs, not one");
        }
        return new RunId(ids.getFirst());
    }

    private long comparisonRowsFor(RunId runId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seed_corpus_comparison WHERE run_id = ?", Long.class, runId.value());
    }

    private WalkId theCorpusWalkOf(Path root) {
        return ledger.finishedWalkFor(Walk.canonicalRoot(root))
                .orElseThrow(() -> new IllegalStateException("census recorded no finished walk of " + root));
    }

    private WalkId theSeedWalkOf(Path seeds) {
        return ledger.finishedWalkFor(Walk.canonicalRoot(seeds))
                .orElseThrow(() -> new IllegalStateException("census recorded no finished walk of " + seeds));
    }

    /** Every file the corpus root and the seed folder hold, whatever it is. */
    private List<Path> filesUnder(Path root, Path seeds) throws IOException {
        try (Stream<Path> corpusFiles = Files.walk(root);
                Stream<Path> seedFiles = Files.walk(seeds)) {
            return Stream.concat(corpusFiles, seedFiles)
                    .filter(Files::isRegularFile)
                    .toList();
        }
    }

    /** The names the profile file's own top-level entries carry. */
    private List<String> profileKeys() throws IOException {
        Matcher matcher = PROFILE_KEY.matcher(Files.readString(profileStore.file()));
        return matcher.results().map(result -> result.group(1)).toList();
    }

    /** Every verdict kind standing against any occurrence of the given walks, whatever it says. */
    private List<String> verdictKindsAgainstOccurrencesOf(WalkId... walkIds) {
        List<String> kinds = Arrays.stream(walkIds)
                .flatMap(walkId -> jdbcTemplate
                        .queryForList(
                                "SELECT v.kind FROM verdict v JOIN file_occurrence o ON o.id = v.occurrence_id"
                                        + " WHERE o.walk_id = ?",
                                String.class,
                                walkId.value())
                        .stream())
                .toList();
        List<String> vocabulary =
                Arrays.stream(VerdictKind.values()).map(Enum::name).toList();
        if (!vocabulary.containsAll(kinds)) {
            throw new IllegalStateException("a verdict row carries a kind outside the closed vocabulary: " + kinds);
        }
        return kinds;
    }
}
