package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.UnusableSeeds;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.ledger.RunId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.core.ExitStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Holds what the seed pass learned, and — once the whole pass is done — decides whether stage 5's
 * measurement run exists at all (ADR-083's gate, under ADR-080's rule).
 *
 * <p>Writer and step-execution listener in one class, which is the shape the gate forces rather than
 * a convenience. ADR-083's second gate is <b>no usable seed at all</b>, and that cannot be answered
 * until every seed has been extracted: the answer is a property of the whole folder, not of a chunk.
 * So the per-chunk half of this class only accumulates — the caches it accumulates from are
 * content-addressed and need no run — and {@link #afterStep} is where the run is minted, exactly once,
 * and only if something usable was found.
 *
 * <p>{@link SeedMeasurementRun} is reached through an {@code ObjectProvider} so that the run row is
 * never minted while the gate is shut: "a run that did nothing should not exist in the {@code run}
 * table" (ADR-080), and a stage-5 run row against a seed folder that produced nothing would read as a
 * measurement that found nothing to say — which is a different claim from never having run.
 *
 * <p>Consequently, when no seed is usable, <b>no {@code unusable_seed} row is written either</b>:
 * those rows carry the run id that found them, and there is no run. What the operator gets in that
 * case is this class's own log line and a successful invocation that removed nothing, which is what a
 * gate is — the invocation ends having recorded what it learned, and the seed folder is what needs
 * fixing.
 *
 * <p><b>Once the run exists, it also writes the seed side's {@code extraction_metric} rows</b>
 * (ADR-092): {@code ExtractionMetrics.write} — never {@code writeAndJudge}, since a seed's tier-2
 * confidence is never a floor — for every outcome, usable or not. This is the same seam the {@code
 * unusable_seed} rows are written from and for the same reason: the run cannot exist until the whole
 * folder has been converted, so a per-document write is impossible without reopening ADR-083's gate
 * ordering.
 */
@Component
@StepScope
class SeedExtractionItemWriter implements ItemWriter<SeedExtractionOutcome>, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(SeedExtractionItemWriter.class);

    private final ObjectProvider<SeedMeasurementRun> seedMeasurementRun;
    private final UnusableSeeds unusableSeeds;
    private final ExtractionMetrics extractionMetrics;
    private final UsableSeedGate usableSeedGate;

    /** Held rather than written per chunk, because the gate below is a fact about the whole folder. */
    private final List<SeedExtractionOutcome> outcomes = new ArrayList<>();

    SeedExtractionItemWriter(
            ObjectProvider<SeedMeasurementRun> seedMeasurementRun,
            UnusableSeeds unusableSeeds,
            ExtractionMetrics extractionMetrics,
            UsableSeedGate usableSeedGate) {
        this.seedMeasurementRun = seedMeasurementRun;
        this.unusableSeeds = unusableSeeds;
        this.extractionMetrics = extractionMetrics;
        this.usableSeedGate = usableSeedGate;
    }

    @Override
    public void write(Chunk<? extends SeedExtractionOutcome> chunk) {
        for (SeedExtractionOutcome outcome : chunk) {
            outcomes.add(outcome);
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long usableSeeds = outcomes.stream().filter(SeedExtractionOutcome::usable).count();
        long unusableSeedCount = outcomes.size() - usableSeeds;
        // Told to the rest of stage 5 before this method can return either way: with no usable seed
        // there is no run, so nothing is written that a later step could read the answer off (ADR-092).
        usableSeedGate.recordUsableSeeds(usableSeeds);
        if (usableSeeds == 0) {
            // ADR-083's gate, and a gate for the right reason rather than a quality judgement:
            // ADR-020's scoring function is a maximum over the seed set, and over an empty set it is
            // undefined -- a value the pipeline requires and does not have.
            log.warn(
                    "No seed document produced any text, so stage 5 minted no run: {} seed documents were"
                            + " extracted and none of them was usable. Fix the seed folder and run again.",
                    unusableSeedCount);
            return stepExecution.getExitStatus();
        }
        RunId runId = seedMeasurementRun.getObject().runId();
        for (SeedExtractionOutcome outcome : outcomes) {
            extractionMetrics.write(outcome.occurrenceId(), runId, outcome.response());
            if (!outcome.usable()) {
                unusableSeeds.record(outcome.occurrenceId(), runId, outcome.unusableReason());
            }
        }
        log.info(
                "Stage 5 extracted the seed set under run {}: {} usable, {} recorded as unusable",
                runId.value(),
                usableSeeds,
                unusableSeedCount);
        return stepExecution.getExitStatus();
    }
}
