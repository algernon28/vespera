package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.SeedCorpusComparison;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stage 5's second step (ADR-086, ADR-092): measures how far the seed set resembles the corpus
 * survivors it would be scored against, and writes both outputs ADR-075's shape asks for — the
 * re-analyzable {@code seed_corpus_comparison} rows and the HTML report — before a single vector is
 * computed. Sits before the embedding-model gate, since {@link SeedCorpusComparison} reads only
 * stored {@code extraction_metric} columns and needs no model at all.
 *
 * <p>A tasklet, like {@link RedundancyResolutionTasklet}: this is one corpus-wide read over rows two
 * earlier passes already wrote, not a per-document conversion.
 *
 * <p>Gated twice before it reaches {@link SeedMeasurementRun}, the same way {@link
 * RedundancyResolutionTasklet} checks {@link RedundancyGate} first: {@link SeedGate} for whether a
 * seed folder was walked at all, and {@link UsableSeedGate} for whether any seed produced text. Either
 * one shut means the first step minted no run, and reaching for it here would mint one over a folder
 * nothing was ever measured against — a run row for a measurement that cannot exist, which is exactly
 * what ADR-083's gate refuses. So this step logs and completes having written nothing, the shape
 * stage 5's first step already established for a shut gate.
 *
 * <p>Writes no verdict and no profile key (ADR-086): a difference in form between the seed set and the
 * corpus is not grounds for removing anything, and this measurement is not a threshold any later stage
 * reads.
 */
@Component
@StepScope
class SeedCorpusComparisonTasklet implements Tasklet {

    /** The seed/corpus comparison report's fixed name in the working directory (ADR-086). */
    static final String SEED_CORPUS_COMPARISON_FILE_NAME = "seed-corpus-comparison.html";

    private static final Logger LOG = LoggerFactory.getLogger(SeedCorpusComparisonTasklet.class);

    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final ObjectProvider<SeedMeasurementRun> seedMeasurementRun;
    private final SeedCorpusComparison seedCorpusComparison;
    private final Path workingDirectory;

    SeedCorpusComparisonTasklet(
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            ObjectProvider<SeedMeasurementRun> seedMeasurementRun,
            SeedCorpusComparison seedCorpusComparison,
            @Value("${vespera.working-dir}") Path workingDirectory) {
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.seedMeasurementRun = seedMeasurementRun;
        this.seedCorpusComparison = seedCorpusComparison;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (seedGate.seedWalk().isEmpty()) {
            LOG.info(
                    "stage 5's seed/corpus comparison is gated: no seed folder is named, or stage 4's gate is"
                            + " shut, or the seed walk has not finished. No comparison was measured or written.");
            return RepeatStatus.FINISHED;
        }
        if (!usableSeedGate.anySeedUsable()) {
            LOG.info(
                    "stage 5's seed/corpus comparison is gated: no seed document produced any text, so seed"
                            + " extraction minted no run to measure under. Fix the seed folder and run again.");
            return RepeatStatus.FINISHED;
        }

        SeedMeasurementRun measurementRun = seedMeasurementRun.getObject();
        LOG.info("Stage 5b (seed/corpus comparison) starting under run {}", measurementRun.runId().value());
        SeedCorpusComparison.Comparison comparison = seedCorpusComparison.measure(
                measurementRun.runId(), measurementRun.extractionRunId(), seedGate.seedWalk().get().walkId());
        Path reportFile = writeReport(comparison);
        LOG.info(
                "Stage 5b (seed/corpus comparison) finished under run {}; report written to {}",
                measurementRun.runId().value(),
                reportFile);
        return RepeatStatus.FINISHED;
    }

    /**
     * Writes the seed/corpus comparison report to the working directory, overwriting whatever an
     * earlier run left there (ADR-075's shape) — never accumulated, never left stale.
     */
    private Path writeReport(SeedCorpusComparison.Comparison comparison) {
        Path reportFile = workingDirectory.resolve(SEED_CORPUS_COMPARISON_FILE_NAME);
        try {
            Files.createDirectories(workingDirectory);
            Files.writeString(
                    reportFile, SeedCorpusComparisonReport.render(comparison), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the seed/corpus comparison report at " + reportFile, e);
        }
        return reportFile;
    }
}
