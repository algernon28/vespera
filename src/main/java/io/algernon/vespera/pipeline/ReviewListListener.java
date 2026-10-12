package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.ledger.RunId;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.dao.DataAccessException;

/**
 * Writes the review list (CONTEXT.md) when the step ends (ADR-175 section 7): one
 * row per {@code extraction-failed} verdict under stage 2's run, read from the ledger and never from the
 * step's own counts, so a resumed stage lists what earlier invocations removed too (ADR-181 section 5).
 *
 * <p><b>Registered before {@link ExtractionHealthCheckListener} in {@link ExtractionJobConfiguration},
 * deliberately.</b> Spring Batch runs {@code afterStep} in the reverse of registration order, and this
 * has to run after {@link ExtractionFaultRecorder}, registered last, has turned the fault rows it wrote
 * into verdicts: registered after that one, this would list the stage without them.
 *
 * <p>Holds {@link StageRuns} rather than asking it for the run when built, for the reason {@link
 * ExtractionFaultRecorder} gives (#319): a step that failed its health check first builds this object
 * from {@code afterStep}, and asking for the run there calls the sidecar that just failed its check.
 */
class ReviewListListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ReviewListListener.class);

    /** The page's name, beside the database. */
    static final String FILE_NAME = "extraction-failures.html";

    private final Ledger ledger;
    private final StageRuns stageRuns;
    private final Path workingDirectory;
    private final ReplacingMove moves;

    ReviewListListener(Ledger ledger, StageRuns stageRuns, Path workingDirectory) {
        this(ledger, stageRuns, workingDirectory, ReplacingMove.ATOMIC);
    }

    /** A listener whose page is put in place by {@code moves}, which the shipped form gives as {@link ReplacingMove#ATOMIC}. */
    ReviewListListener(Ledger ledger, StageRuns stageRuns, Path workingDirectory, ReplacingMove moves) {
        this.ledger = ledger;
        this.stageRuns = stageRuns;
        this.workingDirectory = workingDirectory;
        this.moves = moves;
    }

    /**
     * Writes the page for a step that completed, or failed after reading an occurrence. A step that
     * failed having read nothing never reached the survivors reader that asks for its run -- its health
     * check failed, or the sidecar would not say what it is -- so nothing is asked for and no page is
     * written (#319): there is no run to list, and asking for one would call the sidecar again.
     *
     * <p>The page is written beside its place and moved there once whole, as {@code arrangement.html} is
     * (ADR-223 section 3), by the move that is attempted again where it is refused as access denied (ADR-242),
     * so a write that fails partway, in the file or in the read of its rows, leaves the
     * review list before it, or none. A failure of the file, of the read of its rows or of their count is said
     * in one error line, and none is thrown (ADR-175 section 7).
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        boolean completed =
                ExitStatus.COMPLETED.getExitCode().equals(stepExecution.getExitStatus().getExitCode());
        if (!completed && stepExecution.getReadCount() == 0) {
            return stepExecution.getExitStatus();
        }
        RunId run = stageRuns.extraction();
        // The timed span covers the count and the writing of the page, so the sorted read of
        // eachExtractionFailure is issued before the line after (ADR-220 section 6).
        TimedStatement.Started timed = TimedStatement.begin(
                "Stage 2 (extraction)", "reading", "read", "the occurrences it could not read");
        Path page = workingDirectory.resolve(FILE_NAME);
        Path part = workingDirectory.resolve(FILE_NAME + ".part");
        long failures;
        try {
            failures = ledger.verdicts().extractionFailureCount(run);
        } catch (DataAccessException e) {
            log.error(
                    "Stage 2 (extraction): the files that could not be read could not be counted, and the list of"
                            + " them could not be written to {}: {}",
                    page.toAbsolutePath(),
                    e.toString());
            return stepExecution.getExitStatus();
        }
        try {
            Files.createDirectories(workingDirectory);
            try (Writer out = Files.newBufferedWriter(part, StandardCharsets.UTF_8)) {
                ReviewListReport.write(out, failures, each -> ledger.verdicts().eachExtractionFailure(run, each));
            }
            moves.intoPlace(part, page);
            timed.end();
        } catch (IOException | DataAccessException e) {
            // Said, not thrown, whether the file or the read of its rows failed: Spring Batch only logs what an
            // afterStep throws, with a stack trace, and skips the listeners after it. The verdicts are in the
            // ledger either way.
            log.error(
                    "Stage 2 (extraction): {} file(s) could not be read, and the list of them could not be"
                            + " written to {}: {}",
                    failures,
                    page.toAbsolutePath(),
                    e.toString());
            return stepExecution.getExitStatus();
        }
        log.info(
                "Stage 2 (extraction): {} file(s) could not be read; they are listed in {}",
                failures,
                page.toAbsolutePath());
        return stepExecution.getExitStatus();
    }
}
