package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.UnusableSeeds;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.ledger.Ledger;
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
 * <p>Stage 5's measurement run is reached through {@link StageRuns} so that the run row is never
 * minted while the gate is shut: "a run that did nothing should not exist in the {@code run}
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
 * confidence is never a floor — for every outcome that was converted, usable or not. This is the same
 * seam the {@code unusable_seed} rows are written from and for the same reason: the run cannot exist
 * until the whole folder has been converted, so a per-document write is impossible without reopening
 * ADR-083's gate ordering. What is held until then is each seed's measured row, not the document it
 * was measured from — the processor measures while the document is open and passes the columns on.
 *
 * <p><b>A seed whose file would not open (ADR-155) carries no {@code extraction_metric} row</b> --
 * nothing was converted, so there is nothing measured -- but it still carries an {@code unusable_seed}
 * row, under {@link SeedExtractionOutcome#FILE_COULD_NOT_BE_OPENED}. While the step is not yet
 * recorded as finished and at least one such seed was met, this class does not call {@link
 * Ledger#finishStep}, so stage 5 goes no further in this invocation (ADR-155 section 2) and the next
 * invocation discards and rewrites these same rows rather than walking past a seed set with a hole in
 * it.
 */
@Component
@StepScope
class SeedExtractionItemWriter implements ItemWriter<SeedExtractionOutcome>, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(SeedExtractionItemWriter.class);

    private final SeedGate seedGate;
    private final StageRuns stageRuns;
    private final UnusableSeeds unusableSeeds;
    private final ExtractionMetrics extractionMetrics;
    private final UsableSeedGate usableSeedGate;
    private final Ledger ledger;

    /**
     * Held rather than written per chunk, because the gate below is a fact about the whole folder. One
     * measured row per seed, against a seed folder of a few dozen to a few thousand documents.
     */
    private final List<SeedExtractionOutcome> outcomes = new ArrayList<>();

    SeedExtractionItemWriter(
            SeedGate seedGate,
            StageRuns stageRuns,
            UnusableSeeds unusableSeeds,
            ExtractionMetrics extractionMetrics,
            UsableSeedGate usableSeedGate,
            Ledger ledger) {
        this.seedGate = seedGate;
        this.stageRuns = stageRuns;
        this.unusableSeeds = unusableSeeds;
        this.extractionMetrics = extractionMetrics;
        this.usableSeedGate = usableSeedGate;
        this.ledger = ledger;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("Stage 5a (seed extraction) starting");
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
        if (seedGate.seedWalk().isEmpty()) {
            // A shut gate, which is not the state the warning below was written for (#135). The reader
            // yields nothing silently when the gate is shut, so that silence used to be filled by a
            // sentence telling the operator to fix a seed folder they had never named -- every clause
            // of it false, and at WARN, the loudest line in the invocation. This says what stage 5's
            // other steps say, because the three causes SeedGate answers as one are not told apart
            // anywhere yet.
            log.info("stage 5's seed-extraction step is gated: no seed folder is named, or stage 4's"
                    + " gate is shut, or the seed walk has not finished. No seed document was extracted.");
            return stepExecution.getExitStatus();
        }
        if (usableSeeds == 0) {
            // ADR-083's gate, and a gate for the right reason rather than a quality judgement:
            // ADR-020's scoring function is a maximum over the seed set, and over an empty set it is
            // undefined -- a value the pipeline requires and does not have. A seed whose file would not
            // open is not usable either, because no one knows whether it would be (ADR-155) -- and it
            // is counted in unusableSeedCount the same as one that converted with no text in it.
            log.warn(
                    "No seed document produced any text, so stage 5 minted no run: {} seed documents were"
                            + " extracted and none of them was usable. Fix the seed folder and run again.",
                    unusableSeedCount);
            return stepExecution.getExitStatus();
        }
        RunId runId = stageRuns.seedMeasurement();

        // This step's own work under this run is already recorded, so there is nothing left to write
        // (ADR-115, ADR-116) -- seed-corpus-comparison, which shares this run, is not asked: each step
        // answers only for itself. The read-and-convert above still ran (cheap: DoclingExtractor's
        // cache is keyed by content hash, not by run), which is what keeps usableSeedGate answered on
        // every invocation regardless of whether this step's own rows are rewritten. A seed file that
        // stops opening after this step finished costs only the processor's own warning (ADR-155
        // section 2): the rows already written stand, and this branch sets no new fact.
        if (ledger.stepFinished(runId, StepNames.SEED_EXTRACTION)) {
            log.info("Stage 5a (seed extraction) was already recorded under run {}", runId.value());
            return stepExecution.getExitStatus();
        }

        // Not finished: an invocation that stopped partway may have left rows behind under this same run id. Discarding
        // this step's own rows before working is ADR-115's other half (ADR-116) -- extraction_metric is
        // keyed (occurrence_id, run_id), so a second write would otherwise collide on the first seed.
        extractionMetrics.discardForRun(runId);
        unusableSeeds.discardForRun(runId);

        long couldNotOpenCount = 0;
        for (SeedExtractionOutcome outcome : outcomes) {
            if (outcome.hasMeasurement()) {
                extractionMetrics.write(outcome.occurrenceId(), runId, outcome.measurement());
            } else {
                couldNotOpenCount++;
            }
            if (!outcome.usable()) {
                unusableSeeds.record(outcome.occurrenceId(), runId, outcome.unusableReason());
            }
        }

        if (couldNotOpenCount > 0) {
            // No completion is recorded while any seed file would not open (ADR-155 section 2): the
            // rows just written stand under this run, but the next invocation discards and rewrites
            // them rather than this invocation, or any later one, walking past a seed set with a hole
            // in it. Stage 5's later steps are shut by usableSeedGate's second fact until an invocation
            // finds every seed file open.
            usableSeedGate.recordSeedFileCouldNotOpen();
            log.warn(
                    "{} seed file(s) could not be opened, so stage 5a (seed extraction) is not recorded"
                            + " as finished under run {} and stage 5 goes no further in this invocation."
                            + " Release them and run the same command again.",
                    couldNotOpenCount,
                    runId.value());
            return stepExecution.getExitStatus();
        }

        ledger.finishStep(runId, StepNames.SEED_EXTRACTION);
        log.info(
                "Stage 5 extracted the seed set under run {}: {} usable, {} recorded as unusable",
                runId.value(),
                usableSeeds,
                unusableSeedCount);
        return stepExecution.getExitStatus();
    }
}
