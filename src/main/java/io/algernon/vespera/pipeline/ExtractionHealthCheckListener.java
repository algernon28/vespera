package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.DoclingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

/**
 * Checks {@code docling-serve}'s health once, immediately before stage 2's step processes its first
 * occurrence (ADR-071) — not at job start, since census and stage 1 never touch the sidecar, and not
 * before every call, since a sidecar that dies later fails the call itself. What that failure earns
 * depends on how the call failed (ADR-175): a failure the sidecar answers with, or three timeouts in a
 * row, is skipped and counted by the breaker; a dropped connection is waited out by {@link
 * SidecarRecovery} and the call placed once more.
 *
 * <p>Also carries stage 2's own step start/end logging (ADR-093): the natural place, since it already
 * runs at both boundaries Spring Batch offers a step-scoped listener. The end line says how the step
 * ended, because {@link #afterStep} runs after a failed step too: "finished" only when it completed,
 * and otherwise that it failed, with the same counts and what failed it (#311). It ends by telling the
 * operator what to do about that failure: reconnect the archive where the corpus root can no longer be
 * listed (ADR-210), close what holds the database file where one does (ADR-177), and otherwise fix what
 * the line names, bringing the sidecar back if it stopped answering.
 */
@Component
class ExtractionHealthCheckListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ExtractionHealthCheckListener.class);

    private final DoclingClient doclingClient;

    ExtractionHealthCheckListener(DoclingClient doclingClient) {
        this.doclingClient = doclingClient;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("Stage 2 (extraction) starting: checking docling-serve health");
        try {
            doclingClient.checkHealth();
        } catch (RuntimeException healthCheckFailed) {
            log.error("Stage 2 (extraction) sidecar health check failed: {}", healthCheckFailed.toString());
            throw healthCheckFailed;
        }
        log.info("Stage 2 (extraction) sidecar is healthy");
    }

    /**
     * What the closing line tells the operator to do: reconnect the archive where the corpus root can no
     * longer be listed (ADR-210), close whatever else has the database file open where it is locked
     * (ADR-177), and otherwise fix what the failure names, the sidecar included.
     */
    private static String closingAdvice(StepExecution stepExecution) {
        if (StepFailure.archiveGone(stepExecution)) {
            return "Reconnect the archive and run the same command again.";
        }
        if (StepFailure.lockedDatabaseFile(stepExecution)) {
            return "Close whatever else has the database file open and run the same command again.";
        }
        return "Fix what that names -- if docling-serve stopped answering, bring it back -- and run"
                + " the same command again.";
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (!ExitStatus.COMPLETED.getExitCode().equals(stepExecution.getExitStatus().getExitCode())) {
            // Spring Batch calls this from a finally, so a step that failed arrives here too (#311), and
            // "finished" beside its error would tell the operator it completed. The same exit-code test
            // RunCompletion makes, so this line says failed only when the step did not complete, and a
            // step that did not complete records no completion; the next invocation redoes this run's
            // work under the same id (ADR-115, ADR-116).
            // A locked database file is not the sidecar's doing, so the line stops pointing at it (ADR-177).
            log.error(
                    "Stage 2 (extraction) failed and is not recorded as finished (read={}, written={},"
                            + " skipped={}, filtered={}): {}. {}",
                    stepExecution.getReadCount(),
                    stepExecution.getWriteCount(),
                    stepExecution.getSkipCount(),
                    stepExecution.getFilterCount(),
                    StepFailure.named(stepExecution),
                    closingAdvice(stepExecution));
            return stepExecution.getExitStatus();
        }
        log.info(
                "Stage 2 (extraction) finished: read={}, written={}, skipped={}, filtered={}",
                stepExecution.getReadCount(),
                stepExecution.getWriteCount(),
                stepExecution.getSkipCount(),
                stepExecution.getFilterCount());
        return stepExecution.getExitStatus();
    }
}
