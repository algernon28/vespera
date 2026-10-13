package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceAtPath;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.ledger.RunId;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.dao.DataAccessException;

/**
 * Writes the survivor list (CONTEXT.md) when the step ends (ADR-250): the survivors of stage 2's run that carry
 * a metrics row under it, by path, read a page at a time from the ledger and from extraction's metrics and
 * joined here, because no statement may name both tables (ADR-209).
 *
 * <p><b>Registered immediately before {@link ReviewListListener} in {@link ExtractionJobConfiguration},
 * deliberately.</b> Spring Batch runs {@code afterStep} in the reverse of registration order, so this runs
 * after the review list and after {@link ExtractionFaultRecorder}'s transaction, so the page is read from the
 * verdicts as they stand at the end of the step; a faulted file occurrence carries no metrics row and is on the
 * page in neither order. A listener of its own so that neither page's failure costs the other.
 *
 * <p>Holds {@link StageRuns} rather than asking it for the run when built, for the reason {@link
 * ReviewListListener} gives (#319).
 */
class SurvivorListListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(SurvivorListListener.class);

    /** The page's name, beside the database. */
    static final String FILE_NAME = "extraction-survivors.html";

    private final Ledger ledger;
    private final ExtractionMetrics extractionMetrics;
    private final StageRuns stageRuns;
    private final Path workingDirectory;
    private final ReplacingMove moves;

    SurvivorListListener(
            Ledger ledger, ExtractionMetrics extractionMetrics, StageRuns stageRuns, Path workingDirectory) {
        this(ledger, extractionMetrics, stageRuns, workingDirectory, ReplacingMove.ATOMIC);
    }

    /** A listener whose page is put in place by {@code moves}, which the shipped form gives as {@link ReplacingMove#ATOMIC}. */
    SurvivorListListener(
            Ledger ledger,
            ExtractionMetrics extractionMetrics,
            StageRuns stageRuns,
            Path workingDirectory,
            ReplacingMove moves) {
        this.ledger = ledger;
        this.extractionMetrics = extractionMetrics;
        this.stageRuns = stageRuns;
        this.workingDirectory = workingDirectory;
        this.moves = moves;
    }

    /**
     * Writes the page for a step that completed, or failed after reading an occurrence; nothing, and no
     * request for the run, for one that failed having read nothing (#319). Written beside its place and moved
     * there once whole (ADR-223 section 3, ADR-242), so a failure leaves the page before it, or none. A failure
     * is said in one error line and none is thrown (ADR-175 section 7).
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        boolean completed =
                ExitStatus.COMPLETED.getExitCode().equals(stepExecution.getExitStatus().getExitCode());
        if (!completed && stepExecution.getReadCount() == 0) {
            return stepExecution.getExitStatus();
        }
        RunId run = stageRuns.extraction();
        // The timed span covers the count and the writing of the page (ADR-220 section 6).
        TimedStatement.Started timed =
                TimedStatement.begin("Stage 2 (extraction)", "reading", "read", "the survivors it converted");
        Path page = workingDirectory.resolve(FILE_NAME);
        Path part = workingDirectory.resolve(FILE_NAME + ".part");
        AtomicLong cameThrough = new AtomicLong();
        try {
            ledger.verdicts().eachPageOfSurvivors(run, survivors ->
                    cameThrough.addAndGet(extractionMetrics.recordedAmong(run, survivors).size()));
        } catch (DataAccessException e) {
            log.error(
                    "Stage 2 (extraction): the files that came through could not be counted, and their list could"
                            + " not be written to {}: {}",
                    page.toAbsolutePath(),
                    e.toString());
            return stepExecution.getExitStatus();
        }
        try {
            Files.createDirectories(workingDirectory);
            try (Writer out = Files.newBufferedWriter(part, StandardCharsets.UTF_8)) {
                SurvivorListReport.write(out, cameThrough.get(), each -> eachSurvivor(run, each));
            }
            moves.intoPlace(part, page);
            timed.end();
        } catch (IOException | DataAccessException e) {
            log.error(
                    "Stage 2 (extraction): {} file(s) came through, and their list could not be written to {}: {}",
                    cameThrough.get(),
                    page.toAbsolutePath(),
                    e.toString());
            return stepExecution.getExitStatus();
        }
        log.info(
                "Stage 2 (extraction): {} file(s) came through; their list is {}",
                cameThrough.get(),
                page.toAbsolutePath());
        return stepExecution.getExitStatus();
    }

    /** Hands each survivor that carries a metrics row to {@code each}, a page of paths and of measurements at a time, in path order. */
    private void eachSurvivor(RunId run, java.util.function.Consumer<SurvivorListReport.Row> each) {
        ledger.verdicts().eachPageOfSurvivorsByPath(run, (List<OccurrenceAtPath> paths) -> {
            Map<OccurrenceId, SurvivorListReport.Row> measured = new HashMap<>();
            Map<OccurrenceId, String> pathOf = new HashMap<>();
            paths.forEach(at -> pathOf.put(at.occurrenceId(), at.path().value()));
            extractionMetrics.eachMeasuredFormOf(
                    run,
                    pathOf.keySet(),
                    (occurrence, language, meanScoreIsNull, words, pages, vowelless, singleCharacter) ->
                            measured.put(
                                    occurrence, new SurvivorListReport.Row(pathOf.get(occurrence), pages, words)));
            for (OccurrenceAtPath at : paths) {
                SurvivorListReport.Row row = measured.get(at.occurrenceId());
                if (row != null) {
                    each.accept(row);
                }
            }
        });
    }
}
