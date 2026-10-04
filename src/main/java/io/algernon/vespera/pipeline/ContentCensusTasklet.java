package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Measurement;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.similarity.DocumentFrequency;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stage 3: content census. A tasklet rather than a chunk-oriented step (the stage-3 hand-off spec's
 * settlement of the map's one item of fog): this stage reads already-stored columns and rows
 * ({@code shingle}, {@code extraction_metric}) rather than per-occurrence documents needing
 * conversion, so there is no per-item fault-tolerance need the way stage 2's Docling calls had — the
 * same shape {@link CensusTasklet} and {@link ByteLevelReductionTasklet} already use.
 *
 * <p>Drives {@code similarity}'s document-frequency pass (ADR-038, ADR-074) and {@code extraction}'s
 * confidence-distribution report (ADR-075) under the run {@link StageRuns#contentCensus} mints.
 * Writes no verdict of any kind — stage 3 measures, it does not judge.
 *
 * <p>The confidence-distribution HTML file and the profile pointer to it are composed here rather
 * than inside {@code extraction} (ADR-040: a capability module may depend on {@code ledger} and
 * nothing else horizontal) — {@link ConfidenceDistribution} hands back the same {@code Distribution}
 * value it wrote to its own table, and this class is what renders that value as a file and points the
 * profile at it, the composition only {@code pipeline} may do (ADR-075).
 */
@Component
@StepScope
class ContentCensusTasklet implements Tasklet {

    /** The confidence-distribution report's fixed name in the working directory (ADR-075). */
    static final String CONFIDENCE_DISTRIBUTION_FILE_NAME = "confidence-distribution.html";

    /** For the seconds the measurement of shingle document frequency took, as its line states them (ADR-191). */
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private static final Logger log = LoggerFactory.getLogger(ContentCensusTasklet.class);

    private final DocumentFrequency documentFrequency;
    private final ConfidenceDistribution confidenceDistribution;
    private final StageRuns stageRuns;
    private final Ledger ledger;
    private final ProfileStore profileStore;
    private final Clock clock;
    private final Path workingDirectory;
    private final JdbcTemplate jdbcTemplate;

    ContentCensusTasklet(
            DocumentFrequency documentFrequency,
            ConfidenceDistribution confidenceDistribution,
            StageRuns stageRuns,
            Ledger ledger,
            ProfileStore profileStore,
            Clock clock,
            @Value("${vespera.working-dir}") Path workingDirectory,
            JdbcTemplate jdbcTemplate) {
        this.documentFrequency = documentFrequency;
        this.confidenceDistribution = confidenceDistribution;
        this.stageRuns = stageRuns;
        this.ledger = ledger;
        this.profileStore = profileStore;
        this.clock = clock;
        this.workingDirectory = workingDirectory;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        RunId runId = stageRuns.contentCensus();
        RunId extractionRunId = stageRuns.upstream(StageModules.EXTRACTION);
        return TaskletSteps.once(
                ledger,
                runId,
                StepNames.CONTENT_CENSUS,
                // This step's own work under this run is already measured, so there is nothing here
                // to do (ADR-115, ADR-116). The report beside the database is not rewritten either:
                // it was written from these very rows, and a step that skipped its measuring and
                // rewrote its page would be claiming to have looked again.
                () -> log.info("Stage 3 (content census) was already recorded under run {}", runId.value()),
                () -> {
                    documentFrequency.discardForRun(runId);
                    confidenceDistribution.discardForRun(runId);
                },
                () -> {
                    log.info("Stage 3 (content census) starting under run {}", runId.value());

                    // The one statement inside DocumentFrequency.measure that reads every shingle row of
                    // stage 2's run took half an hour on a 16.7 GB database on a USB spinning disk and said
                    // nothing, so the read has a line before it where there is something to read, and the
                    // measurement has its time after it whether or not there is (ADR-191). The time is the
                    // whole call's: this class cannot time the one statement apart from the rest, and
                    // similarity is not touched for it.
                    announceReadOf(extractionRunId);
                    long measureStarted = System.nanoTime();
                    documentFrequency.measure(runId, extractionRunId);
                    log.info(
                            "Stage 3 (content census) measured shingle document frequency in {} s",
                            String.format(Locale.ROOT, "%.1f", (System.nanoTime() - measureStarted) / NANOS_PER_SECOND));

                    ConfidenceDistribution.Distribution distribution =
                            confidenceDistribution.measure(runId, extractionRunId);
                    log.info("Stage 3 (content census) measured the confidence distribution");
                    Path reportFile = writeReport(distribution);

                    Profile profile = profileStore.load();
                    profileStore.save(profile.withDegenerateOutputConfidenceFloorMeasurement(
                            new Measurement(reportFile.toString(), clock.instant())));

                    log.info(
                            "Stage 3 (content census) finished under run {}; report written to {}",
                            runId.value(),
                            reportFile);
                    return true;
                });
    }

    /**
     * Says how many shingle rows of stage 2's run the measurement is about to read at most, and that
     * stopping loses only the time spent (ADR-191 section 1). Says nothing where the run holds none.
     *
     * <p>The bound is the span of the run's own rowids, greatest less least plus one (ADR-191 section 2).
     * It is asked as two statements on purpose. Each is one descent of {@code shingle_by_run_id}: 2 ms and
     * 40 KB for the pair with the file cache emptied. One statement asking for {@code MIN(rowid),
     * MAX(rowid)} together, or {@code COUNT(*)}, reads the run's whole part of the index instead: about
     * 2.9 s and 209 MB, measured for a run of 2,500,000 rows, with the cache emptied, on a solid-state
     * disk. On the disk where the announced read took half an hour, the same walk would be minutes more:
     * an estimate, not a measurement, from ADR-191's arithmetic of 51,092 pages at 100 pages a second,
     * about eight and a half minutes. No test can tell the two forms apart, so no test holds the
     * two-statement form in place; ADR-191 section 2 and this comment do.
     * The table's own {@code MAX(rowid)} is not used: it counts every run's rows, and the table keeps them.
     */
    private void announceReadOf(RunId extractionRunId) {
        Long least = jdbcTemplate.queryForObject(
                "SELECT MIN(rowid) FROM shingle WHERE run_id = ?", Long.class, extractionRunId.value());
        Long greatest = jdbcTemplate.queryForObject(
                "SELECT MAX(rowid) FROM shingle WHERE run_id = ?", Long.class, extractionRunId.value());
        if (least == null || greatest == null) {
            return;
        }
        log.info(
                "Stage 3 (content census) is reading up to {} shingle rows of stage 2's run before it measures"
                        + " anything; SQLite reads them a page at a time, from wherever in the file stage 2 wrote"
                        + " them, which took half an hour on a USB spinning disk for one run in a 16.7 GB database,"
                        + " and stopping before it ends loses only the time spent",
                greatest - least + 1);
    }

    /**
     * Writes the confidence-distribution report to the working directory, overwriting whatever an
     * earlier run left there (ADR-075) — never accumulated, never left stale.
     */
    private Path writeReport(ConfidenceDistribution.Distribution distribution) {
        Path reportFile = workingDirectory.resolve(CONFIDENCE_DISTRIBUTION_FILE_NAME);
        try {
            Files.createDirectories(workingDirectory);
            Files.writeString(reportFile, ConfidenceDistributionReport.render(distribution), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the confidence-distribution report at " + reportFile, e);
        }
        return reportFile;
    }
}
