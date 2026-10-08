package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.RemovedOccurrence;
import io.algernon.vespera.ledger.Ledger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

/**
 * Writes the review list (CONTEXT.md) when the step ends (ADR-175 section 7): one
 * row per {@code extraction-failed} verdict under stage 2's run, read from the ledger and never from the
 * step's own counts, so a resumed stage lists what earlier invocations removed too (ADR-181 section 5).
 *
 * <p><b>Registered before {@link ExtractionHealthCheckListener} in {@link ExtractionJobConfiguration},
 * deliberately.</b> Spring Batch runs {@code afterStep} in the reverse of registration order, and this
 * has to run after {@link ExtractionFaultRecorder}, registered last, has turned the skips it held into
 * verdicts: registered after that one, this would list the stage without them.
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

    ReviewListListener(Ledger ledger, StageRuns stageRuns, Path workingDirectory) {
        this.ledger = ledger;
        this.stageRuns = stageRuns;
        this.workingDirectory = workingDirectory;
    }

    /**
     * Writes the page for a step that completed, or failed after reading an occurrence. A step that
     * failed having read nothing never reached the survivors reader that asks for its run -- its health
     * check failed, or the sidecar would not say what it is -- so nothing is asked for and no page is
     * written (#319): there is no run to list, and asking for one would call the sidecar again.
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        boolean completed =
                ExitStatus.COMPLETED.getExitCode().equals(stepExecution.getExitStatus().getExitCode());
        if (!completed && stepExecution.getReadCount() == 0) {
            return stepExecution.getExitStatus();
        }
        List<RemovedOccurrence> failures = TimedStatement.of(
                "Stage 2 (extraction)", "reading", "read",
                "the occurrences it could not read",
                () -> ledger.verdicts().extractionFailures(stageRuns.extraction()));
        Path page = workingDirectory.resolve(FILE_NAME);
        try {
            Files.createDirectories(workingDirectory);
            Files.writeString(page, ReviewListReport.render(failures), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Said, not thrown: Spring Batch only logs what an afterStep throws, with a stack trace, and
            // skips the listeners after it. The verdicts are in the ledger either way.
            log.error(
                    "Stage 2 (extraction): {} file(s) could not be read, and the list of them could not be"
                            + " written to {}: {}",
                    failures.size(),
                    page.toAbsolutePath(),
                    e.toString());
            return stepExecution.getExitStatus();
        }
        log.info(
                "Stage 2 (extraction): {} file(s) could not be read; they are listed in {}",
                failures.size(),
                page.toAbsolutePath());
        return stepExecution.getExitStatus();
    }
}
