package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.embedding.EmbeddingStatement;
import io.algernon.vespera.embedding.MeasuredForms;
import io.algernon.vespera.embedding.SeedCorpusComparison;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.OptionalLong;
import java.util.function.LongConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import io.algernon.vespera.ledger.RunId;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
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
 * <p>Gated twice before it reaches stage 5's measurement run, the same way {@link
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
    private final StageRuns stageRuns;
    private final SeedCorpusComparison seedCorpusComparison;
    private final ExtractionMetrics extractionMetrics;
    private final Ledger ledger;
    private final Path workingDirectory;

    SeedCorpusComparisonTasklet(
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            StageRuns stageRuns,
            SeedCorpusComparison seedCorpusComparison,
            ExtractionMetrics extractionMetrics,
            Ledger ledger,
            @Value("${vespera.working-dir}") Path workingDirectory) {
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.stageRuns = stageRuns;
        this.seedCorpusComparison = seedCorpusComparison;
        this.extractionMetrics = extractionMetrics;
        this.ledger = ledger;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        StageFiveGates.Preamble preamble = StageFiveGates.seedWalkAndUsable(
                "stage 5's seed/corpus comparison", seedGate, usableSeedGate);
        if (!preamble.isOpen()) {
            LOG.info(preamble.shutSentence().orElseThrow());
            return RepeatStatus.FINISHED;
        }
        SeedGate.SeedWalk seedWalk = preamble.seedWalk().orElseThrow();

        RunId measurementRun = stageRuns.seedMeasurement();
        RunId extractionRunId = stageRuns.upstream(StageModules.EXTRACTION);

        return TaskletSteps.once(
                ledger,
                measurementRun,
                StepNames.SEED_CORPUS_COMPARISON,
                // seed-extraction, which shares this run, is not asked -- each step answers only for
                // itself.
                () -> LOG.info(
                        "Stage 5b (seed/corpus comparison) was already recorded under run {}",
                        measurementRun.value()),
                () -> seedCorpusComparison.discardForRun(measurementRun),
                () -> {
                    LOG.info("Stage 5b (seed/corpus comparison) starting under run {}", measurementRun.value());
                    // The statements that wait, said as SeedCorpusComparison reports each (ADR-193 section 7,
                    // ADR-204 section 3, ADR-211 section 9): one drain, timed; reads of a run's rows counted by
                    // SQLite's steps, the first of which says nothing where no seed is recorded unusable; and
                    // the corpus survivors' reads, made a page at a time and counted by the rows read.
                    String stage = "Stage 5b (seed/corpus comparison)";
                    SeedCorpusComparison.Comparison comparison = seedCorpusComparison.measure(
                            measurementRun,
                            extractionRunId,
                            seedWalk.walkId(),
                            // extraction reads its own table and hands the rows over (ADR-209 section 3.2).
                            measuredForms(),
                            ReportedStatements.saying()
                                    .timed(EmbeddingStatement.SEED_OCCURRENCES, stage, "the seed walk's occurrences")
                                    .counted(
                                            EmbeddingStatement.UNUSABLE_SEEDS,
                                            stage,
                                            "the unusable seeds",
                                            "Stage 5b (seed/corpus comparison, reading unusable seeds)")
                                    .paged(
                                            EmbeddingStatement.CORPUS_METRICS,
                                            stage,
                                            "the corpus survivors' extraction metrics",
                                            "Stage 5b (seed/corpus comparison, reading corpus metrics)")
                                    .paged(
                                            EmbeddingStatement.CORPUS_METRICS_AGAIN,
                                            stage,
                                            "the corpus survivors' extraction metrics again",
                                            "Stage 5b (seed/corpus comparison, reading corpus metrics again)")
                                    .counted(
                                            EmbeddingStatement.SEED_METRICS,
                                            stage,
                                            "the seeds' extraction metrics",
                                            "Stage 5b (seed/corpus comparison, reading seed metrics)")
                                    .build());
                    Path reportFile = writeReport(comparison);
                    LOG.info(
                            "Stage 5b (seed/corpus comparison) finished under run {}; report written to {}",
                            measurementRun.value(),
                            reportFile);
                    return true;
                });
    }

    /** The two reads of {@code extraction_metric} the comparison needs, {@code extraction}'s own. */
    private MeasuredForms measuredForms() {
        return new MeasuredForms() {
            @Override
            public OptionalLong rowsUpTo(RunId runId) {
                return extractionMetrics.metricRowsUpTo(runId);
            }

            @Override
            public void each(RunId runId, LongConsumer stepsTaken, Row row) {
                extractionMetrics.eachMeasuredForm(runId, stepsTaken, row::read);
            }

            @Override
            public void eachOf(RunId runId, Collection<OccurrenceId> occurrences, Row row) {
                extractionMetrics.eachMeasuredFormOf(runId, occurrences, row::read);
            }
        };
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
