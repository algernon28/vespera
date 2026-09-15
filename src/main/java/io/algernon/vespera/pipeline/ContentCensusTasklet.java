package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.ledger.Ledger;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stage 3: content census. A tasklet rather than a chunk-oriented step (the stage-3 hand-off spec's
 * settlement of the map's one item of fog): this stage reads already-stored columns and rows
 * ({@code shingle}, {@code extraction_metric}) rather than per-occurrence documents needing
 * conversion, so there is no per-item fault-tolerance need the way stage 2's Docling calls had — the
 * same shape {@link CensusTasklet} and {@link ByteLevelReductionTasklet} already use.
 *
 * <p>Drives {@code similarity}'s document-frequency pass (ADR-038, ADR-074) and {@code extraction}'s
 * confidence-distribution report (ADR-075) under the run {@link ContentCensusRun} minted. Writes no
 * verdict of any kind — stage 3 measures, it does not judge.
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

    private static final Logger log = LoggerFactory.getLogger(ContentCensusTasklet.class);

    private final DocumentFrequency documentFrequency;
    private final ConfidenceDistribution confidenceDistribution;
    private final ContentCensusRun contentCensusRun;
    private final Ledger ledger;
    private final ProfileStore profileStore;
    private final Clock clock;
    private final Path workingDirectory;

    ContentCensusTasklet(
            DocumentFrequency documentFrequency,
            ConfidenceDistribution confidenceDistribution,
            ContentCensusRun contentCensusRun,
            Ledger ledger,
            ProfileStore profileStore,
            Clock clock,
            @Value("${vespera.working-dir}") Path workingDirectory) {
        this.documentFrequency = documentFrequency;
        this.confidenceDistribution = confidenceDistribution;
        this.contentCensusRun = contentCensusRun;
        this.ledger = ledger;
        this.profileStore = profileStore;
        this.clock = clock;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // This step's own work under this run is already measured, so there is nothing here to do
        // (ADR-115, ADR-116). The report beside the database is not rewritten either: it was written
        // from these very rows, and a step that skipped its measuring and rewrote its page would be
        // claiming to have looked again.
        if (ledger.stepFinished(contentCensusRun.runId(), ContentCensusRun.STAGE)) {
            log.info(
                    "Stage 3 (content census) was already recorded under run {}",
                    contentCensusRun.runId().value());
            return RepeatStatus.FINISHED;
        }

        // Not finished: an invocation that stopped partway may have left rows behind under this same run id. Discarding
        // this step's own rows before working is ADR-115's other half (ADR-116).
        documentFrequency.discardForRun(contentCensusRun.runId());
        confidenceDistribution.discardForRun(contentCensusRun.runId());

        log.info("Stage 3 (content census) starting under run {}", contentCensusRun.runId().value());

        documentFrequency.measure(contentCensusRun.runId(), contentCensusRun.extractionRunId());
        log.info("Stage 3 (content census) measured shingle document frequency");

        ConfidenceDistribution.Distribution distribution =
                confidenceDistribution.measure(contentCensusRun.runId(), contentCensusRun.extractionRunId());
        log.info("Stage 3 (content census) measured the confidence distribution");
        Path reportFile = writeReport(distribution);

        Profile profile = profileStore.load();
        profileStore.save(profile.withDegenerateOutputConfidenceFloorMeasurement(
                new Measurement(reportFile.toString(), clock.instant())));

        ledger.finishStep(contentCensusRun.runId(), ContentCensusRun.STAGE);
        log.info(
                "Stage 3 (content census) finished under run {}; report written to {}",
                contentCensusRun.runId().value(),
                reportFile);
        return RepeatStatus.FINISHED;
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
