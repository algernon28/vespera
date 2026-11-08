package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.AnomalyLog;
import io.algernon.vespera.engine.corpus.ContentIdentity;
import io.algernon.vespera.engine.corpus.DetectedFormats;
import io.algernon.vespera.engine.corpus.WalkRecorder;
import io.algernon.vespera.engine.extraction.ConfidenceDistribution;
import io.algernon.vespera.engine.extraction.ExtractorIdentity;
import io.algernon.vespera.engine.extraction.WordCountDistribution;
import io.algernon.vespera.engine.ledger.ImplementationVersions;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
import io.algernon.vespera.engine.profile.Measurement;
import io.algernon.vespera.engine.profile.Profile;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.profile.NumericValue;
import io.algernon.vespera.engine.similarity.DocumentFrequency;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import java.util.HashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 3's tasklet as one act (ADR-075): driving both {@code similarity}'s document-frequency pass
 * (already pinned for its own sake by {@link io.algernon.vespera.engine.similarity.DocumentFrequencyTest})
 * and {@code extraction}'s confidence-distribution report, then composing the HTML file and the
 * profile pointer — the composition only {@code pipeline} may do (ADR-040).
 *
 * <p>Assembled by hand against a real walk taken through byte-level reduction and a minted extraction
 * run. The {@code extraction_metric} rows are written directly for the fixture's own occurrences rather
 * than run through a real Docling call, since what this class pins is the report, not extraction's own
 * per-document judgement.
 *
 * <p><b>Only the fixture names how a run is minted:</b> {@link #stageRunsOver} builds the holder, and
 * {@link #contentCensusOver} and the line in {@link #walkedThroughExtractionWithScores} that mints
 * stage 2's run ask it. Every claim reads the run this invocation recorded, through {@link
 * InvocationRuns}, never through the class that minted it, which is why ADR-157's folding of the run
 * classes into one holder changed those methods and moved no claim.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Content census")
@Issue("59")
@Link(name = "ADR-075", url = Adr.STAGE_3_WRITES_A_CONFIDENCE_DISTRIBUTION_REPORT, type = "adr")
@Link(name = "ADR-077", url = Adr.A_REGENERATED_MEASUREMENT_IS_KEYED_PER_RUN, type = "adr")
class ContentCensusTaskletTest {

    /**
     * Stage 1's working directory, where its {@code profile.yaml} is written: a temporary folder of
     * this test's own, outside every corpus root so no walk reads it, and shared with no other test,
     * class, run or branch. A shared one let a stale {@code profile.yaml} from elsewhere change what
     * stages 1 and 2 do here. It is not the census's working directory, which each test names itself.
     */
    @TempDir
    Path stage1Working;

    private static final ExtractorIdentity IDENTITY = new ExtractorIdentity("docling-serve;base-url=http://example");

    /**
     * The invocation each corpus root was taken through extraction in, so stage 3 is handed the record
     * that invocation's stages 1 and 2 actually wrote (ADR-154).
     */
    private final Map<Path, ExecutionContext> invocations = new HashMap<>();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("The profile points at the report")
    @DisplayName("An operator's own confidence-floor value and provenance survive a census run untouched")
    void neverTouchesAConfidenceFloorAnswerAlreadyInTheFile(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        walkedThroughExtractionWithScores(ledger, versions, root, new double[] {0.95});
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        Instant firstMeasuredAt = Instant.parse("2026-09-01T09:00:00Z");
        profileStore.save(ProfileFixture.profile()
                .degenerateOutputConfidenceFloor(new NumericValue(
                        "0.55",
                        "matched to last quarter's manual review",
                        new Measurement("some earlier report", firstMeasuredAt)))
                .build());
        Instant ranAt = Instant.parse("2026-09-05T12:00:00Z");
        Clock clock = Clock.fixed(ranAt, ZoneOffset.UTC);

        contentCensusOver(ledger, versions, root, profileStore, clock, workingDirectory).execute(null, null);

        Profile afterTheRun = profileStore.load();
        Path reportFile = workingDirectory.resolve(ContentCensusTasklet.CONFIDENCE_DISTRIBUTION_FILE_NAME);
        claim(
                "the operator's threshold value is exactly what it was before this run",
                () -> assertThat(afterTheRun.degenerateOutputConfidenceFloor().value()).isEqualTo("0.55"));
        claim(
                "so is the provenance they wrote beside it",
                () -> assertThat(afterTheRun.degenerateOutputConfidenceFloor().provenance())
                        .isEqualTo("matched to last quarter's manual review"));
        claim(
                "and census's own pointer moved on to this run's own report and time, not left pointing"
                        + " at the earlier one",
                () -> assertThat(afterTheRun.degenerateOutputConfidenceFloor().measurement())
                        .isEqualTo(new Measurement(reportFile.toString(), ranAt)));
    }

    /**
     * Stage 3's tasklet as the step builds it, over the content-census run this invocation of {@code
     * root} mints: the one place this class says how that run comes to exist.
     */
    private ContentCensusTasklet contentCensusOver(
            Ledger ledger,
            ImplementationVersions versions,
            Path root,
            ProfileStore profileStore,
            Clock clock,
            Path workingDirectory) {
        return new ContentCensusTasklet(
                new DocumentFrequency(jdbcTemplate, ledger),
                new ConfidenceDistribution(jdbcTemplate, ledger),
                new WordCountDistribution(jdbcTemplate, ledger),
                new DetectedFormats(jdbcTemplate),
                stageRunsOver(ledger, versions, root),
                ledger,
                profileStore,
                clock,
                workingDirectory);
    }

    /**
     * The holder a stage asks for its run through (ADR-157), over the invocation that took {@code root}
     * through byte-level reduction. Stages 2 and 3 pass no gate, so the gates and the later stages'
     * collaborators are left out: an accessor that needed one would fail here rather than mint.
     *
     * <p>The profile is the one in the working directory stage 1 ran in, because stage 2 reads {@code
     * extractionAttempt} from it fresh when its run is minted (ADR-185 §1). Nothing here writes that
     * key, so every run this class mints is the first attempt's.
     */
    private StageRuns stageRunsOver(Ledger ledger, ImplementationVersions versions, Path root) {
        return new StageRuns(
                ledger,
                versions,
                new StaticListableBeanFactory(Map.of("extractorIdentity", IDENTITY))
                        .getBeanProvider(ExtractorIdentity.class),
                new DegenerateOutputConfidenceFloor(null),
                null,
                null,
                null,
                new ProfileStore(stage1Working),
                null,
                null,
                null,
                null,
                PresetsFixture.shipped(),
                root,
                invocations.get(root));
    }

    /**
     * Walks {@code root}, runs byte-level reduction and extraction for real against it, then writes an
     * {@code extraction_metric} row carrying each of {@code meanScores} for the files it just walked --
     * the fixture {@link ConfidenceDistribution} needs actually present, without a real Docling call.
     */
    private RunId walkedThroughExtractionWithScores(
            Ledger ledger, ImplementationVersions versions, Path root, double[] meanScores) throws Exception {
        for (int i = 0; i < meanScores.length; i++) {
            Files.writeString(root.resolve("document-" + i + ".txt"), "the content of document " + i);
        }
        WalkId walkId = new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource))
                .walk(root);
        ChunkContext step = InvocationRecordFixture.aStepOfAFreshInvocation();
        new ByteLevelReductionTasklet(ledger, new ContentIdentity(jdbcTemplate), new DetectedFormats(jdbcTemplate), versions, new io.algernon.vespera.engine.profile.ProfileStore(stage1Working), root, stage1Working).execute(null, step);
        invocations.put(root, InvocationRecordFixture.recordOf(step));
        RunId extractionRunId = stageRunsOver(ledger, versions, root).extraction();

        for (int i = 0; i < meanScores.length; i++) {
            OccurrenceId occurrenceId =
                    ledger.occurrences().occurrenceId(walkId, new OccurrencePath("document-" + i + ".txt")).orElseThrow();
            jdbcTemplate.update(
                    "INSERT INTO extraction_metric"
                            + " (occurrence_id, run_id, status, processing_time, character_count,"
                            + " alphanumeric_char_count, word_count, word_character_length_total,"
                            + " vowelless_word_count, single_character_word_count, mean_score)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    occurrenceId.value(),
                    extractionRunId.value(),
                    "success",
                    0.5,
                    100,
                    90,
                    20,
                    80,
                    0,
                    0,
                    meanScores[i]);
        }
        return extractionRunId;
    }
}
